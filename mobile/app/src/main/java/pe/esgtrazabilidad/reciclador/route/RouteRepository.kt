package pe.esgtrazabilidad.reciclador.route

import org.json.JSONException
import pe.esgtrazabilidad.reciclador.TimeSource
import pe.esgtrazabilidad.reciclador.crypto.VaultTransientException
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.CollectionApi
import pe.esgtrazabilidad.reciclador.net.RouteJson
import pe.esgtrazabilidad.reciclador.net.RouteStop
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.storage.RouteCache

/** What the route screen shows. [fetchedAtMillis] is always the DOWNLOAD time of [stops]. */
sealed interface RouteState {
    data object Loading : RouteState

    /** [stale]: the download failed this time and these are the saved stops (CA-42); [reason] says why, in Spanish. */
    data class Ready(
        val stops: List<RouteStop>,
        val fetchedAtMillis: Long,
        val stale: Boolean = false,
        val reason: String? = null,
    ) : RouteState

    /** Downloaded fine, but nothing is assigned to this recycler. */
    data class Empty(val fetchedAtMillis: Long) : RouteState

    /** No route on the phone and none could be downloaded. [expired]: the saved one was 7 days old and was deleted. */
    data class Unavailable(val expired: Boolean, val reason: String) : RouteState
}

/**
 * Downloads the recycler's route and keeps it in the sealed [RouteCache] (CA-24, CA-42). With network it downloads
 * when the screen opens; without it, it shows the saved one with its download date. Only the route is ever deleted
 * by the 7-day expiry (done by [RouteCache] itself, with the injected clock).
 */
class RouteRepository(
    private val api: CollectionApi,
    private val session: SessionManager,
    private val cache: RouteCache,
    private val time: TimeSource,
) {

    /** What is saved right now, without touching the network: for the first frame and for the offline start. */
    fun saved(): RouteState? {
        val cached = try {
            cache.loadWithDate()
        } catch (e: VaultTransientException) {
            return null
        } ?: return null
        return stateOf(cached.payload, cached.fetchedAtMillis)
    }

    /**
     * Downloads the route. A 401 is retried once with a fresh token and a revocation drops the session (both inside
     * [SessionManager.authorized]); a network failure, 5xx or 429 falls back to the saved route.
     */
    suspend fun refresh(): RouteState {
        val stops = try {
            session.authorized { api.route(it) }
        } catch (e: ApiException) {
            return fallback(reasonFor(e))
        }
        // The Keystore or the disk can fail this time: the downloaded route is still shown, just not saved.
        val fetchedAt = try {
            cache.save(RouteJson.write(stops))
            cache.loadWithDate()?.fetchedAtMillis ?: time.nowMillis()
        } catch (e: VaultTransientException) {
            time.nowMillis()
        } catch (e: java.io.IOException) {
            time.nowMillis()
        } catch (e: IllegalStateException) {
            time.nowMillis()
        }
        return if (stops.isEmpty()) RouteState.Empty(fetchedAt) else RouteState.Ready(stops, fetchedAt)
    }

    private fun fallback(reason: String): RouteState {
        val cached = try {
            cache.loadWithDate()
        } catch (e: VaultTransientException) {
            null
        } catch (e: java.io.IOException) {
            null
        }
        if (cached == null) return RouteState.Unavailable(expired = cache.wasExpired(), reason = reason)
        return when (val s = stateOf(cached.payload, cached.fetchedAtMillis)) {
            is RouteState.Ready -> s.copy(stale = true, reason = reason)
            null -> RouteState.Unavailable(expired = false, reason = reason)
            else -> s
        }
    }

    private fun stateOf(payload: String, fetchedAt: Long): RouteState? = try {
        val stops = RouteJson.parse(payload)
        if (stops.isEmpty()) RouteState.Empty(fetchedAt) else RouteState.Ready(stops, fetchedAt)
    } catch (e: JSONException) {
        cache.clear()
        null
    }

    private fun reasonFor(e: ApiException): String = when {
        e.isNetwork -> "No hay conexión con el servidor."
        e.status == 429 || e.status >= 500 -> "El servidor no respondió. Vuelve a intentarlo en un momento."
        e.status == 403 -> "Tu cuenta no puede ver la ruta ahora. Habla con el personal."
        e.status == 401 -> "La sesión no fue aceptada. Vuelve a abrir la app."
        else -> "No se pudo descargar la ruta."
    }
}
