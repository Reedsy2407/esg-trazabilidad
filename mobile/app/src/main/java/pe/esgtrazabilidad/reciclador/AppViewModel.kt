package pe.esgtrazabilidad.reciclador

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pe.esgtrazabilidad.reciclador.crypto.VaultTransientException
import pe.esgtrazabilidad.reciclador.net.ApiException
import pe.esgtrazabilidad.reciclador.session.DiscardTicket
import pe.esgtrazabilidad.reciclador.session.LogoutPlan
import pe.esgtrazabilidad.reciclador.session.SessionManager
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.session.SyncOutcome
import pe.esgtrazabilidad.reciclador.ui.LogoutDialog

/** What a screen shows besides the session itself: a request in flight and the last error, in plain Spanish. */
data class UiState(val busy: Boolean = false, val error: String? = null)

class AppViewModel(private val session: SessionManager) : ViewModel() {

    val sessionState = session.state
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _dialog = MutableStateFlow<LogoutDialog?>(null)
    val dialog: StateFlow<LogoutDialog?> = _dialog.asStateFlow()

    /** After a revocation: the recycler chose to type the new code (their pending records stay and wait). */
    private val _enteringNewCode = MutableStateFlow(false)
    val enteringNewCode: StateFlow<Boolean> = _enteringNewCode.asStateFlow()

    init {
        viewModelScope.launch { session.start() }
    }

    fun activate(code: String) = run {
        session.activate(code)
        _enteringNewCode.value = false
    }

    fun acceptConsent(locationAccepted: Boolean) = run { session.acceptConsent(locationAccepted) }

    fun retryConsent() = run { session.retryConsent() }

    /** The consent was declined: nothing can be done in the app without it. */
    fun cancelConsent() = run { session.abandon() }

    // ---- logging out (CA-20) ----------------------------------------------------------------

    fun logout() = run {
        _dialog.value = when (val plan = session.requestLogout()) {
            LogoutPlan.Done -> null
            is LogoutPlan.NeedsDecision -> LogoutDialog.Decision(plan.count)
        }
    }

    fun syncAndLogout() = run {
        _dialog.value = when (val outcome = session.logoutAfterSync()) {
            SyncOutcome.Done -> null
            is SyncOutcome.Remaining -> if (session.state.value is SessionState.Revoked) null else LogoutDialog.SyncFailed(outcome.count, outcome.reason)
        }
    }

    /** "Cancelar": nothing was deleted, nothing is called. */
    fun cancelLogout() {
        _dialog.value = null
    }

    /** First step of "Descartar": freezes what would be discarded and shows the second confirmation with its count. */
    fun askDiscard() = run {
        _dialog.value = LogoutDialog.ConfirmDiscard(session.beginDiscard())
    }

    /** "No, volver" in the second confirmation: back to the three options, or to the notice if the session was revoked. */
    fun backFromDiscard() = run {
        session.cancelDiscard()
        _dialog.value = if (session.state.value is SessionState.Revoked) null else LogoutDialog.Decision(session.pendingCount())
    }

    fun confirmDiscard(ticket: DiscardTicket) = run {
        try {
            // The number passed is the one the second dialog showed the recycler.
            session.confirmDiscard(ticket, ticket.count)
            _dialog.value = null
        } catch (e: IllegalArgumentException) {
            // What would be discarded changed since it was shown: nothing was deleted; ask again with the real number.
            session.cancelDiscard()
            _dialog.value = if (session.state.value is SessionState.Revoked) null else LogoutDialog.Decision(session.pendingCount())
            _ui.value = UiState(busy = true, error = COUNT_CHANGED)
        }
    }

    fun dismissSyncFailure() {
        _dialog.value = null
    }

    // ---- after a revocation -----------------------------------------------------------------

    fun enterNewCode() {
        _enteringNewCode.value = true
    }

    fun backToNotice() {
        _enteringNewCode.value = false
        clearError()
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    private fun run(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.value = UiState(busy = true)
        viewModelScope.launch {
            _ui.value = try {
                block()
                _ui.value.copy(busy = false)
            } catch (e: ApiException) {
                UiState(error = messageFor(e))
            } catch (e: VaultTransientException) {
                UiState(error = STORAGE_PROBLEM)
            } catch (e: IOException) {
                UiState(error = STORAGE_PROBLEM)
            } catch (e: IllegalStateException) {
                UiState(error = STORAGE_PROBLEM)
            }
        }
    }

    companion object {
        const val STORAGE_PROBLEM = "No se pudo guardar o leer los datos seguros del teléfono. Vuelve a intentarlo."
        const val COUNT_CHANGED = "La cantidad de registros cambió. Vuelve a intentarlo."

        fun messageFor(e: ApiException): String = when {
            e.isNetwork -> "No hay conexión con el servidor. Revisa tu red y vuelve a intentarlo."
            e.code == ApiException.CODE_INVALID_ACTIVATION -> "El código no es válido o venció. Pide uno nuevo al personal."
            e.code == ApiException.CODE_TOO_MANY_ATTEMPTS -> "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."
            e.code == ApiException.CODE_ACCOUNT_INACTIVE -> "Tu cuenta no está activa. Habla con el personal."
            e.isRevocation || e.code == ApiException.CODE_REFRESH_INVALID -> "La sesión terminó. Pide un código nuevo al personal."
            e.status >= 500 -> "El servidor tuvo un problema. Vuelve a intentarlo en un momento."
            else -> "No se pudo completar la operación. Vuelve a intentarlo."
        }
    }
}
