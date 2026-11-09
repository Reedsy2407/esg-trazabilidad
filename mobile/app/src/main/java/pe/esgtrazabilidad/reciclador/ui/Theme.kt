package pe.esgtrazabilidad.reciclador.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The web tokens of the frontend (frontend/src/styles.css, brief in frontend/design/DESIGN-BRIEF.md) in Compose
 * terms, with the same values and the contrast measured there: ink on paper 15.6:1, paper on brand 8.0:1, brand
 * on paper 7.9:1, disabled ink on disabled background 4.67:1. Square corners (radius 0), body text 18 sp with a
 * 15 sp floor, controls 56 dp. State colours (vigente, por vencer, vencido) are not interface colours and are not
 * here: an error is a box with an ink border and words, as on the web (`.problem`).
 */
object Tokens {
    val Brand = Color(0xFF1A576C)
    val BrandDeep = Color(0xFF123F4F)
    val Ink = Color(0xFF1B2422)
    val InkSoft = Color(0xFF46524F)
    val Ground = Color(0xFFD6DBD9)
    val Paper = Color(0xFFFCFDFD)
    val Line = Color(0xFFAAB3B0)
    val LineInput = Color(0xFF46524F)
    val DisabledBg = Color(0xFFE4E8E6)
    val DisabledInk = Color(0xFF5D6866)

    val Control = 56.dp
    val Gutter = 16.dp
    val Space2 = 8.dp
    val Space3 = 12.dp
    val Space4 = 16.dp
    val Space6 = 24.dp
}

private val Colors: ColorScheme = lightColorScheme(
    primary = Tokens.Brand,
    onPrimary = Tokens.Paper,
    primaryContainer = Tokens.Paper,
    onPrimaryContainer = Tokens.Ink,
    background = Tokens.Ground,
    onBackground = Tokens.Ink,
    surface = Tokens.Paper,
    onSurface = Tokens.Ink,
    surfaceVariant = Tokens.Paper,
    onSurfaceVariant = Tokens.InkSoft,
    outline = Tokens.LineInput,
    outlineVariant = Tokens.Line,
    error = Tokens.Ink,
    onError = Tokens.Paper,
)

// The web voice is Archivo (OFL), shipped there as WOFF2, which Android cannot load; until the TTF files are
// added to res/font, the platform sans-serif stands in. Sizes follow the brief's scale.
private val Voice = FontFamily.SansSerif

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = Voice, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
    titleLarge = TextStyle(fontFamily = Voice, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = Voice, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Voice, fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = Voice, fontSize = 18.sp, lineHeight = 28.sp),
    bodySmall = TextStyle(fontFamily = Voice, fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontFamily = Voice, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
)

private val Square = RoundedCornerShape(0.dp)
private val Flat = Shapes(
    extraSmall = Square,
    small = Square,
    medium = Square,
    large = Square,
    extraLarge = Square,
)

@Composable
fun RecicladorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = Flat, content = content)
}
