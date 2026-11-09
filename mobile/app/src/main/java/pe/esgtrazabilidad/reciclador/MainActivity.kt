package pe.esgtrazabilidad.reciclador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.ui.CodeScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentUnavailableScreen
import pe.esgtrazabilidad.reciclador.ui.HomeScreen
import pe.esgtrazabilidad.reciclador.ui.LogoutDialogs
import pe.esgtrazabilidad.reciclador.ui.RecicladorTheme
import pe.esgtrazabilidad.reciclador.ui.RevokedScreen
import pe.esgtrazabilidad.reciclador.ui.pendingLabel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RecyclerApp
        setContent {
            RecicladorTheme {
                Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val vm: AppViewModel = viewModel { AppViewModel(app.session) }
                    AppContent(vm)
                }
            }
        }
    }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    val state by vm.sessionState.collectAsState()
    val ui by vm.ui.collectAsState()
    val dialog by vm.dialog.collectAsState()
    val enteringNewCode by vm.enteringNewCode.collectAsState()

    when (val s = state) {
        SessionState.Starting -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Abriendo…") }
        is SessionState.NeedsCode -> CodeScreen(s.notice, ui.busy, ui.error, vm::activate)
        is SessionState.NeedsConsent -> ConsentScreen(
            consent = s.consent,
            busy = ui.busy,
            error = ui.error,
            onAccept = vm::acceptConsent,
            onCancel = vm::cancelConsent,
        )
        is SessionState.ConsentUnavailable -> ConsentUnavailableScreen(ui.busy, ui.error, vm::retryConsent, vm::cancelConsent)
        is SessionState.Active -> HomeScreen(s, ui.busy, ui.error, vm::logout)
        is SessionState.Revoked -> if (enteringNewCode) {
            CodeScreen(
                notice = "Escribe el código nuevo. Tus ${pendingLabel(s.pendingCount)} siguen guardados y se subirán al activar.",
                busy = ui.busy,
                error = ui.error,
                onSubmit = vm::activate,
                onBack = vm::backToNotice,
            )
        } else {
            RevokedScreen(s.pendingCount, ui.error, onNewCode = vm::enterNewCode, onDiscard = vm::askDiscard)
        }
    }

    LogoutDialogs(
        dialog = dialog,
        busy = ui.busy,
        error = ui.error,
        onSync = vm::syncAndLogout,
        onCancel = vm::cancelLogout,
        onAskDiscard = vm::askDiscard,
        onConfirmDiscard = vm::confirmDiscard,
        onBackFromDiscard = vm::backFromDiscard,
        onDismissFailure = vm::dismissSyncFailure,
    )
}
