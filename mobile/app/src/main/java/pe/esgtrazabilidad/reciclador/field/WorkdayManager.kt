package pe.esgtrazabilidad.reciclador.field

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pe.esgtrazabilidad.reciclador.TimeSource
import pe.esgtrazabilidad.reciclador.crypto.VaultTransientException
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.net.CollectionApi
import pe.esgtrazabilidad.reciclador.net.GpsPointDto
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.session.TokenClaims

/** Waiting time after the n-th failure in a row: 15 s, 30 s, 1 min... never more than 15 minutes. */
object Backoff {
    const val BASE_MILLIS = 15_000L
    const val MAX_MILLIS = 15 * 60 * 1000L

    fun delayMillis(failures: Int): Long {
        if (failures <= 0) return 0
        val doublings = (failures - 1).coerceAtMost(20)
        return (BASE_MILLIS shl doublings).coerceAtMost(MAX_MILLIS)
    }
}

/** What the workday screen shows. [workday] is null when there is none in progress. */
data class WorkdayUi(val workday: WorkdayView? = null, val notice: String? = null, val syncing: Boolean = false)

data class WorkdayView(
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val status: WorkdayStatus,
    val recordingGps: Boolean,
    val pendingPoints: Int,
    val confirmedByServer: Boolean,
)

enum class StartOutcome { STARTED, ALREADY_OPEN, OTHER_ACCOUNT }

enum class SyncOutcome { NOTHING, DONE, WAITING, SESSION_ENDED, REJECTED, OTHER_ACCOUNT }

/**
 * The workday on the phone and its way to the server (R21, R22, CA-43, CA-47).
 *
 * - The workday has a [WorkdayRow.clientId] made once, so retrying "open" never makes a second workday.
 * - Every point gets the next `deviceSeq` of its workday in the same transaction that stores it.
 * - A point leaves the phone only after the server answered 2xx for its batch (accepted or duplicate).
 * - Failures (no network, 5xx, 429...) keep everything and wait longer each time; nothing relies on a timer of
 *   its own: sending is tried when a point arrives, when the app opens and when the recycler asks.
 * - Revocation: the session is dropped by [SessionManager.authorized]; the points stay on the phone.
 */
class WorkdayManager(
    private val store: FieldStore,
    private val api: CollectionApi,
    private val session: SessionManager,
    private val time: TimeSource,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val syncLock = Mutex()
    private val _ui = MutableStateFlow(WorkdayUi())
    val ui: StateFlow<WorkdayUi> = _ui.asStateFlow()

    private var failures = 0
    private var notBeforeMillis = 0L
    private var notice: String? = null

    /** Called when the app opens: shows what is there and tries to send. */
    suspend fun resume(): SyncOutcome {
        reload()
        return sync(force = true)
    }

    /** Opens a workday on the phone (and on the server if it can). [recordGps] is decided by the caller. */
    suspend fun start(recordGps: Boolean): StartOutcome {
        val existing = store.current()
        if (existing != null) {
            val mine = session.currentRecyclerId()
            return if (existing.recyclerId != null && mine != null && existing.recyclerId != mine) {
                StartOutcome.OTHER_ACCOUNT
            } else {
                StartOutcome.ALREADY_OPEN
            }
        }
        store.create(
            WorkdayRow(
                clientId = newId(),
                serverId = null,
                recyclerId = session.currentRecyclerId(),
                startedAtMillis = time.nowMillis(),
                endedAtMillis = null,
                status = WorkdayStatus.OPEN,
                recordingGps = recordGps,
                nextSeq = 1,
            ),
        )
        notice = null
        reload()
        sync(force = true)
        return StartOutcome.STARTED
    }

    /** True while a service should keep recording: an OPEN workday of this account that records GPS. */
    suspend fun shouldRecord(): Boolean {
        if (session.state.value !is SessionState.Active) return false
        val w = store.current() ?: return false
        if (w.status != WorkdayStatus.OPEN || !w.recordingGps) return false
        val mine = session.currentRecyclerId()
        return w.recyclerId == null || mine == null || w.recyclerId == mine
    }

    /** The system stopped giving the location (permission taken away, GPS off): the workday goes on without GPS. */
    suspend fun stopRecordingGps(reason: String) {
        val w = store.current() ?: return
        if (w.recordingGps) store.setRecordingGps(w.clientId, false)
        notice = reason
        reload()
    }

    /** Stores one reading. Returns false when it was not stored (no recording workday, or an invalid reading). */
    suspend fun recordPoint(lat: Double, lng: Double, accuracyM: Double): Boolean {
        if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
        val accuracy = if (accuracyM.isFinite() && accuracyM >= 0) accuracyM else 0.0
        val w = store.current() ?: return false
        if (w.status != WorkdayStatus.OPEN || !w.recordingGps) return false
        try {
            store.appendPoint(w.clientId, time.nowMillis(), lat, lng, accuracy)
        } catch (e: IllegalStateException) {
            return false // the workday was just confirmed closed and removed
        }
        reload()
        return true
    }

    /**
     * After a stored point: sends when a batch is worth it (or the server does not know the workday yet) and the
     * waiting time after the last failure is over. Driven by the arrival of points, not by a timer.
     */
    suspend fun onPointStored(): SyncOutcome {
        val v = _ui.value.workday ?: return SyncOutcome.NOTHING
        if (v.pendingPoints < FLUSH_THRESHOLD && v.confirmedByServer) return SyncOutcome.WAITING
        return syncIfDue()
    }

    /** "Cerrar jornada": closes it on the phone first (the record never depends on the network), then tells the server. */
    suspend fun close(): SyncOutcome {
        val w = store.current() ?: return SyncOutcome.NOTHING
        if (w.status == WorkdayStatus.OPEN) store.markClosed(w.clientId, time.nowMillis())
        reload()
        return sync(force = true)
    }

    /** Tries to send when the waiting time after the last failure is over. */
    suspend fun syncIfDue(): SyncOutcome = sync(force = false)

    suspend fun sync(force: Boolean): SyncOutcome = syncLock.withLock {
        val w = store.current() ?: run {
            reload()
            return SyncOutcome.NOTHING
        }
        if (!force && time.nowMillis() < notBeforeMillis) return SyncOutcome.WAITING
        _ui.value = _ui.value.copy(syncing = true)
        try {
            val outcome = send(w)
            if (outcome == SyncOutcome.DONE || outcome == SyncOutcome.NOTHING) {
                failures = 0
                notBeforeMillis = 0
                notice = null
            }
            outcome
        } finally {
            reload(syncing = false)
        }
    }

    private suspend fun send(w: WorkdayRow): SyncOutcome {
        try {
            session.authorized { token ->
                val mine = TokenClaims.parse(token).recyclerId
                if (w.recyclerId != null && mine != null && w.recyclerId != mine) throw OtherAccount()
                if (w.recyclerId == null && mine != null) store.setRecyclerId(w.clientId, mine)

                var serverId = w.serverId
                if (serverId == null) {
                    serverId = api.openWorkday(token, w.startedAtMillis, w.clientId).workdayId
                    store.setServerId(w.clientId, serverId)
                }
                while (true) {
                    val batch = store.nextBatch(w.clientId, MAX_BATCH)
                    if (batch.isEmpty()) break
                    api.sendPoints(
                        token,
                        serverId,
                        batch.map { GpsPointDto(it.deviceSeq, it.recordedAtMillis, it.lat, it.lng, it.accuracyM) },
                    )
                    // 2xx: accepted or duplicate, either way the server has it.
                    store.deletePoints(batch.map { it.id })
                }
                if (w.status == WorkdayStatus.CLOSED) {
                    api.closeWorkday(token, serverId, w.endedAtMillis ?: time.nowMillis())
                    // A point stored while this ran stays and goes with the next send (the server takes it for 5 minutes).
                    if (store.pointCount(w.clientId) == 0) store.deleteWorkday(w.clientId)
                }
            }
            return SyncOutcome.DONE
        } catch (e: OtherAccount) {
            notice = OTHER_ACCOUNT_NOTICE
            return SyncOutcome.OTHER_ACCOUNT
        } catch (e: ApiException) {
            return failed(w, e)
        } catch (e: VaultTransientException) {
            return transientFailure()
        } catch (e: IOException) {
            return transientFailure()
        }
    }

    private suspend fun failed(w: WorkdayRow, e: ApiException): SyncOutcome {
        when {
            e.isRevocation -> {
                notice = SESSION_ENDED_NOTICE
                return SyncOutcome.SESSION_ENDED
            }
            e.code == ApiException.CODE_WORKDAY_CLOSED -> {
                // The server will never take these points. They stay on the phone; nothing is deleted.
                store.markRejected(w.clientId, time.nowMillis())
                notice = REJECTED_NOTICE
                return SyncOutcome.REJECTED
            }
            e.code == ApiException.CODE_WORKDAY_NOT_FOUND -> {
                // The server does not know that id: forget it; opening again by clientId is idempotent.
                store.setServerId(w.clientId, null)
                notice = RETRYING_NOTICE
            }
            e.code == ApiException.CODE_CONSENT_REQUIRED -> notice = CONSENT_NOTICE
            e.isNetwork -> notice = OFFLINE_NOTICE
            else -> notice = RETRYING_NOTICE
        }
        return transientFailure(keepNotice = true)
    }

    private fun transientFailure(keepNotice: Boolean = false): SyncOutcome {
        failures++
        notBeforeMillis = time.nowMillis() + Backoff.delayMillis(failures)
        if (!keepNotice) notice = RETRYING_NOTICE
        return SyncOutcome.WAITING
    }

    suspend fun reload(syncing: Boolean = false) {
        val w = store.current()
        _ui.value = WorkdayUi(
            workday = w?.let {
                WorkdayView(it.startedAtMillis, it.endedAtMillis, it.status, it.recordingGps, store.pointCount(it.clientId), it.serverId != null)
            },
            notice = notice,
            syncing = syncing,
        )
    }

    private class OtherAccount : Exception()

    companion object {
        const val MAX_BATCH = 500

        /** Points waiting before a send is tried while recording (about two minutes at one point per 10 s). */
        const val FLUSH_THRESHOLD = 12
        const val OFFLINE_NOTICE = "Sin conexión: tu jornada y tus puntos están guardados en el teléfono y se enviarán cuando vuelva la red."
        const val RETRYING_NOTICE = "No se pudo enviar ahora. Está guardado en el teléfono y se reintentará."
        const val CONSENT_NOTICE = "Falta aceptar el consentimiento vigente para enviar la ubicación. Tus puntos siguen guardados."
        const val REJECTED_NOTICE = "El servidor ya tenía esta jornada cerrada y no recibe más puntos. Se conservan en el teléfono, sin enviarse."
        const val SESSION_ENDED_NOTICE = "La sesión terminó. Los puntos de tu jornada siguen guardados en el teléfono."
        const val OTHER_ACCOUNT_NOTICE = "Hay una jornada sin enviar de otra cuenta en este teléfono. Actívala con el código de esa cuenta para enviarla."
    }
}
