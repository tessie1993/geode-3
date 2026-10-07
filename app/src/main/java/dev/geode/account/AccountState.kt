package dev.geode.account

enum class CommerceEnvironment(val audienceSuffix: String) {
    PRODUCTION("production"),
    STAGING("staging"),
}

/** The Google profile and Play commerce subject are deliberately different identities. */
data class CommercePrincipal(
    val installationId: String,
    val installationKeyThumbprint: String,
    val commerceSubject: String,
    val packageName: String,
    val environment: CommerceEnvironment,
) {
    val audience: String get() = "$packageName:${environment.audienceSuffix}"

    internal fun isComplete(): Boolean =
        installationId.isNotBlank() &&
            installationKeyThumbprint.isNotBlank() &&
            commerceSubject.isNotBlank() &&
            packageName.isNotBlank()
}

data class AccountProfile(
    val subject: String,
    val displayName: String?,
)

sealed interface IdentityState {
    data object LocalOnly : IdentityState

    data object Selecting : IdentityState

    data object Verifying : IdentityState

    data class SignedIn(val profile: AccountProfile) : IdentityState

    data object ReauthenticationRequired : IdentityState

    data class DeletionPending(val operationId: String) : IdentityState

    data object SignedOut : IdentityState
}

/** Contains no session credentials and does not itself authenticate or authorize anything. */
data class AccountState(
    val identity: IdentityState = IdentityState.LocalOnly,
    val commercePrincipal: CommercePrincipal? = null,
) {
    /** Repository integration must also revoke/clear profile credentials and cancel profile jobs. */
    fun signedOut(): AccountState = copy(identity = IdentityState.SignedOut)
}
