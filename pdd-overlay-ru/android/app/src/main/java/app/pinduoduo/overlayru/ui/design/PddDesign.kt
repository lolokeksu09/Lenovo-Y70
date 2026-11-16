package app.pinduoduo.overlayru.ui.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object PddPalette {
    val Background = Color(0xFF06080D)
    val Surface = Color(0xFF141820)
    val Elevated = Color(0xFF202632)
    val Outline = Color(0xFF343B48)
    val Text = Color(0xFFF6F7FA)
    val Secondary = Color(0xFFB7C0CE)
    val Accent = Color(0xFFF03B53)
    val AccentDark = Color(0xFF8C172A)
    val Success = Color(0xFF65D7A4)
    val Warning = Color(0xFFFFC46B)
    val Error = Color(0xFFFF8A9A)
}

private val PddColors = darkColorScheme(
    primary = PddPalette.Accent,
    onPrimary = Color.White,
    primaryContainer = PddPalette.AccentDark,
    onPrimaryContainer = PddPalette.Text,
    background = PddPalette.Background,
    onBackground = PddPalette.Text,
    surface = PddPalette.Surface,
    onSurface = PddPalette.Text,
    surfaceVariant = PddPalette.Elevated,
    onSurfaceVariant = PddPalette.Secondary,
    outline = PddPalette.Outline,
    error = PddPalette.Error
)

@Composable
internal fun PddTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PddColors, typography = Typography(), content = content)
}
