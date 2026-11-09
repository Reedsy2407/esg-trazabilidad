package pe.esgtrazabilidad.reciclador

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.session.SessionState
import pe.esgtrazabilidad.reciclador.ui.CodeScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentUnavailableScreen
import pe.esgtrazabilidad.reciclador.ui.HomeScreen
import pe.esgtrazabilidad.reciclador.ui.RecicladorTheme
import pe.esgtrazabilidad.reciclador.ui.RevokedScreen

/**
 * Renders each screen and saves a PNG for the visual review. Only with `-e capturas 1`; it asserts nothing.
 * Files go to the app's media folder (`adb pull /sdcard/Android/media/pe.esgtrazabilidad.reciclador/capturas`).
 */
class ScreenshotsInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val enabled = InstrumentationRegistry.getArguments().getString("capturas") == "1"

    private fun capture(name: String, content: @Composable () -> Unit) {
        assumeTrue("run with -e capturas 1", enabled)
        compose.setContent { RecicladorTheme { content() } }
        compose.waitForIdle()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.externalMediaDirs.first(), "capturas").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val consent = Consent(
        3,
        "Autorizo a mi asociación y a la plataforma de trazabilidad ESG a tratar mis datos personales con la única finalidad de registrar y verificar mi trabajo de recojo, conforme a la Ley N.º 29733.",
        "Si aceptas, la app guarda tu recorrido mientras dura la jornada, para verificar que el recojo ocurrió.",
        90,
        12,
    )

    @Test fun codigo() = capture("1-codigo") { CodeScreen(notice = null, busy = false, error = null, onSubmit = {}) }

    @Test fun codigoConError() = capture("2-codigo-con-error") {
        CodeScreen(notice = "La sesión terminó. Pide un código nuevo al personal.", busy = false, error = "El código no es válido o venció. Pide uno nuevo al personal.", onSubmit = {}, onBack = {})
    }

    @Test fun consentimiento() = capture("3-consentimiento") { ConsentScreen(consent, busy = false, error = null, onAccept = {}, onCancel = {}) }

    @Test fun consentimientoNoDisponible() = capture("4-consentimiento-no-disponible") {
        ConsentUnavailableScreen(busy = false, error = "No hay conexión con el servidor. Revisa tu red y vuelve a intentarlo.", onRetry = {}, onCancel = {})
    }

    @Test fun sesionActiva() = capture("5-sesion-activa") {
        HomeScreen(SessionState.Active(offline = true, locationAccepted = true), busy = false, error = null, onLogout = {})
    }

    @Test fun revocada() = capture("6-revocada") { RevokedScreen(count = 3, error = null, onNewCode = {}, onDiscard = {}) }
}
