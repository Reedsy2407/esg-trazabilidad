package pe.esgtrazabilidad.reciclador.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.esgtrazabilidad.reciclador.net.Consent

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
        CodeField(
            value = code,
            onValueChange = { code = it.filter { c -> !c.isWhitespace() }.take(MAX_CODE_LENGTH) },
            enabled = !busy,
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

/**
 * The activation code field with the brief's control: a 2 dp rule in ink-soft (8.0:1 on paper) that becomes a 3 dp
 * brand rule on focus, 56 dp tall, the code in mono. Material's own field cannot draw that rule, so the container
 * is ours (a plain [BasicTextField] with its label above it, as on the web).
 */
@Composable
fun CodeField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
        Text("Código de activación", style = MaterialTheme.typography.titleMedium)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            interactionSource = interaction,
            textStyle = Mono.let { MaterialTheme.typography.bodyLarge.copy(fontFamily = it, fontSize = 22.sp, letterSpacing = 2.sp, color = Tokens.Ink) },
            cursorBrush = SolidColor(Tokens.Brand),
            // Password-type keyboard: no suggestions, no autocorrect, nothing for the keyboard to learn.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Tokens.Control)
                .background(if (enabled) Tokens.Paper else Tokens.DisabledBg)
                .border(if (focused) 3.dp else 2.dp, if (focused) Tokens.Brand else Tokens.LineInput)
                .padding(horizontal = Tokens.Space3)
                .testTag(Tags.CODE_FIELD),
            decorationBox = { inner -> Box(Modifier.heightIn(min = Tokens.Control - 4.dp), contentAlignment = Alignment.CenterStart) { inner() } },
        )
    }
}

private const val MIN_CODE_LENGTH = 6
private const val MAX_CODE_LENGTH = 32

/**
 * The consent in two parts. The general one is required to operate; the location is asked separately and may be
 * refused (then only what uses GPS is blocked, CA-14). The retention periods are figures in a panel of their own
 * (they are what the recycler most needs to read), and the accept button is pinned under the text, so a long consent
 * never hides it.
 */
@Composable
fun ConsentScreen(consent: Consent, busy: Boolean, error: String?, onAccept: (locationAccepted: Boolean) -> Unit, onCancel: () -> Unit) {
    var general by rememberSaveable { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(false) }
    Screen(
        footer = {
            error?.let { Problem(it) }
            PrimaryButton(
                text = if (busy) "Guardando…" else "Aceptar y continuar",
                enabled = general && !busy,
                onClick = { onAccept(location) },
                modifier = Modifier.testTag(Tags.CONSENT_ACCEPT),
            )
            SecondaryButton(text = "Cancelar", enabled = !busy, onClick = onCancel)
        },
    ) {
        PageTitle("Tus datos y tu consentimiento")
        Sheet { Text(consent.text, style = MaterialTheme.typography.bodyLarge) }
        // The jump of hierarchy: how long things are kept, in figures, apart from the body text.
        SectionTitle("Cuánto tiempo se guardan")
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space3)) {
            RetentionFigure("${consent.gpsDays}", "días", "Ubicación exacta del recorrido", Modifier.weight(1f))
            RetentionFigure("${consent.photoMonths}", "meses", "Fotos de evidencia", Modifier.weight(1f))
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
    }
}

@Composable
private fun RetentionFigure(number: String, unit: String, what: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(Tokens.Paper)
            .border(2.dp, Tokens.Ink)
            .padding(Tokens.Space3)
            // One sentence for a screen reader: the figure and its unit alone mean nothing.
            .clearAndSetSemantics { contentDescription = "$what: $number $unit." },
        verticalArrangement = Arrangement.spacedBy(Tokens.Space2),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space2)) {
            Text(number, style = Figure.copy(fontSize = 40.sp, lineHeight = 44.sp), color = Tokens.Ink)
            Text(unit, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(what, style = MaterialTheme.typography.bodySmall)
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
