package pe.esgtrazabilidad.reciclador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import pe.esgtrazabilidad.reciclador.field.WorkdayStatus
import pe.esgtrazabilidad.reciclador.field.WorkdayUi
import pe.esgtrazabilidad.reciclador.field.WorkdayView
import pe.esgtrazabilidad.reciclador.net.RouteStop
import pe.esgtrazabilidad.reciclador.route.RouteState
import pe.esgtrazabilidad.reciclador.session.SessionState

object HomeTags {
    const val TAB_WORKDAY = "tab-workday"
    const val TAB_ROUTE = "tab-route"
    const val TAB_ACCOUNT = "tab-account"
    const val WORKDAY_STATE = "workday-state"
    const val WORKDAY_START = "workday-start"
    const val WORKDAY_CLOSE = "workday-close"
    const val WORKDAY_RETRY = "workday-retry"
    const val NO_GPS = "workday-no-gps"
    const val ROUTE_STATE = "route-state"
    const val ROUTE_STOP = "route-stop"
    const val ROUTE_RELOAD = "route-reload"
}

enum class HomeTab(val label: String, val tag: String) {
    WORKDAY("Jornada", HomeTags.TAB_WORKDAY),
    ROUTE("Ruta", HomeTags.TAB_ROUTE),
    ACCOUNT("Cuenta", HomeTags.TAB_ACCOUNT),
}

private val LIMA = ZoneId.of("America/Lima")
private val LOCALE = Locale.forLanguageTag("es-PE")
private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", LOCALE)
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", LOCALE)

fun formatDownloadTime(millis: Long): String = DATE_TIME.format(Instant.ofEpochMilli(millis).atZone(LIMA))

private fun clock(millis: Long) = CLOCK.format(Instant.ofEpochMilli(millis).atZone(LIMA))

private val DAYS = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
private val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

fun dayName(code: String): String = DAYS.indexOf(code).let { if (it < 0) code else DAY_NAMES[it] }

private fun points(n: Int) = if (n == 1) "1 punto" else "$n puntos"

/**
 * The signed-in app: three destinations in a bar pinned to the bottom (each 56 dp, with the selected one filled).
 * The state of the session, of the route and of the workday arrive as plain values, so every state is drawable.
 */
@Composable
fun HomeScreen(
    state: SessionState.Active,
    busy: Boolean,
    error: String?,
    onLogout: () -> Unit,
    route: RouteState = RouteState.Loading,
    routeRefreshing: Boolean = false,
    onReloadRoute: () -> Unit = {},
    workday: WorkdayUi = WorkdayUi(),
    gpsNotice: String? = null,
    otherAccount: Boolean = false,
    onStartWorkday: () -> Unit = {},
    onCloseWorkday: () -> Unit = {},
    onRetryWorkday: () -> Unit = {},
    initialTab: HomeTab = HomeTab.WORKDAY,
) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    Screen(footer = { TabBar(tab) { tab = it } }) {
        when (tab) {
            HomeTab.WORKDAY -> WorkdayPage(state, workday, gpsNotice, otherAccount, onStartWorkday, onCloseWorkday, onRetryWorkday)
            HomeTab.ROUTE -> RoutePage(route, routeRefreshing, onReloadRoute)
            HomeTab.ACCOUNT -> AccountPage(state, busy, error, onLogout)
        }
    }
}

@Composable
private fun TabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    Row(Modifier.fillMaxWidth().border(2.dp, Tokens.Ink).selectableGroup()) {
        HomeTab.entries.forEach { tab ->
            val on = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Tokens.Control)
                    .background(if (on) Tokens.Brand else Tokens.Paper)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                    .testTag(tab.tag),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    tab.label,
                    color = if (on) Tokens.Paper else Tokens.Ink,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---- the workday --------------------------------------------------------------------------------

@Composable
private fun WorkdayPage(
    state: SessionState.Active,
    ui: WorkdayUi,
    gpsNotice: String?,
    otherAccount: Boolean,
    onStart: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
) {
    PageTitle("Jornada")
    ui.notice?.let { Problem(it) }
    val workday = ui.workday
    if (workday == null) {
        if (otherAccount) {
            Problem("Hay una jornada sin enviar de otra cuenta en este teléfono. Actívala con el código de esa cuenta para enviarla.")
        }
        Sheet {
            Text("No tienes una jornada abierta.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(HomeTags.WORKDAY_STATE))
            Text(
                if (state.locationAccepted) "Al abrirla se registrará tu recorrido con el GPS, aunque la app quede en segundo plano."
                else "No aceptaste la ubicación: la jornada se abrirá sin GPS y no se registrará tu recorrido.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        PrimaryButton(text = "Abrir jornada", enabled = !otherAccount, onClick = onStart, modifier = Modifier.testTag(HomeTags.WORKDAY_START))
        return
    }
    val open = workday.status == WorkdayStatus.OPEN
    Visor(
        label = if (open) "Jornada abierta" else "Jornada cerrada",
        figure = clock(if (open) workday.startedAtMillis else workday.endedAtMillis ?: workday.startedAtMillis),
        line = if (open) "Desde las ${clock(workday.startedAtMillis)}" else "Falta confirmarla con el servidor",
        modifier = Modifier.testTag(HomeTags.WORKDAY_STATE),
    )
    GpsSheet(workday, gpsNotice)
    if (open) {
        SecondaryButton(text = "Cerrar jornada", onClick = onClose, modifier = Modifier.testTag(HomeTags.WORKDAY_CLOSE))
    } else {
        PrimaryButton(
            text = if (ui.syncing) "Enviando…" else "Reintentar envío",
            enabled = !ui.syncing,
            onClick = onRetry,
            modifier = Modifier.testTag(HomeTags.WORKDAY_RETRY),
        )
    }
}

@Composable
private fun GpsSheet(workday: WorkdayView, gpsNotice: String?) {
    Sheet {
        if (workday.recordingGps) {
            SectionTitle("Recorrido con GPS")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
                Text("${workday.pendingPoints}", style = Figure.copy(fontSize = 36.sp, lineHeight = 40.sp), color = Tokens.Ink)
                Text(
                    if (workday.pendingPoints == 1) "punto por enviar" else "puntos por enviar",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(
                if (workday.pendingPoints == 0) "Todo lo registrado ya llegó al servidor." else "Se envían solos; si no hay red, quedan guardados en el teléfono.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            SectionTitle("Sin GPS")
            Text(
                gpsNotice ?: "Esta jornada no registra tu recorrido.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(HomeTags.NO_GPS),
            )
        }
        if (workday.status == WorkdayStatus.CLOSED && workday.pendingPoints > 0) {
            Text("Está guardada en el teléfono con ${points(workday.pendingPoints)} por enviar.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ---- the route ----------------------------------------------------------------------------------

@Composable
private fun RoutePage(state: RouteState, refreshing: Boolean, onReload: () -> Unit) {
    PageTitle("Tu ruta")
    when (state) {
        RouteState.Loading -> Sheet {
            Text("Descargando tu ruta…", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(HomeTags.ROUTE_STATE))
        }
        is RouteState.Empty -> {
            Sheet {
                Text("Todavía no tienes cronogramas asignados.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag(HomeTags.ROUTE_STATE))
                Text("Pide al personal que te asigne tu ruta y vuelve a actualizar.", style = MaterialTheme.typography.bodyLarge)
                DownloadedAt(state.fetchedAtMillis)
            }
        }
        is RouteState.Unavailable -> Problem(
            if (state.expired) "Tu ruta guardada tenía 7 días y se borró. Conéctate para descargarla de nuevo."
            else "No hay una ruta guardada. ${state.reason}",
        )
        is RouteState.Ready -> {
            if (state.stale) {
                Problem("${state.reason.orEmpty()} Muestro la ruta guardada.")
            }
            Visor(
                label = "Ruta guardada",
                figure = "${state.stops.size}",
                line = if (state.stops.size == 1) "recojo asignado" else "recojos asignados",
                modifier = Modifier.testTag(HomeTags.ROUTE_STATE),
            )
            DownloadedAt(state.fetchedAtMillis)
            StopList(state.stops)
        }
    }
    SecondaryButton(
        text = if (refreshing) "Descargando…" else "Actualizar ruta",
        enabled = !refreshing,
        onClick = onReload,
        modifier = Modifier.testTag(HomeTags.ROUTE_RELOAD),
    )
}

@Composable
private fun DownloadedAt(millis: Long) {
    Text("Descargada el ${formatDownloadTime(millis)}", style = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono))
}

/** One continuous roll of paper, grouped by day; time in mono at the left, the neighbour at the right. */
@Composable
private fun StopList(stops: List<RouteStop>) {
    val sorted = stops.sortedWith(compareBy({ DAYS.indexOf(it.dayOfWeek).let { i -> if (i < 0) DAYS.size else i } }, { it.time }, { it.neighbor.fullName }))
    Column(Modifier.fillMaxWidth().background(Tokens.Paper).border(1.dp, Tokens.Line)) {
        var lastDay: String? = null
        sorted.forEach { stop ->
            if (stop.dayOfWeek != lastDay) {
                lastDay = stop.dayOfWeek
                Text(
                    dayName(stop.dayOfWeek).uppercase(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Tokens.Paper,
                    modifier = Modifier.fillMaxWidth().background(Tokens.InkSoft).padding(horizontal = Tokens.Space3, vertical = Tokens.Space2),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(Tokens.Space3).testTag(HomeTags.ROUTE_STOP),
                horizontalArrangement = Arrangement.spacedBy(Tokens.Space3),
            ) {
                Text(stop.time, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontWeight = FontWeight.SemiBold))
                Column(Modifier.weight(1f)) {
                    Text(stop.neighbor.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("${stop.neighbor.address} · ${stop.neighbor.district}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---- the account --------------------------------------------------------------------------------

@Composable
private fun AccountPage(state: SessionState.Active, busy: Boolean, error: String?, onLogout: () -> Unit) {
    PageTitle("Cuenta")
    if (state.offline) {
        Problem("Sin conexión: la sesión sigue guardada y se renovará cuando vuelva la red.")
    }
    Sheet {
        Text("Tu teléfono está activado.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(Tags.HOME_TITLE))
        Text(
            if (state.locationAccepted) "Ubicación: aceptada." else "Ubicación: no aceptada (las jornadas se abren sin GPS).",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
    error?.let { Problem(it) }
    SecondaryButton(text = if (busy) "Cerrando…" else "Cerrar sesión", enabled = !busy, onClick = onLogout, modifier = Modifier.testTag(Tags.LOGOUT))
}
