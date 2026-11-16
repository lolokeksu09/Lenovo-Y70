package app.pinduoduo.overlayru.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lightweight original vector-like shopping-bag motif; no bitmap screenshots. */
@Composable
internal fun PddBagArt(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(124.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(124.dp)) {
            val center = Offset(size.width * .58f,size.height * .55f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(PddPalette.Accent.copy(alpha = .27f), Color.Transparent),
                    center = center,
                    radius = size.width * .62f
                ),
                radius = size.width * .61f, center = center
            )
        }
        Box(
            modifier = Modifier.size(97.dp).graphicsLayer { rotationZ = -8f },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(97.dp)) {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    brush=Brush.linearGradient(
                        listOf(Color(0xFFFF6574),Color(0xFFD41B36),Color(0xFF720D23)),
                        start=Offset(w*.15f,h*.12f),end=Offset(w*.95f,h)
                    ),
                    topLeft=Offset(w*.08f,h*.28f),
                    size=Size(w*.80f,h*.66f),
                    cornerRadius=CornerRadius(12.dp.toPx())
                )
                drawRoundRect(
                    color=Color.White.copy(alpha=.42f),
                    topLeft=Offset(w*.10f,h*.30f),
                    size=Size(w*.76f,h*.61f),
                    cornerRadius=CornerRadius(12.dp.toPx()),
                    style=Stroke(1.6.dp.toPx())
                )
                val handle = Path().apply {
                    moveTo(w*.30f,h*.36f)
                    cubicTo(w*.29f,h*.07f,w*.70f,h*.07f,w*.68f,h*.36f)
                }
                drawPath(
                    path=handle,color=Color(0xFFFFDAE0),
                    style=Stroke(width=4.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawPath(
                    path=handle,color=Color(0xFFFF4D63),
                    style=Stroke(width=2.dp.toPx(),cap=androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
            Text(
                "拼多多",
                fontSize=12.sp,
                fontWeight=FontWeight.Black,
                color=Color.White,
                modifier=Modifier.graphicsLayer { translationY = 15.dp.toPx() }
            )
        }
    }
}
