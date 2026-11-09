package pe.esgtrazabilidad.reciclador.storage

import java.io.File
import org.json.JSONException
import org.json.JSONObject
import pe.esgtrazabilidad.reciclador.crypto.Vault
import pe.esgtrazabilidad.reciclador.crypto.VaultTransientException
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException

/**
 * The refresh token, sealed with the session key: the only credential that ever reaches the disk, and never
 * in clear. The access token is not stored at all (it lives in memory for 15 minutes).
 */
class SessionStore(dir: File, private val vault: Vault) {

    private val path = File(dir, FILE_NAME)
    private val file = SealedFile(path, vault)

    /** True when a session file exists (readable or not): lets the caller say why a stored session was lost. */
    fun hasStoredSession(): Boolean = path.exists()

    fun save(refreshToken: String) {
        file.write(JSONObject().put("refresh", refreshToken).toString().toByteArray(Charsets.UTF_8))
    }

    /**
     * The token, or null when there is none or it can never be read again (then it is wiped: a new code is needed).
     * A transient Keystore failure is NOT swallowed here: it propagates, and nothing is deleted.
     */
    fun load(): String? = try {
        file.read()?.let { JSONObject(String(it, Charsets.UTF_8)).getString("refresh") }
    } catch (e: VaultUnavailableException) {
        clear()
        null
    } catch (e: JSONException) {
        clear()
        null
    }

    /** Credentials and the session key. */
    fun clear() {
        file.delete()
        try {
            vault.destroyKey()
        } catch (e: VaultTransientException) {
            // The file is already gone, so the key left behind opens nothing.
        }
    }

    companion object {
        const val FILE_NAME = "session.bin"
    }
}
