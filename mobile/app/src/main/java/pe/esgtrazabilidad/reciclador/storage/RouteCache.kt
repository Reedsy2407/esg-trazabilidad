package pe.esgtrazabilidad.reciclador.storage

import java.io.File
import org.json.JSONException
import org.json.JSONObject
import pe.esgtrazabilidad.reciclador.TimeSource
import pe.esgtrazabilidad.reciclador.crypto.Vault
import pe.esgtrazabilidad.reciclador.crypto.VaultUnavailableException

/** The cached route and the instant it was downloaded (not the instant it was read). */
data class CachedRoute(val payload: String, val fetchedAtMillis: Long)

/**
 * The cached route (the neighbours' names, addresses and districts), sealed with the session key. It lives at most
 * 7 days since it was downloaded; past that it is deleted, and ONLY it (CA-24): pending records and workday points
 * are never touched by this expiry. A marker file with no personal data remembers that the last route was dropped
 * by expiry, so the screen can say so after a restart (it disappears with the next download or with a wipe).
 */
class RouteCache(dir: File, vault: Vault, private val time: TimeSource) {

    private val path = File(dir, FILE_NAME)
    private val expiredMark = File(dir, EXPIRED_MARK)
    private val file = SealedFile(path, vault)

    fun save(payload: String) {
        file.write(
            JSONObject().put("fetchedAt", time.nowMillis()).put("payload", payload).toString().toByteArray(Charsets.UTF_8),
        )
        expiredMark.delete()
    }

    /** The cached route with its download time, or null when there is none, it expired, or it can never be read. */
    fun loadWithDate(): CachedRoute? {
        val json = try {
            file.read()?.let { JSONObject(String(it, Charsets.UTF_8)) }
        } catch (e: VaultUnavailableException) {
            clear()
            return null
        } catch (e: JSONException) {
            clear()
            return null
        } ?: return null
        val fetchedAt = json.getLong("fetchedAt")
        if (time.nowMillis() - fetchedAt >= MAX_AGE_MILLIS) {
            file.delete()
            expiredMark.writeBytes(ByteArray(0))
            return null
        }
        return CachedRoute(json.getString("payload"), fetchedAt)
    }

    /** The cached route, or null when there is none, it expired, or it can never be read (a transient failure propagates). */
    fun load(): String? = loadWithDate()?.payload

    /** Deletes the route if it is 7 days old or more. True if it deleted one. */
    fun purgeIfExpired(): Boolean {
        if (!path.exists()) return false
        loadWithDate()
        return !path.exists()
    }

    /** True when the last route on this phone was dropped because it was 7 days old. */
    fun wasExpired(): Boolean = expiredMark.exists()

    fun clear() {
        file.delete()
        expiredMark.delete()
    }

    companion object {
        const val FILE_NAME = "route.bin"
        const val EXPIRED_MARK = "route.expired"
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
