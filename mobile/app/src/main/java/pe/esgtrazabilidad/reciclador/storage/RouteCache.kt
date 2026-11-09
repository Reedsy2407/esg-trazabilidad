package pe.esgtrazabilidad.reciclador.storage

import java.io.File
import org.json.JSONException
import org.json.JSONObject
import pe.esgtrazabilidad.reciclador.TimeSource
import pe.esgtrazabilidad.reciclador.crypto.Vault
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException

/**
 * The cached route (the neighbours' names, addresses and districts, which arrive in module 2), sealed with the
 * session key. It lives at most 7 days since it was fetched; past that it is deleted, and ONLY it (CA-24):
 * pending records are never touched by this expiry.
 */
class RouteCache(dir: File, vault: Vault, private val time: TimeSource) {

    private val path = File(dir, FILE_NAME)
    private val file = SealedFile(path, vault)

    fun save(payload: String) {
        file.write(
            JSONObject().put("fetchedAt", time.nowMillis()).put("payload", payload).toString().toByteArray(Charsets.UTF_8),
        )
    }

    /** The cached route, or null when there is none, it expired, or it can never be read (a transient failure propagates). */
    fun load(): String? {
        val json = try {
            file.read()?.let { JSONObject(String(it, Charsets.UTF_8)) }
        } catch (e: VaultUnavailableException) {
            clear()
            return null
        } catch (e: JSONException) {
            clear()
            return null
        } ?: return null
        if (time.nowMillis() - json.getLong("fetchedAt") >= MAX_AGE_MILLIS) {
            clear()
            return null
        }
        return json.getString("payload")
    }

    /** Deletes the route if it is 7 days old or more. True if it deleted one. */
    fun purgeIfExpired(): Boolean {
        if (!path.exists()) return false
        load()
        return !path.exists()
    }

    fun clear() = file.delete()

    companion object {
        const val FILE_NAME = "route.bin"
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
