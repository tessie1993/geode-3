package dev.geode.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumAccessPolicyTest {
    private val serverNow = 1_800_000_000_000L
    private val principal =
        CommercePrincipal("install-a", "key-a", "commerce-a", "dev.geode", CommerceEnvironment.PRODUCTION)
    private val context =
        PremiumAccessContext(
            principal = principal,
            timeAnchor = VerifiedTimeAnchor(serverNow, 1000L, "boot-a"),
            elapsedRealtimeMs = 1000L,
            bootSessionId = "boot-a",
            minimumRevision = 5L,
        )
    private val grant =
        VerifiedEntitlement(
            principal = principal,
            productId = PremiumProduct.MONTHLY.productId,
            state = EntitlementState.ACTIVE,
            capabilities = PremiumCapabilities(premiumVisuals = true),
            source = EntitlementSource.VERIFICATION_SERVICE,
            verification = LeaseVerification.VERIFIED,
            audience = principal.audience,
            revision = 5L,
            verifiedAtEpochMs = serverNow,
            issuedAtEpochMs = serverNow,
            accessUntilEpochMs = serverNow + PremiumAccessPolicy.SUBSCRIPTION_OFFLINE_MAX_MS,
            offlineUntilEpochMs = serverNow + PremiumAccessPolicy.SUBSCRIPTION_OFFLINE_MAX_MS,
        )

    @Test
    fun `active grace and canceled subscriptions retain access only through verified expiry`() {
        listOf(EntitlementState.ACTIVE, EntitlementState.GRACE, EntitlementState.CANCELED_ACTIVE).forEach { state ->
            assertTrue(evaluate(grant.copy(state = state)) is PremiumAccessDecision.Allowed)
        }
        val expiring = grant.copy(accessUntilEpochMs = serverNow + 10L)
        assertDenied(PremiumDenial.EXPIRED, expiring, context.copy(elapsedRealtimeMs = 1010L))
    }

    @Test
    fun `local callbacks imported flags and unverified claims cannot grant access`() {
        assertDenied(PremiumDenial.NO_VERIFIED_GRANT, null)
        listOf(EntitlementSource.LOCAL_PURCHASE_CALLBACK, EntitlementSource.IMPORTED_DATA).forEach { source ->
            assertDenied(PremiumDenial.NO_VERIFIED_GRANT, grant.copy(source = source))
        }
        listOf(LeaseVerification.UNVERIFIED, LeaseVerification.REJECTED).forEach { verification ->
            assertDenied(PremiumDenial.NO_VERIFIED_GRANT, grant.copy(verification = verification))
        }
    }

    @Test
    fun `purchase grants cannot cross installation key owner package or environment`() {
        listOf(
            principal.copy(installationId = "install-b"),
            principal.copy(installationKeyThumbprint = "key-b"),
            principal.copy(commerceSubject = "commerce-b"),
            principal.copy(packageName = "dev.geode.debug"),
            principal.copy(environment = CommerceEnvironment.STAGING),
        ).forEach { other ->
            assertDenied(PremiumDenial.PRINCIPAL_MISMATCH, grant, context.copy(principal = other))
        }
        assertDenied(PremiumDenial.PRINCIPAL_MISMATCH, grant.copy(audience = "another-app:production"))
        assertDenied(PremiumDenial.PRINCIPAL_MISMATCH, grant, context.copy(principal = null))
        val blank = principal.copy(installationKeyThumbprint = "")
        assertDenied(PremiumDenial.PRINCIPAL_MISMATCH, grant.copy(principal = blank), context.copy(principal = blank))
    }

    @Test
    fun `unknown products and subscription lifetime type confusion are denied`() {
        assertDenied(PremiumDenial.UNKNOWN_PRODUCT, grant.copy(productId = "invented_premium"))
        assertDenied(PremiumDenial.INELIGIBLE_STATE, grant.copy(state = EntitlementState.LIFETIME))
        assertDenied(PremiumDenial.INELIGIBLE_STATE, grant.copy(productId = PremiumProduct.LIFETIME.productId))
    }

    @Test
    fun `pending held paused expired revoked and unknown states do not unlock`() {
        listOf(
            EntitlementState.FREE,
            EntitlementState.PENDING,
            EntitlementState.VERIFYING,
            EntitlementState.ON_HOLD,
            EntitlementState.PAUSED,
            EntitlementState.EXPIRED,
            EntitlementState.REVOKED,
            EntitlementState.UNKNOWN,
        ).forEach { state -> assertDenied(PremiumDenial.INELIGIBLE_STATE, grant.copy(state = state)) }
    }

    @Test
    fun `old signed leases cannot roll back a newer authenticated revision`() {
        assertDenied(PremiumDenial.STALE_REVISION, grant.copy(revision = 4L))
        assertDenied(PremiumDenial.STALE_REVISION, grant.copy(revision = 0L))
        assertDenied(PremiumDenial.STALE_REVISION, grant, context.copy(minimumRevision = -1L))
    }

    @Test
    fun `a subscription requires a finite positive authoritative access expiry`() {
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(accessUntilEpochMs = null))
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(accessUntilEpochMs = -1L))
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(accessUntilEpochMs = serverNow))
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(verifiedAtEpochMs = -1L))
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(offlineUntilEpochMs = -1L))
    }

    @Test
    fun `offline lease cannot exceed the cap or authorize at its exclusive expiry`() {
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(offlineUntilEpochMs = grant.offlineUntilEpochMs + 1L))
        assertDenied(
            PremiumDenial.EXPIRED,
            grant,
            context.copy(elapsedRealtimeMs = 1000L + PremiumAccessPolicy.SUBSCRIPTION_OFFLINE_MAX_MS),
        )
    }

    @Test
    fun `lifetime has no product expiry but its cached verification is bounded`() {
        val lifetime =
            grant.copy(
                productId = PremiumProduct.LIFETIME.productId,
                state = EntitlementState.LIFETIME,
                accessUntilEpochMs = null,
                offlineUntilEpochMs = serverNow + PremiumAccessPolicy.LIFETIME_OFFLINE_MAX_MS,
            )
        assertTrue(evaluate(lifetime) is PremiumAccessDecision.Allowed)
        assertDenied(PremiumDenial.INVALID_LEASE, lifetime.copy(accessUntilEpochMs = serverNow + 10L))
        assertDenied(PremiumDenial.INVALID_LEASE, lifetime.copy(offlineUntilEpochMs = lifetime.offlineUntilEpochMs + 1L))
        assertDenied(
            PremiumDenial.EXPIRED,
            lifetime,
            context.copy(elapsedRealtimeMs = 1000L + PremiumAccessPolicy.LIFETIME_OFFLINE_MAX_MS),
        )
    }

    @Test
    fun `reboot missing anchor and elapsed clock rollback require fresh verification`() {
        assertDenied(PremiumDenial.TIME_REQUIRES_VERIFICATION, grant, context.copy(bootSessionId = "boot-b"))
        assertDenied(PremiumDenial.TIME_REQUIRES_VERIFICATION, grant, context.copy(timeAnchor = null))
        assertDenied(PremiumDenial.TIME_REQUIRES_VERIFICATION, grant, context.copy(elapsedRealtimeMs = 999L))
        assertDenied(
            PremiumDenial.TIME_REQUIRES_VERIFICATION,
            grant,
            context.copy(timeAnchor = VerifiedTimeAnchor(serverNow, -1L, "boot-a")),
        )
    }

    @Test
    fun `future lease and overflowing timestamps cannot extend access`() {
        assertDenied(PremiumDenial.INVALID_LEASE, grant.copy(issuedAtEpochMs = serverNow + 1L))
        assertDenied(
            PremiumDenial.INVALID_LEASE,
            grant.copy(verifiedAtEpochMs = Long.MAX_VALUE - 1L, issuedAtEpochMs = Long.MAX_VALUE - 1L),
        )
        assertDenied(
            PremiumDenial.TIME_REQUIRES_VERIFICATION,
            grant,
            context.copy(timeAnchor = VerifiedTimeAnchor(Long.MAX_VALUE, 0L, "boot-a")),
        )
    }

    @Test
    fun `a verified visual grant cannot unlock an ungranted export capability`() {
        assertEquals(
            PremiumAccessDecision.Denied(PremiumDenial.CAPABILITY_NOT_GRANTED),
            PremiumAccessPolicy.evaluate(PremiumCapability.HIGH_RESOLUTION_EXPORT, grant, context),
        )
    }

    @Test
    fun `Google sign out clears profile without reassigning or deleting commerce ownership`() {
        val state = AccountState(IdentityState.SignedIn(AccountProfile("profile-a", "Listener")), principal)
        val signedOut = state.signedOut()
        assertEquals(IdentityState.SignedOut, signedOut.identity)
        assertEquals(principal, signedOut.commercePrincipal)
        assertTrue(evaluate(grant, context.copy(principal = signedOut.commercePrincipal)) is PremiumAccessDecision.Allowed)
        assertTrue(state.identity is IdentityState.SignedIn)
    }

    private fun evaluate(
        entitlement: VerifiedEntitlement?,
        accessContext: PremiumAccessContext = context,
    ): PremiumAccessDecision = PremiumAccessPolicy.evaluate(PremiumCapability.PREMIUM_VISUALS, entitlement, accessContext)

    private fun assertDenied(
        reason: PremiumDenial,
        entitlement: VerifiedEntitlement?,
        accessContext: PremiumAccessContext = context,
    ) {
        assertEquals(PremiumAccessDecision.Denied(reason), evaluate(entitlement, accessContext))
    }
}
