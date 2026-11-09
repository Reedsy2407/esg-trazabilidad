package pe.esgtrazabilidad.reciclador.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import pe.esgtrazabilidad.reciclador.net.Consent
import pe.esgtrazabilidad.reciclador.session.SessionState

object Tags {
    const val CODE_FIELD = "code-field"
    const val ACTIVATE = "activate"
    const val CONSENT_GENERAL = "consent-general"
    const val CONSENT_LOCATION = "consent-location"
    const val CONSENT_ACCEPT = "consent-accept"
    const val LOGOUT = "logout"
    const val HOME_TITLE = "home-title"
}

/** The code the staff handed over in person: 10 characters, typed once per phone. */
@Composable
fun CodeScreen(notice: String?, busy: Boolean, error: String?, onSubmit: (String) -> Unit, onBack: (() -> Unit)? = null) {
    // Not saved with the instance state: the code is single-use data and should not outlive the screen.
    var code by remember { mutableStateOf("") }
    Screen {
        PageTitle("Activar la app")
        Text(
            "Escribe el código de 10 caracteres que te dio el personal. Sirve una sola vez y vence a las 72 horas.",
            style = MaterialTheme.typography.bodyLarge,
        )
        notice?.let { Problem(it) }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter { c -> !c.isWhitespace() }.take(MAX_CODE_LENGTH) },
            label = { Text("Código de activación") },
            singleLine = true,
            enabled = !busy,
            // Password-type keyboard: no suggestions, no autocorrect, nothing for the keyboard to learn.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Tokens.Brand,
                unfocusedBorderColor = Tokens.LineInput,
                focusedContainerColor = Tokens.Paper,
                unfocusedContainerColor = Tokens.Paper,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Control).testTag(Tags.CODE_FIELD),
        )
        error?.let { Problem(it) }
        PrimaryButton(
            text = if (busy) "Activando…" else "Activar",
            enabled = !busy && code.length >= MIN_CODE_LENGTH,
            onClick = { onSubmit(code) },
        )
        onBack?.let { SecondaryButton(text = "Volver", enabled = !busy, onClick = it) }
    }
}

private const val MIN_CODE_LENGTH = 6
private const val MAX_CODE_LENGTH = 32

/**
 * The consent in two parts. The general one is required to operate; the location is asked separately and may be
 * refused (then only what uses GPS is blocked, CA-14). The retention periods are shown as numbers, apart from the text.
 */
@Composable
fun ConsentScreen(consent: Consent, busy: Boolean, error: String?, onAccept: (locationAccepted: Boolean) -> Unit, onCancel: () -> Unit) {
    var general by rememberSaveable { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(false) }
    Screen {
        PageTitle("Tus datos y tu consentimiento")
        Sheet {
            Text(consent.text, style = MaterialTheme.typography.bodyLarge)
            SectionTitle("Cuánto tiempo se guardan")
            Text("Ubicación exacta del recorrido: ${consent.gpsDays} días.", style = MaterialTheme.typography.bodyLarge)
            Text("Fotos de evidencia: ${consent.photoMonths} meses.", style = MaterialTheme.typography.bodyLarge)
        }
        CheckRow(general, "He leído y acepto el tratamiento de mis datos", { general = it })
        Sheet {
            SectionTitle("Ubicación (aparte)")
            Text(consent.locationText, style = MaterialTheme.typography.bodyLarge)
        }
        CheckRow(location, "Acepto que se use mi ubicación durante la jornada", { location = it })
        Text(
            "Si no aceptas la ubicación, podrás usar el resto de la app, pero no abrir una jornada con GPS.",
            style = MaterialTheme.typography.bodySmall,
        )
        error?.let { Problem(it) }
        PrimaryButton(
            text = if (busy) "Guardando…" else "Aceptar y continuar",
            enabled = general && !busy,
            onClick = { onAccept(location) },
        )
        SecondaryButton(text = "Cancelar", enabled = !busy, onClick = onCancel)
    }
}

/** The phone is activated but the consent text could not be fetched: the code is spent, so the way out is to retry. */
@Composable
fun ConsentUnavailableScreen(busy: Boolean, error: String?, onRetry: () -> Unit, onCancel: () -> Unit) {
    Screen {
        PageTitle("No se pudo cargar el consentimiento")
        Text(
            "Tu código ya se usó y el teléfono quedó activado. Falta leer y aceptar el consentimiento: comprueba tu conexión y vuelve a intentarlo.",
            style = MaterialTheme.typography.bodyLarge,
        )
        error?.let { Problem(it) }
        PrimaryButton(if (busy) "Cargando…" else "Reintentar", enabled = !busy, onClick = onRetry)
        SecondaryButton("Cancelar", enabled = !busy, onClick = onCancel)
    }
}

@Composable
fun HomeScreen(state: SessionState.Active, busy: Boolean, error: String?, onLogout: () -> Unit) {
    Screen {
        PageTitle("Sesión activa")
        if (state.offline) {
            Problem("Sin conexión: la sesión sigue guardada y se renovará cuando vuelva la red.")
        }
        Sheet {
            Text("Tu teléfono está activado.", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (state.locationAccepted) "Ubicación: aceptada." else "Ubicación: no aceptada (no podrás abrir una jornada con GPS).",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Aquí aparecerá tu jornada de recojo cuando esté disponible.", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Problem(it) }
        SecondaryButton(text = if (busy) "Cerrando…" else "Cerrar sesión", enabled = !busy, onClick = onLogout)
    }
}
