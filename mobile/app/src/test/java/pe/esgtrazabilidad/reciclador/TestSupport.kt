package pe.esgtrazabilidad.reciclador

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import pe.esgtrazabilidad.reciclador.crypto.Vault
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.net.Tokens

/** A clock the test moves. */
class FakeClock(var now: Long = 1_800_000_000_000L) : TimeSource {
    override fun nowMillis(): Long = now
    fun advanceDays(days: Long) { now += days * 24 * 60 * 60 * 1000 }
    fun advanceSeconds(seconds: Long) { now += seconds * 1000 }
}

/** The same AES-GCM contract as the Keystore vault, with the key in memory: for JVM tests only. */
class SoftwareVault : Vault {
    private var key: SecretKey? = null

    override fun seal(plain: ByteArray): ByteArray {
        val k = key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        return byteArrayOf(12) + iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray {
        val k = key ?: throw VaultUnavailableException("key missing")
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, sealed, 1, 12))
            return cipher.doFinal(sealed, 13, sealed.size - 13)
        } catch (e: Exception) {
            throw VaultUnavailableException("cannot open", e)
        }
    }

    override fun destroyKey() {
        key = null
    }

    val hasKey: Boolean get() = key != null
}

/** A vault whose Keystore "fails this time" on demand, to prove a transient failure deletes nothing. */
class FlakyVault(private val inner: Vault) : Vault {
    var failOpen = false
    var failDestroy = false

    override fun seal(plain: ByteArray): ByteArray = inner.seal(plain)

    override fun open(sealed: ByteArray): ByteArray {
        if (failOpen) throw pe.esgtrazabilidad.reciclador.crypto.VaultTransientException("keystore busy")
        return inner.open(sealed)
    }

    override fun destroyKey() {
        if (failDestroy) throw pe.esgtrazabilidad.reciclador.crypto.VaultTransientException("could not delete the key")
        inner.destroyKey()
    }
}

/** An unsigned token with the claims the app reads (`cv`, `lc`): the app does not verify signatures. */
fun fakeAccessToken(consentVersion: Int?, location: Boolean, marker: String = "acc"): String {
    val payload = JSONObject().put("sub", "account").put("role", "RECYCLER").put("mark", marker)
    if (consentVersion != null) payload.put("cv", consentVersion)
    if (location) payload.put("lc", true)
    val enc = Base64.getUrlEncoder().withoutPadding()
    return listOf("""{"alg":"none"}""", payload.toString(), "sig").joinToString(".") { enc.encodeToString(it.toByteArray()) }
}

/** The server, scripted by the test. Counts calls and keeps the last tokens it issued. */
class FakeAuthApi : AuthApi {
    var refreshCounter = 0
    var consentVersionServerSide = 1
    var consentAccepted = false
    var locationAccepted = false
    var activateError: ApiException? = null
    var refreshError: ApiException? = null
    var logoutError: ApiException? = null
    var consentFetchError: ApiException? = null
    var loggedOutWith: String? = null
    var activations = 0

    /** Refresh tokens are made of this prefix and a counter, so tests can look for them on disk. */
    val tokenPrefix = "RT-SENTINEL-${System.nanoTime()}-"
    var expiresInSeconds = 900L

    private fun accessToken() = fakeAccessToken(
        consentVersion = if (consentAccepted) consentVersionServerSide else null,
        location = consentAccepted && locationAccepted,
        marker = "acc${refreshCounter}",
    )

    override suspend fun activate(code: String, deviceLabel: String, deviceInstallId: String): Tokens {
        activateError?.let { throw it }
        activations++
        return Tokens(accessToken(), "$tokenPrefix${++refreshCounter}", expiresInSeconds, consentRequired = !consentAccepted, consentVersion = consentVersionServerSide)
    }

    /** Runs while a refresh is "on the wire": lets a test change the phone's state in the middle of a call. */
    var onRefresh: (() -> Unit)? = null

    override suspend fun refresh(refreshToken: String): Tokens {
        onRefresh?.invoke()
        refreshError?.let { throw it }
        require(refreshToken.startsWith(tokenPrefix)) { "unknown refresh token" }
        return Tokens(accessToken(), "$tokenPrefix${++refreshCounter}", expiresInSeconds)
    }

    override suspend fun consent(accessToken: String): Consent {
        consentFetchError?.let { throw it }
        return Consent(consentVersionServerSide, "Texto del consentimiento", "Texto de la ubicación", 90, 12)
    }

    override suspend fun acceptConsent(accessToken: String, version: Int, accepted: Boolean, locationAccepted: Boolean) {
        require(version == consentVersionServerSide)
        consentAccepted = accepted
        this.locationAccepted = locationAccepted
    }

    override suspend fun logout(accessToken: String, refreshToken: String) {
        logoutError?.let { throw it }
        loggedOutWith = refreshToken
    }
}

fun revoked(code: String = "AUTH-017") = ApiException(401, code)
fun offline() = ApiException(0, null)
