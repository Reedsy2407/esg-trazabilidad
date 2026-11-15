package pe.esgtrazabilidad.reciclador

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.remember
import pe.esgtrazabilidad.reciclador.field.LocationAvailability
import pe.esgtrazabilidad.reciclador.field.NoGpsReason
import pe.esgtrazabilidad.reciclador.field.TrackingControl
import pe.esgtrazabilidad.reciclador.field.TrackingService
import pe.esgtrazabilidad.reciclador.field.WorkdayViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import pe.esgtrazabilidad.reciclador.route.RouteViewModel
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
private fun workdayViewModel(): WorkdayViewModel {
    val app = LocalContext.current.applicationContext as RecyclerApp
    val tracking = remember(app) {
        object : TrackingControl {
            override fun start() = TrackingService.start(app)
            override fun stop() = TrackingService.stop(app)
        }
    }
    return viewModel { WorkdayViewModel(app.workdays, tracking, app.session.state) }
}

private fun locationPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    // The notice of the foreground service; refusing it does not stop the service, only hides the notice.
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

/** GPS only if the recycler accepted the location AND the system gives it right now; otherwise it opens without. */
private fun openWorkday(vm: WorkdayViewModel, context: Context, locationAccepted: Boolean) {
    val reason = when {
        !locationAccepted -> NoGpsReason.NOT_ACCEPTED
        !LocationAvailability.permissionGranted(context) -> NoGpsReason.NO_PERMISSION
        !LocationAvailability.gpsEnabled(context) -> NoGpsReason.GPS_OFF
        else -> null
    }
    vm.start(recordGps = reason == null, whyNot = reason)
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
        is SessionState.Active -> {
            val routes = (LocalContext.current.applicationContext as RecyclerApp).route
            val routeVm: RouteViewModel = viewModel { RouteViewModel(routes) }
            LaunchedEffect(Unit) { routeVm.open() }
            DisposableEffect(Unit) { onDispose { routeVm.close() } }
            val route by routeVm.state.collectAsState()
            val refreshing by routeVm.refreshing.collectAsState()
            val workdayVm = workdayViewModel()
            val workday by workdayVm.ui.collectAsState()
            val gpsNotice by workdayVm.gpsNotice.collectAsState()
            val otherAccount by workdayVm.otherAccount.collectAsState()
            LaunchedEffect(Unit) { workdayVm.open() }
            val context = LocalContext.current
            // Open the workday once the recycler answered the permission question (an answer of "no" opens it without GPS).
            val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                openWorkday(workdayVm, context, s.locationAccepted)
            }
            HomeScreen(
                s, ui.busy, ui.error, vm::logout, route, refreshing, routeVm::reload,
                workday = workday,
                gpsNotice = gpsNotice,
                otherAccount = otherAccount,
                onStartWorkday = {
                    if (s.locationAccepted && !LocationAvailability.permissionGranted(context)) {
                        permissions.launch(locationPermissions())
                    } else {
                        openWorkday(workdayVm, context, s.locationAccepted)
                    }
                },
                onCloseWorkday = workdayVm::close,
                onRetryWorkday = workdayVm::retry,
            )
        }
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
