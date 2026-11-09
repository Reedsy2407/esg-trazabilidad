package pe.esgtrazabilidad.reciclador.ui

import pe.esgtrazabilidad.reciclador.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The product's top bar, as on the web: the official symbol (white T with the folded corner and the leaf, on the
 * brand square) next to the name, over the deep petrol band. The brief requires the logo at sign-in and start-up
 * screens; the name beside it makes the symbol decorative for a screen reader, so it has no description.
 */
@Composable
fun BrandBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.BrandDeep)
            .padding(horizontal = Tokens.Gutter, vertical = Tokens.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(48.dp).background(Tokens.Brand),
        )
        Text("Trazabilidad ESG", color = Tokens.Paper, style = MaterialTheme.typography.titleMedium)
    }
}

/** The page: the brand bar, then ground background, scrollable, one gutter all around. */
@Composable
fun Screen(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandBar()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Tokens.Gutter),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space4),
        ) {
            content()
        }
    }
}

@Composable
fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** A flat sheet on paper with a hard border, like the web `sheet-flat`. */
@Composable
fun Sheet(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.Paper)
            .border(BorderStroke(1.dp, Tokens.Line))
            .padding(Tokens.Space4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        content()
    }
}

/** An error or notice read out as soon as it appears; text carries the meaning, not only the colour. */
@Composable
fun Problem(text: String) {
    // Ink box with a drawn exclamation mark, like the web `.problem`: the meaning is in the words and the sign.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tokens.Paper)
            .border(BorderStroke(2.dp, Tokens.Ink))
            .padding(Tokens.Space3)
            .semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        Box(modifier = Modifier.size(24.dp).background(Tokens.Ink), contentAlignment = Alignment.Center) {
            Text("!", color = Tokens.Paper, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
        Text(text = text, color = Tokens.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        // Disabled looks disabled without relying on colour alone: a muted rule and muted text, like the web.
        border = BorderStroke(2.dp, if (enabled) Tokens.Brand else Tokens.Line),
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Control),
        colors = ButtonDefaults.buttonColors(
            containerColor = Tokens.Brand,
            contentColor = Tokens.Paper,
            disabledContainerColor = Tokens.DisabledBg,
            disabledContentColor = Tokens.DisabledInk,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Control),
        border = BorderStroke(2.dp, if (enabled) Tokens.Ink else Tokens.DisabledInk),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Tokens.Ink, disabledContentColor = Tokens.DisabledInk),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A checkbox whose whole row is the target (at least 56 dp tall) and which a screen reader reads as one control. */
@Composable
fun CheckRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Tokens.Control)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Tokens.Space3),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
