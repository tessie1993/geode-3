package dev.geode.account

enum class PremiumProductKind {
    SUBSCRIPTION,
    LIFETIME,
}

enum class PremiumProduct(val productId: String, val kind: PremiumProductKind) {
    MONTHLY("premium_monthly", PremiumProductKind.SUBSCRIPTION),
    YEARLY("premium_yearly", PremiumProductKind.SUBSCRIPTION),
    LIFETIME("premium_lifetime", PremiumProductKind.LIFETIME),
    ;

    companion object {
        fun fromId(productId: String): PremiumProduct? = entries.firstOrNull { it.productId == productId }
    }
}

/** Free playback, safety controls and access to user data are intentionally absent. */
enum class PremiumCapability {
    PREMIUM_VISUALS,
    HIGH_RESOLUTION_EXPORT,
    LONG_EXPORT,
    WATERMARK_FREE_EXPORT,
}

data class PremiumCapabilities(
    val premiumVisuals: Boolean = false,
    val highResolutionExport: Boolean = false,
    val longExport: Boolean = false,
    val watermarkFreeExport: Boolean = false,
) {
    operator fun contains(capability: PremiumCapability): Boolean =
        when (capability) {
            PremiumCapability.PREMIUM_VISUALS -> premiumVisuals
            PremiumCapability.HIGH_RESOLUTION_EXPORT -> highResolutionExport
            PremiumCapability.LONG_EXPORT -> longExport
            PremiumCapability.WATERMARK_FREE_EXPORT -> watermarkFreeExport
        }
}

enum class EntitlementState {
    ACTIVE,
    GRACE,
    CANCELED_ACTIVE,
    LIFETIME,
    FREE,
    PENDING,
    VERIFYING,
    ON_HOLD,
    PAUSED,
    EXPIRED,
    REVOKED,
    UNKNOWN,
}

enum class EntitlementSource {
    VERIFICATION_SERVICE,
    LOCAL_PURCHASE_CALLBACK,
    IMPORTED_DATA,
}

enum class LeaseVerification {
    VERIFIED,
    UNVERIFIED,
    REJECTED,
}

/**
 * Policy input, not a cryptographic verifier. Only a future trusted verifier may
 * supply VERIFIED after checking signature, pinned algorithm/key, issuer and all
 * signed claims. Never deserialize a stored/server "verified" Boolean into it.
 * No billing callback or profile transition constructs an access grant here.
 */
data class VerifiedEntitlement(
    val principal: CommercePrincipal,
    val productId: String,
    val state: EntitlementState,
    val capabilities: PremiumCapabilities,
    val source: EntitlementSource,
    val verification: LeaseVerification,
    val audience: String,
    val revision: Long,
    val verifiedAtEpochMs: Long,
    val issuedAtEpochMs: Long,
    val accessUntilEpochMs: Long?,
    val offlineUntilEpochMs: Long,
)

/** Established from authenticated server time; never reconstructed from device wall time. */
data class VerifiedTimeAnchor(
    val serverEpochMs: Long,
    val elapsedRealtimeMs: Long,
    val bootSessionId: String,
) {
    internal fun now(
        currentElapsedRealtimeMs: Long,
        currentBootSessionId: String,
    ): Long? {
        if (serverEpochMs <= 0L || elapsedRealtimeMs < 0L || bootSessionId.isBlank()) return null
        if (bootSessionId != currentBootSessionId || currentElapsedRealtimeMs < elapsedRealtimeMs) return null
        val delta = currentElapsedRealtimeMs - elapsedRealtimeMs
        if (serverEpochMs > Long.MAX_VALUE - delta) return null
        return serverEpochMs + delta
    }
}

data class PremiumAccessContext(
    val principal: CommercePrincipal?,
    val timeAnchor: VerifiedTimeAnchor?,
    val elapsedRealtimeMs: Long,
    val bootSessionId: String,
    // Persist the highest authenticated aggregate revision for this principal.
    // A newer revocation must not be undone by replaying an older signed lease.
    val minimumRevision: Long,
)

enum class PremiumDenial {
    NO_VERIFIED_GRANT,
    PRINCIPAL_MISMATCH,
    UNKNOWN_PRODUCT,
    STALE_REVISION,
    INELIGIBLE_STATE,
    INVALID_LEASE,
    TIME_REQUIRES_VERIFICATION,
    EXPIRED,
    CAPABILITY_NOT_GRANTED,
}

sealed interface PremiumAccessDecision {
    data class Allowed(val product: PremiumProduct, val validUntilEpochMs: Long) : PremiumAccessDecision

    data class Denied(val reason: PremiumDenial) : PremiumAccessDecision
}

/** Fail-closed policy for starting new premium work; not an identity/billing implementation. */
object PremiumAccessPolicy {
    const val SUBSCRIPTION_OFFLINE_MAX_MS: Long = 72L * 60L * 60L * 1000L
    const val LIFETIME_OFFLINE_MAX_MS: Long = 30L * 24L * 60L * 60L * 1000L

    fun evaluate(
        capability: PremiumCapability,
        entitlement: VerifiedEntitlement?,
        context: PremiumAccessContext,
    ): PremiumAccessDecision {
        val grant = entitlement ?: return denied(PremiumDenial.NO_VERIFIED_GRANT)
        if (grant.source != EntitlementSource.VERIFICATION_SERVICE || grant.verification != LeaseVerification.VERIFIED) {
            return denied(PremiumDenial.NO_VERIFIED_GRANT)
        }
        val principal = context.principal
        if (principal == null || !principal.isComplete() || grant.principal != principal || grant.audience != principal.audience) {
            return denied(PremiumDenial.PRINCIPAL_MISMATCH)
        }
        val product = PremiumProduct.fromId(grant.productId) ?: return denied(PremiumDenial.UNKNOWN_PRODUCT)
        if (grant.revision <= 0L || context.minimumRevision < 0L || grant.revision < context.minimumRevision) {
            return denied(PremiumDenial.STALE_REVISION)
        }
        if (!eligible(product, grant.state)) return denied(PremiumDenial.INELIGIBLE_STATE)
        val expiry = validatedExpiry(product, grant) ?: return denied(PremiumDenial.INVALID_LEASE)
        val now =
            context.timeAnchor?.now(context.elapsedRealtimeMs, context.bootSessionId)
                ?: return denied(PremiumDenial.TIME_REQUIRES_VERIFICATION)
        if (grant.issuedAtEpochMs > now) return denied(PremiumDenial.INVALID_LEASE)
        if (now >= expiry) return denied(PremiumDenial.EXPIRED)
        if (capability !in grant.capabilities) return denied(PremiumDenial.CAPABILITY_NOT_GRANTED)
        return PremiumAccessDecision.Allowed(product, expiry)
    }

    private fun eligible(
        product: PremiumProduct,
        state: EntitlementState,
    ): Boolean =
        when (product.kind) {
            PremiumProductKind.LIFETIME -> state == EntitlementState.LIFETIME
            PremiumProductKind.SUBSCRIPTION ->
                state == EntitlementState.ACTIVE ||
                    state == EntitlementState.GRACE ||
                    state == EntitlementState.CANCELED_ACTIVE
        }

    private fun validatedExpiry(
        product: PremiumProduct,
        grant: VerifiedEntitlement,
    ): Long? {
        val maxOfflineMs =
            when (product.kind) {
                PremiumProductKind.SUBSCRIPTION -> SUBSCRIPTION_OFFLINE_MAX_MS
                PremiumProductKind.LIFETIME -> LIFETIME_OFFLINE_MAX_MS
            }
        if (grant.verifiedAtEpochMs <= 0L || grant.issuedAtEpochMs < grant.verifiedAtEpochMs) return null
        if (grant.verifiedAtEpochMs > Long.MAX_VALUE - maxOfflineMs) return null
        if (grant.offlineUntilEpochMs <= grant.issuedAtEpochMs) return null
        if (grant.offlineUntilEpochMs > grant.verifiedAtEpochMs + maxOfflineMs) return null
        return when (product.kind) {
            PremiumProductKind.LIFETIME -> grant.offlineUntilEpochMs.takeIf { grant.accessUntilEpochMs == null }
            PremiumProductKind.SUBSCRIPTION -> {
                val accessUntil = grant.accessUntilEpochMs ?: return null
                if (accessUntil <= grant.issuedAtEpochMs) return null
                minOf(accessUntil, grant.offlineUntilEpochMs)
            }
        }
    }

    private fun denied(reason: PremiumDenial): PremiumAccessDecision = PremiumAccessDecision.Denied(reason)
}
