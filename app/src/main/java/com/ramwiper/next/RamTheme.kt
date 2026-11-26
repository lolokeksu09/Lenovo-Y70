package com.ramwiper.next

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

@Composable fun RamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFA8C8F8), onPrimary = Color(0xFF102E50),
            background = Color.Black, onBackground = Color(0xFFF0F1F3),
            surface = Color(0xFF101214), onSurface = Color(0xFFF0F1F3),
            surfaceVariant = Color(0xFF20252B), onSurfaceVariant = Color(0xFFB7BDC5),
            outline = Color(0xFF7C838B), outlineVariant = Color(0xFF30363D),
            error = Color(0xFFFFB4AB)
        ),
        shapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(10.dp), large = RoundedCornerShape(14.dp), extraLarge = RoundedCornerShape(18.dp)),
        content = content
    )
}
