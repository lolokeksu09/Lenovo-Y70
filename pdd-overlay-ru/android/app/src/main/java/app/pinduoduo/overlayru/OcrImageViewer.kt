package app.pinduoduo.overlayru

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
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
 * Reversible in-view Russian captions: the underlying bitmap is never edited.
 * Payment/price/OTP boxes are never covered, and captions too large for their
 * OCR region leave the original visible with a selectable outline.
 */
@Composable
internal fun OcrImageViewer(
    preview: Bitmap,
    lines: List<Pair<String, String?>>,
    regions: List<OcrRegion?>,
    onDismiss: () -> Unit
) {
    // Incoming ML Kit callbacks must not clear a selection or zoom.
    var selected by remember(preview) { mutableIntStateOf(-1) }
    var showInlineTranslations by remember(preview) { mutableStateOf(true) }
    var viewport by remember(preview) { mutableStateOf(OcrPhotoViewport()) }
    var displaySize by remember(preview) { mutableStateOf(IntSize.Zero) }
    val liveRegions by rememberUpdatedState(regions)
    val eligible = regions.indices.filter { regions[it] != null }
    val position = eligible.indexOf(selected)

    fun zoomBy(factor: Float) {
        viewport = viewport.applyGesture(
            zoomChange = factor, panX = 0f, panY = 0f,
            centroidX = displaySize.width / 2f,
            centroidY = displaySize.height / 2f,
            canvasWidth = displaySize.width.toFloat(),
            canvasHeight = displaySize.height.toFloat(),
            bitmapWidth = preview.width.toFloat(),
            bitmapHeight = preview.height.toFloat()
        )
    }
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
                                "Два пальца — масштаб, одним — выбор текста", color = ViewerMuted,
                                fontSize = 12.sp
                            )
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                            Text("Готово", color = ViewerRed, fontSize = 14.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (showInlineTranslations) "Русский текст поверх фото"
                            else "Оригинальные надписи",
                            fontSize = 12.sp, color = ViewerMuted
                        )
                        TextButton(onClick = {
                            showInlineTranslations = !showInlineTranslations
                        }) {
                            Text(
                                if (showInlineTranslations) "Показать оригинал"
                                else "Показать перевод",
                                color = ViewerGreen, fontSize = 12.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                            .background(Color.Black)
                            .clipToBounds()
                            .onSizeChanged { displaySize = it }
                            .pointerInput(preview) {
                                detectTransformGestures(panZoomLock = true) { center, pan, change, _ ->
                                    viewport = viewport.applyGesture(
                                        zoomChange = change,
                                        panX = pan.x, panY = pan.y,
                                        centroidX = center.x, centroidY = center.y,
                                        canvasWidth = size.width.toFloat(),
                                        canvasHeight = size.height.toFloat(),
                                        bitmapWidth = preview.width.toFloat(),
                                        bitmapHeight = preview.height.toFloat()
                                    )
                                }
                            }
                            .pointerInput(preview) {
                                detectTapGestures(
                                    onDoubleTap = { point ->
                                        viewport = viewport.applyGesture(
                                            zoomChange = if (viewport.scale < 2f) 2.5f
                                                else OcrPhotoViewport.MIN_ZOOM / viewport.scale,
                                            panX = 0f, panY = 0f,
                                            centroidX = point.x, centroidY = point.y,
                                            canvasWidth = size.width.toFloat(),
                                            canvasHeight = size.height.toFloat(),
                                            bitmapWidth = preview.width.toFloat(),
                                            bitmapHeight = preview.height.toFloat()
                                        )
                                    },
                                    onTap = { tap ->
                                        val (x, y) = viewport.toImageCanvas(
                                            tap.x, tap.y,
                                            size.width.toFloat(), size.height.toFloat()
                                        )
                                        val index = liveRegions.indices.reversed().firstOrNull { i ->
                                            liveRegions[i]?.intoCanvas(
                                                size.width.toFloat(), size.height.toFloat(),
                                                preview.width.toFloat(), preview.height.toFloat()
                                            )?.contains(x, y) == true
                                        }
                                        if (index != null) selected = index
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Товарное изображение с выделенными надписями",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    scaleX = viewport.scale
                                    scaleY = viewport.scale
                                    translationX = viewport.offsetX
                                    translationY = viewport.offsetY
                                }
                        )
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                                .graphicsLayer {
                                    scaleX = viewport.scale
                                    scaleY = viewport.scale
                                    translationX = viewport.offsetX
                                    translationY = viewport.offsetY
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

                                val source = lines.getOrNull(index)?.first.orEmpty()
                                val translated = lines.getOrNull(index)?.second
                                if (showInlineTranslations && OcrInlineTextPolicy.canCover(
                                    source, translated, region,
                                    rect.right - rect.left, rect.bottom - rect.top
                                )) {
                                    val message = translated!!.trim()
                                    val padX = 4.dp.toPx()
                                    val padY = 2.dp.toPx()
                                    val maxWidth = (rect.right - rect.left - 2 * padX)
                                        .toInt().coerceAtLeast(1)
                                    val maxHeight = rect.bottom - rect.top - 2 * padY
                                    val textPaint = TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                                        color = android.graphics.Color.WHITE
                                        isSubpixelText = true
                                    }
                                    var fitted: StaticLayout? = null
                                    for (fontSp in 16 downTo 10) {
                                        textPaint.textSize = fontSp.sp.toPx()
                                        val candidate = StaticLayout.Builder.obtain(
                                            message, 0, message.length, textPaint, maxWidth
                                        ).setAlignment(Layout.Alignment.ALIGN_CENTER)
                                            .setIncludePad(false)
                                            .build()
                                        if (candidate.height <= maxHeight) {
                                            fitted = candidate
                                            break
                                        }
                                    }
                                    fitted?.let { labelLayout ->
                                        drawRoundRect(
                                            color = ViewerSurface.copy(alpha = .97f),
                                            topLeft = Offset(rect.left, rect.top),
                                            size = Size(
                                                rect.right - rect.left, rect.bottom - rect.top
                                            ),
                                            cornerRadius = CornerRadius(3.dp.toPx())
                                        )
                                        drawIntoCanvas { canvas ->
                                            val native = canvas.nativeCanvas
                                            val checkpoint = native.save()
                                            native.translate(
                                                rect.left + padX,
                                                rect.top + padY +
                                                    (maxHeight - labelLayout.height) / 2f
                                            )
                                            labelLayout.draw(native)
                                            native.restoreToCount(checkpoint)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Surface(color = ViewerSurface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    String.format(java.util.Locale.ROOT, "%.1f×", viewport.scale),
                                    color = ViewerMuted, fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { zoomBy(0.7f) },
                                    enabled = viewport.scale > 1f,
                                    modifier = Modifier.size(48.dp)
                                ) { Text("−", color = ViewerInk, fontSize = 20.sp) }
                                TextButton(
                                    onClick = { viewport = OcrPhotoViewport() },
                                    enabled = viewport.scale > 1f,
                                    modifier = Modifier.height(48.dp)
                                ) { Text("Сброс", color = ViewerInk, fontSize = 12.sp) }
                                TextButton(
                                    onClick = { zoomBy(1.6f) },
                                    enabled = viewport.scale < OcrPhotoViewport.MAX_ZOOM,
                                    modifier = Modifier.size(48.dp)
                                ) { Text("+", color = ViewerInk, fontSize = 20.sp) }
                            }
                            if (position >= 0 && selected < lines.size) {
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
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Область " + (position + 1) + " из " + eligible.size,
                                        color = ViewerMuted, fontSize = 12.sp
                                    )
                                    Row {
                                        TextButton(
                                            onClick = { selected = eligible[position - 1] },
                                            enabled = position > 0,
                                            modifier = Modifier.height(48.dp)
                                        ) { Text("Пред.", color = ViewerInk, fontSize = 12.sp) }
                                        TextButton(
                                            onClick = { selected = eligible[position + 1] },
                                            enabled = position < eligible.lastIndex,
                                            modifier = Modifier.height(48.dp)
                                        ) { Text("След.", color = ViewerInk, fontSize = 12.sp) }
                                    }
                                }
                            } else {
                                Text(
                                    "Распознано " + regions.count { it != null } + " областей",
                                    color = ViewerInk, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "Нажми выделенную область или увеличь мелкий текст жестом и кнопкой «+».",
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
