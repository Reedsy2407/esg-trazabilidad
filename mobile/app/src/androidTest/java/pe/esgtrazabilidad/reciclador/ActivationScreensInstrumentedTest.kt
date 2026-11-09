package pe.esgtrazabilidad.reciclador

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.ui.CodeScreen
import pe.esgtrazabilidad.reciclador.ui.ConsentScreen
import pe.esgtrazabilidad.reciclador.ui.RecicladorTheme
import pe.esgtrazabilidad.reciclador.ui.Tags

/** CA-16 (the code screen), CA-14 on the phone (the location is a separate choice with the periods shown). */
class ActivationScreensInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val consent = Consent(3, "Texto general del consentimiento", "Texto aparte de la ubicación", 60, 6)

    @Test
    fun activateStaysDisabledUntilTheCodeIsLongEnoughAndSendsWhatWasTyped() {
        var sent: String? = null
        compose.setContent { RecicladorTheme { CodeScreen(notice = null, busy = false, error = null, onSubmit = { sent = it }) } }

        compose.onNodeWithText("Activar").assertIsNotEnabled()
        compose.onNodeWithTag(Tags.CODE_FIELD).performTextInput("ABC")
        compose.onNodeWithText("Activar").assertIsNotEnabled()
        compose.onNodeWithTag(Tags.CODE_FIELD).performTextInput("DEF GH")
        compose.onNodeWithText("Activar").assertIsEnabled().performClick()

        assertEquals("spaces are not part of the code", "ABCDEFGH", sent)
    }

    @Test
    fun anErrorAndANoticeAreShownAsText() {
        compose.setContent {
            RecicladorTheme { CodeScreen(notice = "La sesión terminó.", busy = false, error = "El código no es válido o venció.", onSubmit = {}) }
        }
        compose.onNodeWithText("La sesión terminó.").assertExists()
        compose.onNodeWithText("El código no es válido o venció.").assertExists()
    }

    @Test
    fun theConsentShowsThePeriodsAndNeedsTheGeneralAnswerButNotTheLocation() {
        var answer: Boolean? = null
        compose.setContent { RecicladorTheme { ConsentScreen(consent, busy = false, error = null, onAccept = { answer = it }, onCancel = {}) } }

        compose.onNodeWithText("Ubicación exacta del recorrido: 60 días.").assertExists()
        compose.onNodeWithText("Fotos de evidencia: 6 meses.").assertExists()
        compose.onNodeWithText("Texto aparte de la ubicación").assertExists()
        compose.onNodeWithText("Aceptar y continuar").assertIsNotEnabled()

        compose.onNodeWithText("He leído y acepto el tratamiento de mis datos").performClick()
        compose.onNodeWithText("Aceptar y continuar").assertIsEnabled().performClick()

        assertEquals("refusing the location is allowed", false, answer)
    }

    @Test
    fun acceptingTheLocationIsASeparateChoice() {
        var answer: Boolean? = null
        compose.setContent { RecicladorTheme { ConsentScreen(consent, busy = false, error = null, onAccept = { answer = it }, onCancel = {}) } }

        compose.onNodeWithText("He leído y acepto el tratamiento de mis datos").performClick()
        compose.onNodeWithText("Acepto que se use mi ubicación durante la jornada").performClick()
        compose.onNodeWithText("Aceptar y continuar").performClick()

        assertEquals(true, answer)
    }
}
