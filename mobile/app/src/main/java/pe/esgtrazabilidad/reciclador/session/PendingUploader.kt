package pe.esgtrazabilidad.reciclador.session

import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.storage.PendingRecord

/**
 * Sends pending records to the server and says which ones it CONFIRMED (201 new, or 200 already there by clientId).
 * Only a confirmed record may be removed from the phone (R18, CA-25). The real upload arrives with module 2
 * (the check-in route does not exist yet); module 1 ships the contract and the safe default below.
 */
fun interface PendingUploader {
    @Throws(ApiException::class)
    suspend fun upload(accessToken: String, records: List<PendingRecord>): Set<String>
}

/** Confirms nothing: until module 2 exists there is no route to send to, so no record is ever considered sent. */
object NothingUploadable : PendingUploader {
    override suspend fun upload(accessToken: String, records: List<PendingRecord>): Set<String> = emptySet()
}

/** What "Cerrar sesión" decides before touching anything. */
sealed interface LogoutPlan {
    /** There was nothing pending: the session was closed and everything was wiped. */
    data object Done : LogoutPlan

    /** [count] records are not on the server yet: nothing was deleted; the recycler must choose. */
    data class NeedsDecision(val count: Int) : LogoutPlan
}

/** The result of "Sincronizar ahora" while logging out. */
sealed interface SyncOutcome {
    /** The server confirmed every record: the session was closed and everything was wiped. */
    data object Done : SyncOutcome

    /** [count] records are still pending (no network, or the server did not confirm them): nothing was closed. */
    data class Remaining(val count: Int, val reason: String) : SyncOutcome
}
