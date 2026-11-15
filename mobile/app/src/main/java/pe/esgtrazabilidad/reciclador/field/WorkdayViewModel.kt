package pe.esgtrazabilidad.reciclador.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.esgtrazabilidad.reciclador.session.SessionState

/** Starts and stops the foreground service; the Android implementation lives next to the activity. */
interface TrackingControl {
    fun start()
    fun stop()
}

/** Why a workday was opened without GPS, in plain Spanish (R17, R32). Null when it records the route. */
object NoGpsReason {
    const val NOT_ACCEPTED = "No aceptaste la ubicación en el consentimiento: esta jornada se abre sin GPS."
    const val NO_PERMISSION = "El teléfono no dio permiso de ubicación: esta jornada se abre sin GPS."
    const val GPS_OFF = "El GPS del teléfono está apagado: esta jornada se abre sin GPS."
}

class WorkdayViewModel(
    private val manager: WorkdayManager,
    private val tracking: TrackingControl,
    sessionState: StateFlow<SessionState>,
) : ViewModel() {

    val ui: StateFlow<WorkdayUi> = manager.ui

    private val _gpsNotice = MutableStateFlow<String?>(null)

    /** Why this workday has no GPS, shown when it was just opened. */
    val gpsNotice: StateFlow<String?> = _gpsNotice.asStateFlow()

    private val _other = MutableStateFlow(false)

    /** A workday of another account is waiting on this phone: a new one cannot be opened over it. */
    val otherAccount: StateFlow<Boolean> = _other.asStateFlow()

    init {
        // Notices belong to a session: they go when it ends, not when the screen rotates.
        viewModelScope.launch { sessionState.collect { if (it !is SessionState.Active) leave() } }
    }

    /** The app opened: show what is there, send what is waiting, and bring the service back if it was killed. */
    fun open() {
        launch {
            manager.resume()
            if (manager.shouldRecord()) tracking.start()
        }
    }

    /** [recordGps] is true only when the recycler accepted the location AND the system gives it; else [whyNot] says why. */
    fun start(recordGps: Boolean, whyNot: String?) = launch {
        when (manager.start(recordGps)) {
            StartOutcome.STARTED -> {
                _gpsNotice.value = if (recordGps) null else whyNot
                if (recordGps) tracking.start()
            }
            StartOutcome.ALREADY_OPEN -> Unit
            StartOutcome.OTHER_ACCOUNT -> _other.value = true
        }
    }

    fun close() = launch {
        tracking.stop()
        manager.close()
    }

    /** "Reintentar": tries to send now, ignoring the waiting time. */
    fun retry() = launch { manager.sync(force = true) }

    /**
     * The session ended (logout, revocation): the notices of this session go; the data stays on disk. The service
     * is not stopped here (a screen rotation also leaves the composition): it stops itself on its next reading,
     * because [WorkdayManager.shouldRecord] is false without an active session.
     */
    fun leave() {
        _gpsNotice.value = null
        _other.value = false
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
