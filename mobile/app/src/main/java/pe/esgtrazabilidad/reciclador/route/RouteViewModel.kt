package pe.esgtrazabilidad.reciclador.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The route screen's state. Opening the app (or the screen) downloads; the saved route shows meanwhile. */
class RouteViewModel(private val repository: RouteRepository) : ViewModel() {

    private val _state = MutableStateFlow<RouteState>(repository.saved() ?: RouteState.Loading)
    val state: StateFlow<RouteState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var job: Job? = null

    /**
     * The route screen came on stage (the app opened, or a new session started on this phone): start from what is
     * saved NOW, never from what a previous session left in memory, and download.
     */
    fun open() {
        job?.cancel()
        _state.value = repository.saved() ?: RouteState.Loading
        reload()
    }

    /** The screen left the stage (logout, revocation): nothing of this session's route stays in memory. */
    fun close() {
        job?.cancel()
        _state.value = RouteState.Loading
        _refreshing.value = false
    }

    fun reload() {
        if (job?.isActive == true) return
        _refreshing.value = true
        job = viewModelScope.launch {
            try {
                _state.value = repository.refresh()
            } finally {
                _refreshing.value = false
            }
        }
    }
}
