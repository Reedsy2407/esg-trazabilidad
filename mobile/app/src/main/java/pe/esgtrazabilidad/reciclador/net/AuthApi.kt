package pe.esgtrazabilidad.reciclador.net

/** Answers of the contracts in Parte A.2 of the specification. */
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val consentRequired: Boolean = false,
    val consentVersion: Int? = null,
) {
    // Never print a credential, even by accident in a log line or a failed assertion.
    override fun toString(): String = "Tokens(expiresInSeconds=$expiresInSeconds, consentRequired=$consentRequired)"
}

data class Consent(
    val version: Int,
    val text: String,
    val locationText: String,
    val gpsDays: Int,
    val photoMonths: Int,
)

/**
 * An error answer. [status] 0 means no answer at all (no network). [code] is the service's `code`
 * (AUTH-014, AUTH-017, ...), null when the body had none.
 */
class ApiException(val status: Int, val code: String?, message: String? = null) : Exception(message ?: "HTTP $status $code") {

    /** The device was revoked or the account is inactive: the phone must drop its session (CA-21). */
    val isRevocation: Boolean get() = code == CODE_DEVICE_REVOKED || code == CODE_ACCOUNT_INACTIVE

    val isNetwork: Boolean get() = status == 0

    companion object {
        const val CODE_INVALID_ACTIVATION = "AUTH-014"
        const val CODE_TOO_MANY_ATTEMPTS = "AUTH-015"
        const val CODE_REFRESH_INVALID = "AUTH-016"
        const val CODE_DEVICE_REVOKED = "AUTH-017"
        const val CODE_ACCOUNT_INACTIVE = "AUTH-018"
    }
}

interface AuthApi {
    @Throws(ApiException::class)
    suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String): Tokens

    @Throws(ApiException::class)
    suspend fun refresh(refreshToken: String): Tokens

    @Throws(ApiException::class)
    suspend fun consent(accessToken: String): Consent

    @Throws(ApiException::class)
    suspend fun acceptConsent(accessToken: String, version: Int, accepted: Boolean, locationAccepted: Boolean)

    @Throws(ApiException::class)
    suspend fun logout(accessToken: String, refreshToken: String)
}
