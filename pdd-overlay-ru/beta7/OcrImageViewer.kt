package app.pinduoduo.overlayru

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val ViewerBg = Color(0xFF080A0E)
private val ViewerSurface = Color(0xFF15191F)
private val ViewerInk = Color(0xFFF5F6F9)
private val ViewerMuted = Color(0xFFB3BDCA)
private val ViewerRed = Color(0xFFEA3A50)
private val ViewerGreen = Color(0xFF69E0C0)

/**
 * Opt-in image viewer. Never modifies pixels or hides purchase amounts.
 * Highlights source text boxes and shows Russian text only after user tap.
 */
@Composable
internal fun OcrImageViewer(
    preview: Bitmap,
    lines: List<Pair<String, String?>>,
    regions: List<OcrRegion?>,
    onDismiss: () -> Unit
) {
    var selected by remember(preview, lines, regions) { mutableIntStateOf(-1) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        MaterialTheme {
            Surface(color = ViewerBg, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Перевод на фото", color = ViewerInk,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Нажми подсвеченную надпись", color = ViewerMuted,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                            Text("Готово", color = ViewerRed, fontSize = 14.sp)
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Товарное изображение с выделенными надписями",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                                .pointerInput(preview, regions) {
                                    detectTapGestures { tap ->
                                        val w = size.width.toFloat()
                                        val h = size.height.toFloat()
                                        val i = regions.indices.reversed().firstOrNull { index ->
                                            regions[index]?.intoCanvas(
                                                w, h,
                                                preview.width.toFloat(), preview.height.toFloat()
                                            )?.contains(tap.x, tap.y) == true
                                        }
                                        if (i != null) selected = i
                                    }
                                }
                        ) {
                            for ((index, region) in regions.withIndex()) {
                                val rect = region?.intoCanvas(
                                    size.width, size.height,
                                    preview.width.toFloat(), preview.height.toFloat()
                                ) ?: continue
                                val active = index == selected
                                drawRoundRect(
                                    color = if (active) ViewerGreen else ViewerRed,
                                    topLeft = Offset(rect.left, rect.top),
                                    size = Size(rect.right - rect.left, rect.bottom - rect.top),
                                    cornerRadius = CornerRadius(4.dp.toPx()),
                                    style = Stroke(width = (if (active) 2.5f else 1.25f).dp.toPx())
                                )
                                if (active) {
                                    drawRoundRect(
                                        color = ViewerGreen.copy(alpha = 0.13f),
                                        topLeft = Offset(rect.left, rect.top),
                                        size = Size(rect.right - rect.left, rect.bottom - rect.top),
                                        cornerRadius = CornerRadius(4.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                    Surface(color = ViewerSurface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                            if (selected >= 0 && selected < lines.size) {
                                Text(
                                    lines[selected].first, color = ViewerMuted,
                                    fontSize = 13.sp, lineHeight = 19.sp
                                )
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    lines[selected].second?.takeIf { it.isNotBlank() }
                                        ?: "Для этой строки перевод пока недоступен",
                                    color = ViewerInk, fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold, lineHeight = 24.sp
                                )
                            } else {
                                Text(
                                    "Распознано " + regions.count { it != null } + " областей",
                                    color = ViewerInk, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Нажми выделенную область, чтобы прочитать перевод.",
                                    color = ViewerMuted, fontSize = 12.sp, lineHeight = 18.sp
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Цены и условия покупки всегда сверяй с оригиналом.",
                                color = ViewerMuted, fontSize = 11.sp, lineHeight = 17.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
