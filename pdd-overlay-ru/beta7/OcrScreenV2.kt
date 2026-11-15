package app.pinduoduo.overlayru

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg = Color(0xFF080A0E)
private val surface = Color(0xFF15191F)
private val border = Color(0xFF303741)
private val bright = Color(0xFFF5F6F8)
private val subdued = Color(0xFFAFB6C1)
private val muted = Color(0xFF858E9C)
private val accent = Color(0xFFE93950)
private val errorBg = Color(0xFF29171D)
private val errorText = Color(0xFFFFA3AB)

/** Compact AMOLED OCR surface with accessible actionable error/empty/loading states. */
@Composable
internal fun OcrScreenV2(
    busy: Boolean,
    preview: Bitmap?,
    status: String,
    errorTitle: String?,
    errorHint: String,
    rows: List<Pair<String, String?>>,
    regions: List<OcrRegion?>,
    modelReady: Boolean,
    onPick: () -> Unit,
    onSettings: () -> Unit,
    onCopy: () -> Unit,
    onBack: () -> Unit
) {
    var photoViewerOpen by remember(preview) { mutableStateOf(false) }
    if (photoViewerOpen && preview != null) {
        OcrImageViewer(
            preview = preview,
            lines = rows,
            regions = regions,
            onDismiss = { photoViewerOpen = false }
        )
    }
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = bg, surface = surface, primary = accent,
            onPrimary = Color.White, onSurface = bright, onSurfaceVariant = subdued
        )
    ) {
        // MaterialTheme supplies colors, not an Activity background.
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 580.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        TextButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
                            Text("‹  Назад", color = subdued, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text("Перевод фото", color = bright, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Надписи на карточках товаров Pinduoduo",
                            color = subdued, fontSize = 14.sp, lineHeight = 21.sp
                        )
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = surface,
                            border = BorderStroke(1.dp, border)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(15.dp)) {
                                if (preview == null) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(174.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            PhotoPlaceholder()
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "Выбери изображение",
                                                color = bright, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "Скриншот или фото товара",
                                                color = subdued, fontSize = 12.sp
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 226.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = preview.asImageBitmap(),
                                            contentDescription = "Предпросмотр выбранного изображения",
                                            modifier = Modifier.fillMaxWidth().heightIn(max = 226.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))
                                }
                                Button(
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    onClick = onPick,
                                    enabled = !busy,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                                ) {
                                    Text(
                                        if (preview == null) "Выбрать фото" else "Выбрать другое фото",
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    if (preview != null && rows.isNotEmpty() && regions.any { it != null }) {
                        item {
                            OutlinedButton(
                                onClick = { photoViewerOpen = true },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "Посмотреть текст на изображении",
                                    color = bright, fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (busy || errorTitle != null || status.isNotBlank()) {
                        item {
                            Surface(
                                color = if (errorTitle != null) errorBg else surface,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(15.dp)
                                        .semantics { liveRegion = LiveRegionMode.Polite },
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (busy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = accent
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(
                                            errorTitle ?: status,
                                            color = if (errorTitle != null) errorText else bright,
                                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                                        )
                                        if (errorHint.isNotBlank()) {
                                            Text(errorHint, color = subdued, fontSize = 12.sp, lineHeight = 18.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (rows.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Результат", color = bright, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Переведено " + rows.count { !it.second.isNullOrBlank() } +
                                            " из " + rows.size + " строк",
                                        color = subdued, fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = onCopy, modifier = Modifier.height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Text("Копировать", color = bright, fontSize = 13.sp)
                                }
                            }
                        }
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = surface,
                                border = BorderStroke(1.dp, border)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
                                    rows.forEachIndexed { index, row ->
                                        if (index > 0) HorizontalDivider(thickness = 0.5.dp, color = border)
                                        Column(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(row.first, color = subdued, fontSize = 13.sp, lineHeight = 19.sp)
                                            row.second?.takeIf { it.isNotBlank() }?.let { translated ->
                                                Text(
                                                    translated, color = bright, fontSize = 15.sp,
                                                    lineHeight = 22.sp, fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(
                                    start = 15.dp, end = 10.dp, top = 10.dp, bottom = 10.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Языковая модель", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = bright)
                                    Text(
                                        if (modelReady) "Офлайн-перевод доступен"
                                        else "Для перевода скачай модель",
                                        fontSize = 11.sp, color = subdued, lineHeight = 17.sp
                                    )
                                }
                                TextButton(onClick = onSettings, modifier = Modifier.height(48.dp)) {
                                    Text("Настроить", fontSize = 12.sp, color = accent)
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Файл обрабатывается на телефоне. Цены, доставку и условия покупки сверяй с оригиналом.",
                            color = muted, fontSize = 11.sp, lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoPlaceholder() {
    Canvas(modifier = Modifier.size(48.dp)) {
        val width = 2.dp.toPx()
        drawRoundRect(
            color = muted,
            topLeft = Offset(width, width),
            size = Size(size.width - width * 2, size.height - width * 2),
            cornerRadius = CornerRadius(9.dp.toPx()),
            style = Stroke(width)
        )
        drawCircle(
            color = muted, radius = 4.dp.toPx(),
            center = Offset(size.width * 0.73f, size.height * 0.32f)
        )
        val landscape = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.75f)
            lineTo(size.width * 0.35f, size.height * 0.50f)
            lineTo(size.width * 0.51f, size.height * 0.67f)
            lineTo(size.width * 0.62f, size.height * 0.55f)
            lineTo(size.width * 0.85f, size.height * 0.77f)
        }
        drawPath(landscape, muted, style = Stroke(width))
    }
}
