package app.pinduoduo.overlayru.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Single cohesive visual language for PDD Overlay RU:
 * Chinese-shopping utility first, premium through clarity, type and rhythm.
 *
 * High-contrast AMOLED neutrals; the product red identifies primary action,
 * not every decorative border/heading.
 */
internal object PddPalette {
    val Background = Color(0xFF080A0E)
    val Surface = Color(0xFF151A21)
    val Elevated = Color(0xFF212630)
    val Outline = Color(0xFF343B46)
    val Text = Color(0xFFF6F7FA)
    val Secondary = Color(0xFFB5BDC9)
    val Accent = Color(0xFFEB4055)
    val Success = Color(0xFF66DDB8)
    val Warning = Color(0xFFF4C376)
}

private val Dark = darkColorScheme(
    primary = PddPalette.Accent,
    onPrimary = Color.White,
    background = PddPalette.Background,
    onBackground = PddPalette.Text,
    surface = PddPalette.Surface,
    onSurface = PddPalette.Text,
    onSurfaceVariant = PddPalette.Secondary,
    surfaceVariant = PddPalette.Elevated,
    outline = PddPalette.Outline
)

@Composable
internal fun PddTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, content = content)
}
