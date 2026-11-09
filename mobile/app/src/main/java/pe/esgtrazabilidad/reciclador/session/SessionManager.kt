package pe.esgtrazabilidad.reciclador.session

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.esgtrazabilidad.reciclador.TimeSource
import pe.esgtrazabilidad.reciclador.crypto.VaultTransientException
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.AuthApi
import pe.esgtrazabilidad.reciclador.net.Tokens
import pe.esgtrazabilidad.reciclador.storage.PendingStore
import pe.esgtrazabilidad.reciclador.storage.RouteCache
import pe.esgtrazabilidad.reciclador.storage.SessionStore

/**
 * The recycler's session. The access token lives only in this object's memory; the refresh token goes to disk
 * only sealed by [SessionStore]. Every change of screen is a change of [state].
 *
 * The rule that shapes everything below (R18): the pending records are never deleted without the recycler's
 * explicit, doubly confirmed choice, and never because a session ended. Logging out, a revocation and the 7-day
 * route expiry wipe the session and the route; they do not touch [PendingStore]. Its only deletions are:
 * a server confirmation ([logoutAfterSync]) and a discard that holds a [DiscardTicket] ([confirmDiscard]).
 */
class SessionManager(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val routeCache: RouteCache,
    private val pending: PendingStore,
    private val uploader: PendingUploader,
    private val time: TimeSource,
    private val deviceLabel: String,
    private val appDir: File,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Starting)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private var accessToken: String? = null
    private var accessExpiresAtMillis = 0L
    private var refreshToken: String? = null

    /** The ticket handed out by [beginDiscard] and not yet used or cancelled: only this one can discard. */
    private var armedTicket: DiscardTicket? = null

    /** Reads the stored session and, if there is one, refreshes it. Safe to call on every app start. */
    suspend fun start() = mutex.withLock {
        try {
            // Opening the app is a moment the 7-day limit of the cached route is applied, with or without network.
            routeCache.purgeIfExpired()
            val hadSession = sessionStore.hasStoredSession()
            val stored = sessionStore.load()
            if (stored == null) {
                // A session file that could never be read again (its key is gone) must not look like a first run.
                _state.value = idleState(lostSession = hadSession)
                return@withLock
            }
            refreshToken = stored
            try {
                adopt(api.refresh(stored))
                _state.value = stateFromClaims()
            } catch (e: ApiException) {
                if (endsTheSession(e)) {
                    revokeLocked(e.code)
                } else {
                    // No network, a waking service, a rate limit: the session is fine, it just could not refresh now.
                    _state.value = SessionState.Active(offline = true)
                }
            }
        } catch (e: VaultTransientException) {
            // The Keystore failed this time. Nothing is deleted: closing and reopening the app may fix it.
            _state.value = SessionState.NeedsCode(notice = KEYSTORE_BUSY)
        } catch (e: java.io.IOException) {
            _state.value = SessionState.NeedsCode(notice = KEYSTORE_BUSY)
        }
    }

    /** Trades the code for a session. Throws [ApiException] (AUTH-014 wrong or used code, AUTH-015 too many tries). */
    suspend fun activate(code: String) = mutex.withLock {
        val tokens = api.activate(code.trim(), deviceLabel, UUID.randomUUID().toString())
        // The code is spent from here on: whatever happens next, the stored session must survive it.
        adopt(tokens)
        _state.value = stateFromClaims()
    }

    /** Fetches the consent again after [SessionState.ConsentUnavailable]. */
    suspend fun retryConsent() = mutex.withLock {
        if (_state.value is SessionState.ConsentUnavailable) _state.value = stateFromClaims()
    }

    /** Records the answer to the consent in force and takes the token that carries it. */
    suspend fun acceptConsent(locationAccepted: Boolean) = mutex.withLock {
        val current = _state.value as? SessionState.NeedsConsent ?: return@withLock
        val token = accessToken ?: throw ApiException(401, ApiException.CODE_REFRESH_INVALID)
        api.acceptConsent(token, current.consent.version, accepted = true, locationAccepted = locationAccepted)
        val stored = refreshToken ?: throw ApiException(401, ApiException.CODE_REFRESH_INVALID)
        adopt(api.refresh(stored))
        _state.value = stateFromClaims()
    }

    /** Gives up the activation in progress (the consent was declined): drops the session, never the pending records. */
    suspend fun abandon() = mutex.withLock {
        dropSession()
        _state.value = idleState(lostSession = false)
    }

    /** How many records are waiting to be uploaded. */
    suspend fun pendingCount(): Int = mutex.withLock { pending.count() }

    // ---- a valid access token, refreshed by itself ------------------------------------------

    /**
     * The access token for the next call: the one in memory while it is valid, otherwise a refresh (one at a time:
     * the lock makes simultaneous callers share a single rotation instead of racing). A revocation found here drops
     * the session and rethrows; any other failure leaves the session as it was.
     */
    suspend fun accessToken(): String = mutex.withLock { validAccessToken() }

    /**
     * Runs [call] with a valid token. If the server answers AUTH-017 or AUTH-018 to it (CA-21), the session is dropped
     * at once, the pending records are kept, and the error is rethrown.
     */
    suspend fun <T> authorized(call: suspend (accessToken: String) -> T): T {
        val token = accessToken()
        try {
            return call(token)
        } catch (e: ApiException) {
            if (e.isRevocation) mutex.withLock { revokeLocked(e.code) }
            throw e
        }
    }

    private suspend fun validAccessToken(): String {
        // Trying to sync is the other moment the 7-day limit of the cached route is applied.
        routeCache.purgeIfExpired()
        val current = accessToken
        if (current != null && time.nowMillis() < accessExpiresAtMillis - SAFETY_MARGIN_MILLIS) return current
        val stored = refreshToken ?: sessionStore.load() ?: throw ApiException(401, ApiException.CODE_REFRESH_INVALID)
        try {
            adopt(api.refresh(stored))
        } catch (e: ApiException) {
            if (endsTheSession(e)) revokeLocked(e.code)
            throw e
        }
        return accessToken ?: throw ApiException(401, ApiException.CODE_REFRESH_INVALID)
    }

    // ---- logging out (CA-20) ----------------------------------------------------------------

    /**
     * "Cerrar sesión". With nothing pending it closes and wipes at once (branch a). With pending records it deletes
     * NOTHING and returns [LogoutPlan.NeedsDecision]: the recycler then chooses [logoutAfterSync], cancels (calls
     * nothing) or starts a discard with [beginDiscard].
     */
    suspend fun requestLogout(): LogoutPlan {
        if (pending.count() > 0) return LogoutPlan.NeedsDecision(pending.count())
        return try {
            finishLogout(expectedIds = emptyList())
            LogoutPlan.Done
        } catch (e: PendingChangedException) {
            // A record was added while the session was being closed: it must be decided about, not wiped.
            LogoutPlan.NeedsDecision(pending.count())
        }
    }

    /**
     * "Sincronizar ahora": needs network and a valid session. It closes only when the server confirmed EVERY record;
     * what it did not confirm stays, and so does the session.
     */
    suspend fun logoutAfterSync(): SyncOutcome {
        val token = try {
            accessToken()
        } catch (e: ApiException) {
            return SyncOutcome.Remaining(pending.count(), reasonFor(e))
        }
        val confirmed = try {
            uploader.upload(token, pending.list())
        } catch (e: ApiException) {
            if (e.isRevocation) mutex.withLock { revokeLocked(e.code) }
            return SyncOutcome.Remaining(pending.count(), reasonFor(e))
        }
        pending.removeConfirmed(confirmed)
        if (pending.count() > 0) return SyncOutcome.Remaining(pending.count(), NOT_CONFIRMED)
        return try {
            finishLogout(expectedIds = emptyList())
            SyncOutcome.Done
        } catch (e: PendingChangedException) {
            SyncOutcome.Remaining(pending.count(), NOT_CONFIRMED)
        }
    }

    /**
     * First step of "Descartar": freezes what is about to be discarded (how many records and exactly which) and arms
     * the only [DiscardTicket] that [confirmDiscard] will accept. Deleting nothing; works with a live session and
     * after a revocation. Starting again replaces the ticket, so an old dialog cannot discard anything new.
     */
    suspend fun beginDiscard(): DiscardTicket = mutex.withLock {
        val ids = pending.list().map { it.id }
        DiscardTicket(count = ids.size, ids = ids).also { armedTicket = it }
    }

    /** "No, volver" / dismissing the second confirmation: the ticket stops being valid. */
    suspend fun cancelDiscard() = mutex.withLock { armedTicket = null }

    /**
     * Second step, "Sí, descartar N registros". It needs the armed [DiscardTicket] AND the number the recycler read in
     * the second dialog ([shownCount]); with anything else, or if the pending records are no longer exactly the ones
     * the ticket froze (one was uploaded or added meanwhile), nothing is deleted and [IllegalArgumentException] says so.
     * The check and the local wipe happen together under the lock, so no record added in between is lost.
     */
    suspend fun confirmDiscard(ticket: DiscardTicket, shownCount: Int) {
        mutex.withLock {
            require(armedTicket === ticket) { "That discard was not started, or was replaced or cancelled" }
            require(shownCount == ticket.count) { "The confirmation said $shownCount but the ticket froze ${ticket.count}" }
        }
        try {
            finishLogout(expectedIds = ticket.ids)
        } catch (e: PendingChangedException) {
            mutex.withLock { armedTicket = null }
            throw IllegalArgumentException("The pending records changed since the confirmation was shown", e)
        }
        mutex.withLock { armedTicket = null }
    }

    /**
     * The end of every logout. A valid token is obtained first (it may need the network), then, in ONE critical
     * section, the pending records are compared with [expectedIds]: if they differ, nothing is wiped and
     * [PendingChangedException] is thrown. Otherwise the session, the route, every other file, the pending records
     * and their key go. Only afterwards is the server told (best effort, with the token taken before the wipe), so
     * logging out works without network and no local state depends on that call.
     */
    private suspend fun finishLogout(expectedIds: List<String>) {
        val token = try {
            accessToken()
        } catch (e: ApiException) {
            null // no session left to revoke, or no network: the phone is wiped regardless
        }
        val refresh = mutex.withLock { refreshToken }
        mutex.withLock {
            if (pending.list().map { it.id } != expectedIds) throw PendingChangedException()
            pending.discardAll()
            dropSession()
            _state.value = SessionState.NeedsCode()
        }
        if (token != null && refresh != null) {
            try {
                api.logout(token, refresh)
            } catch (e: ApiException) {
                // Best effort.
            }
        }
    }

    private class PendingChangedException : Exception()

    // ---- internals --------------------------------------------------------------------------

    private fun adopt(tokens: Tokens) {
        sessionStore.save(tokens.refreshToken)
        refreshToken = tokens.refreshToken
        accessToken = tokens.accessToken
        accessExpiresAtMillis = time.nowMillis() + tokens.expiresInSeconds * 1000
    }

    /**
     * Without `cv` the consent in force has not been accepted (first time, or its periods changed): ask again.
     * If the consent cannot be fetched now, the session stays (the code is already spent) and the app offers to retry.
     */
    private suspend fun stateFromClaims(): SessionState {
        val token = accessToken.orEmpty()
        val claims = TokenClaims.parse(token)
        if (claims.consentVersion == null) {
            return try {
                SessionState.NeedsConsent(api.consent(token))
            } catch (e: ApiException) {
                if (endsTheSession(e)) throw e
                SessionState.ConsentUnavailable(reason = e.message.orEmpty())
            }
        }
        return SessionState.Active(locationAccepted = claims.locationAccepted)
    }

    /** Only the server saying the session is over ends it; any other failure leaves it alone. */
    private fun endsTheSession(e: ApiException): Boolean =
        e.isRevocation || e.code == ApiException.CODE_REFRESH_INVALID || e.status == 401 || e.status == 403

    /**
     * The session is over for a reason that is not the recycler's choice (CA-21): credentials, session key and route
     * go at once; the pending records stay, with their own key, and the screen says how many and why. If the Keystore
     * fails while counting them, the state still changes (the session IS gone) and says to reopen the app.
     */
    private fun revokeLocked(code: String?) {
        dropSession()
        _state.value = try {
            val count = pending.count()
            if (count > 0) SessionState.Revoked(count, code) else SessionState.NeedsCode(notice = SESSION_ENDED)
        } catch (e: VaultTransientException) {
            SessionState.NeedsCode(notice = KEYSTORE_BUSY)
        }
    }

    private fun idleState(lostSession: Boolean): SessionState {
        val count = try {
            pending.count()
        } catch (e: VaultTransientException) {
            return SessionState.NeedsCode(notice = KEYSTORE_BUSY)
        }
        return when {
            // After a revocation the blocking notice must survive an app restart: nothing is discarded by itself.
            count > 0 -> SessionState.Revoked(count, null)
            lostSession -> SessionState.NeedsCode(notice = SESSION_ENDED)
            else -> SessionState.NeedsCode()
        }
    }

    /** Credentials, the session key, the route and every file except the pending records and their key. */
    private fun dropSession() {
        accessToken = null
        refreshToken = null
        accessExpiresAtMillis = 0L
        sessionStore.clear()
        routeCache.clear()
        val keep = setOf(PendingStore.FILE_NAME, PendingStore.FILE_NAME + ".tmp")
        appDir.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.deleteRecursively() }
    }

    private fun reasonFor(e: ApiException): String = when {
        e.isNetwork -> "No hay conexión con el servidor. Conéctate y vuelve a intentarlo."
        e.isRevocation -> "La sesión fue revocada."
        else -> "El servidor no respondió como se esperaba. Vuelve a intentarlo en un momento."
    }

    companion object {
        const val SESSION_ENDED = "La sesión terminó. Pide un código nuevo al personal."
        const val KEYSTORE_BUSY = "No se pudo leer la sesión guardada en este momento. Cierra la app y vuelve a abrirla."
        const val NOT_CONFIRMED = "El servidor no confirmó todos los registros."

        /** A token this close to its end is refreshed first, so a call never starts with one that dies on the way. */
        private const val SAFETY_MARGIN_MILLIS = 30_000L
    }
}

/**
 * Proof that the first step of a discard happened. It freezes how many records and which ones; only the ticket handed
 * out last, and only with the number the recycler read in the second confirmation, can discard (see
 * [SessionManager.confirmDiscard]). The constructor is internal: callers cannot forge one.
 */
class DiscardTicket internal constructor(val count: Int, internal val ids: List<String>)
