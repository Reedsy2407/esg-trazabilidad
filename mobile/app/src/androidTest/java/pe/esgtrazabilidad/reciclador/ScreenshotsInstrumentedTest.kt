package pe.esgtrazabilidad.reciclador

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import pe.esgtrazabilidad.reciclador.field.WorkdayStatus
import pe.esgtrazabilidad.reciclador.field.WorkdayUi
import pe.esgtrazabilidad.reciclador.field.WorkdayView
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.net.RouteNeighbor
import pe.esgtrazabilidad.reciclador.net.RouteStop
import pe.esgtrazabilidad.reciclador.route.RouteState
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.ui.CodeScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentUnavailableScreen
import pe.esgtrazabilidad.reciclador.ui.HomeScreen
import pe.esgtrazabilidad.reciclador.ui.HomeTab
import pe.esgtrazabilidad.reciclador.ui.RecicladorTheme
import pe.esgtrazabilidad.reciclador.ui.RevokedScreen

/**
 * Renders each screen at the width given by `-e ancho` (360 or 411 dp) and saves a PNG for the visual review. Only with `-e capturas 1`; it
 * asserts nothing. Files go to the app's media folder (`adb pull /sdcard/Android/media/pe.esgtrazabilidad.reciclador/capturas`).
 */
class ScreenshotsInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val enabled = InstrumentationRegistry.getArguments().getString("capturas") == "1"

    // setContent can be called once per test: the suite runs once per width (`-e ancho 360`, `-e ancho 411`).
    private val width = InstrumentationRegistry.getArguments().getString("ancho")?.toIntOrNull() ?: 360

    private fun capture(name: String, content: @Composable () -> Unit) {
        assumeTrue("run with -e capturas 1", enabled)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.externalMediaDirs.first(), "capturas").apply { mkdirs() }
        compose.setContent { RecicladorTheme { Box(Modifier.size(width.dp, 760.dp)) { content() } } }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name-${width}dp.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val consent = Consent(
        3,
        "Autorizo a mi asociación y a la plataforma de trazabilidad ESG a tratar mis datos personales con la única finalidad de registrar y verificar mi trabajo de recojo, conforme a la Ley N.º 29733. Puedo pedir que se corrijan o se borren mis datos, y sé que el personal de mi asociación puede ver mis jornadas y mis recojos.",
        "Si aceptas, la app guarda tu recorrido mientras dura la jornada, para verificar que el recojo ocurrió.",
        90,
        12,
    )

    private val now = 1_800_000_000_000L
    private val active = SessionState.Active(locationAccepted = true)

    private fun stop(day: String, time: String, name: String, address: String, district: String, n: Int) =
        RouteStop("c-$n", day, time, RouteNeighbor("v-$n", name, address, district))

    private val stops = listOf(
        stop("FRIDAY", "07:30", "Rosa Quispe Huamán", "Jr. Los Pinos 214", "Surquillo", 1),
        stop("MONDAY", "08:00", "Carlos Mendoza Ríos", "Av. Angamos Este 1820", "Surquillo", 2),
        stop("MONDAY", "08:40", "Lucía Paredes Soto", "Calle Las Orquídeas 45, int. 3", "San Borja", 3),
        stop("MONDAY", "09:15", "Mercado Santa Rosa (puesto 12)", "Jr. Tarata 330", "Miraflores", 4),
        stop("WEDNESDAY", "16:00", "Edgar Salas Vega", "Pasaje Los Cedros 8", "Surquillo", 5),
    )

    private fun workday(status: WorkdayStatus = WorkdayStatus.OPEN, gps: Boolean = true, pending: Int = 37, confirmed: Boolean = true) =
        WorkdayView(now - 3 * 3600_000, if (status == WorkdayStatus.CLOSED) now else null, status, gps, pending, confirmed)

    @Composable
    private fun Home(
        tab: HomeTab,
        state: SessionState.Active = active,
        route: RouteState = RouteState.Loading,
        workday: WorkdayUi = WorkdayUi(),
        gpsNotice: String? = null,
        refreshing: Boolean = false,
    ) = HomeScreen(
        state, busy = false, error = null, onLogout = {}, route = route, routeRefreshing = refreshing,
        workday = workday, gpsNotice = gpsNotice, initialTab = tab,
    )

    // ---- module 1 screens, now with the mobile profile ----
    @Test fun codigo() = capture("01-codigo") { CodeScreen(notice = null, busy = false, error = null, onSubmit = {}) }

    @Test fun codigoConError() = capture("02-codigo-con-error") {
        CodeScreen(notice = "La sesión terminó. Pide un código nuevo al personal.", busy = false, error = "El código no es válido o venció. Pide uno nuevo al personal.", onSubmit = {}, onBack = {})
    }

    @Test fun consentimiento() = capture("03-consentimiento") { ConsentScreen(consent, busy = false, error = null, onAccept = {}, onCancel = {}) }

    @Test fun consentimientoNoDisponible() = capture("04-consentimiento-no-disponible") {
        ConsentUnavailableScreen(busy = false, error = "No hay conexión con el servidor. Revisa tu red y vuelve a intentarlo.", onRetry = {}, onCancel = {})
    }

    @Test fun revocada() = capture("05-revocada") { RevokedScreen(count = 3, error = null, onNewCode = {}, onDiscard = {}) }

    // ---- workday ----
    @Test fun jornadaSinJornada() = capture("10-jornada-sin-jornada") { Home(HomeTab.WORKDAY) }

    @Test fun jornadaSinJornadaSinUbicacion() = capture("11-jornada-sin-jornada-ubicacion-rechazada") {
        Home(HomeTab.WORKDAY, state = SessionState.Active(locationAccepted = false))
    }

    @Test fun jornadaAbiertaConGps() = capture("12-jornada-abierta-gps") { Home(HomeTab.WORKDAY, workday = WorkdayUi(workday())) }

    @Test fun jornadaAbiertaSinUbicacion() = capture("13-jornada-abierta-sin-ubicacion") {
        Home(
            HomeTab.WORKDAY,
            state = SessionState.Active(locationAccepted = false),
            workday = WorkdayUi(workday(gps = false, pending = 0)),
            gpsNotice = "No aceptaste la ubicación en el consentimiento: esta jornada se abre sin GPS.",
        )
    }

    @Test fun jornadaSinRed() = capture("14-jornada-sin-red") {
        Home(
            HomeTab.WORKDAY,
            state = SessionState.Active(offline = true, locationAccepted = true),
            workday = WorkdayUi(workday(pending = 212), notice = "Sin conexión: tu jornada y tus puntos están guardados en el teléfono y se enviarán cuando vuelva la red."),
        )
    }

    @Test fun jornadaCerradaPorConfirmar() = capture("15-jornada-cerrada-por-confirmar") {
        Home(HomeTab.WORKDAY, workday = WorkdayUi(workday(WorkdayStatus.CLOSED, pending = 12), notice = "No se pudo enviar ahora. Está guardado en el teléfono y se reintentará."))
    }

    // ---- route ----
    @Test fun rutaCargando() = capture("20-ruta-cargando") { Home(HomeTab.ROUTE, route = RouteState.Loading, refreshing = true) }

    @Test fun rutaConDatos() = capture("21-ruta-con-datos") { Home(HomeTab.ROUTE, route = RouteState.Ready(stops, now)) }

    @Test fun rutaGuardadaSinRed() = capture("22-ruta-guardada-sin-red") {
        Home(HomeTab.ROUTE, route = RouteState.Ready(stops, now - 2 * 86_400_000, stale = true, reason = "No hay conexión con el servidor."))
    }

    @Test fun rutaVacia() = capture("23-ruta-vacia") { Home(HomeTab.ROUTE, route = RouteState.Empty(now)) }

    @Test fun rutaError() = capture("24-ruta-error") {
        Home(HomeTab.ROUTE, route = RouteState.Unavailable(expired = false, reason = "No hay conexión con el servidor."))
    }

    @Test fun rutaCaducada() = capture("25-ruta-caducada") { Home(HomeTab.ROUTE, route = RouteState.Unavailable(expired = true, reason = "No hay conexión con el servidor.")) }

    // ---- account ----
    @Test fun cuenta() = capture("30-cuenta") { Home(HomeTab.ACCOUNT, state = SessionState.Active(offline = true, locationAccepted = true)) }
}
