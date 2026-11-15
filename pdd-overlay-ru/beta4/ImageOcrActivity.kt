package app.pinduoduo.overlayru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Explicit, user-selected image OCR. Never requests screen-capture, storage or
 * additional Accessibility permissions; image pixels are processed on-device.
 * ML Kit translation uses the existing user-consented offline model.
 */
class ImageOcrActivity : ComponentActivity() {
    private data class OcrLine(val source: String, val translation: String? = null)

    private val engine by lazy { TranslationEngine.get(this) }
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var busy by mutableStateOf(false)
    private var notice by mutableStateOf(
        "Выбери скриншот или изображение товара. OCR выполняется на устройстве."
    )
    private var rows by mutableStateOf<List<OcrLine>>(emptyList())

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyze(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(7, 9, 12)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(7, 9, 12)
        setContent {
            ImageOcrScreen(
                busy = busy,
                status = notice,
                rows = rows.map { it.source to it.translation },
                onPick = { picker.launch("image/*") },
                onSettings = {
                    startActivity(Intent(this, MainActivity::class.java))
                },
                onCopy = { copyResults() },
                onBack = { finish() }
            )
        }
    }

    private fun analyze(uri: Uri) {
        val scan = ++generation
        busy = true
        rows = emptyList()
        notice = "Читаю текст с изображения…"
        worker.execute {
            val bitmap = try { decodeBounded(uri) } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed || scan != generation) {
                    bitmap?.recycle()
                    return@runOnUiThread
                }
                if (bitmap == null) {
                    busy = false
                    notice = "Не удалось открыть изображение. Выбери JPG или PNG."
                    return@runOnUiThread
                }
                try {
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { document ->
                            if (!isDestroyed && generation == scan) {
                                val text = document.textBlocks.flatMap { it.lines }
                                    .map { it.text.trim() }
                                    .filter { it.isNotBlank() }
                                    .distinct()
                                    .take(60)
                                busy = false
                                rows = text.map { OcrLine(it) }
                                if (text.isEmpty()) {
                                    notice = "Текст не распознан. Попробуй более чёткий скриншот."
                                } else {
                                    notice = if (engine.ready() || engine.englishReady()) {
                                        "Распознано строк: ${text.size}. Перевожу доступными офлайн-моделями…"
                                    } else {
                                        "Распознано строк: ${text.size}. Для перевода скачай языковую модель в настройках."
                                    }
                                    translateRows(scan)
                                }
                            }
                        }
                        .addOnFailureListener {
                            if (!isDestroyed && generation == scan) {
                                busy = false
                                notice = "Ошибка распознавания. Попробуй другое изображение."
                            }
                        }
                        .addOnCompleteListener { bitmap.recycle() }
                } catch (_: RuntimeException) {
                    bitmap.recycle()
                    busy = false
                    notice = "Не удалось запустить распознавание."
                }
            }
        }
    }

    /** Downsample before decoding; untrusted gallery URIs cannot allocate full-size bitmaps. */
    private fun decodeBounded(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (maxOf(width, height) / sample > 2048) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun translateRows(scan: Int) {
        val initial = rows
        initial.forEachIndexed { index, item ->
            if (!TranslationPolicy.shouldTranslate(item.source, false, false) ||
                PaymentActionPolicy.isProtected(item.source)) return@forEachIndexed
            val immediate = engine.translateOcr(item.source) { translated ->
                if (translated.isNullOrBlank() || isDestroyed || generation != scan) return@translateOcr
                if (rows.getOrNull(index)?.source == item.source) {
                    rows = rows.toMutableList().also {
                        it[index] = OcrLine(item.source, translated)
                    }
                }
            }
            if (!immediate.isNullOrBlank() && generation == scan &&
                rows.getOrNull(index)?.source == item.source) {
                rows = rows.toMutableList().also {
                    it[index] = OcrLine(item.source, immediate)
                }
            }
        }
    }

    private fun copyResults() {
        if (rows.isEmpty()) return
        val text = rows.joinToString("\n") { it.translation ?: it.source }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PDD OCR перевод", text))
        notice = "Текст скопирован в буфер обмена"
    }

    override fun onDestroy() {
        ++generation
        worker.shutdownNow()
        if (this::recognizer.isInitialized) recognizer.close()
        super.onDestroy()
    }
}

private val OCRBg = Color(0xFF07090C)
private val OCRSurface = Color(0xFF14171D)
private val OCRMuted = Color(0xFFA7ADB8)
private val OCRText = Color(0xFFF4F5F8)
private val OCRRed = Color(0xFFF13D51)

@Composable
private fun ImageOcrScreen(
    busy: Boolean,
    status: String,
    rows: List<Pair<String, String?>>,
    onPick: () -> Unit,
    onSettings: () -> Unit,
    onCopy: () -> Unit,
    onBack: () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OCRRed, onPrimary = Color.White,
            background = OCRBg, surface = OCRSurface, onSurface = OCRText
        )
    ) {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, top = 16.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                androidx.compose.material3.TextButton(onClick = onBack) { Text("‹  Назад") }
                Text("Перевод изображения", fontSize = 25.sp,
                    fontWeight = FontWeight.Bold, color = OCRText)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Выбери скриншот карточки товара Pinduoduo. " +
                        "Китайский текст распознаётся локально; перевод использует уже загруженную ML Kit модель.",
                    fontSize = 13.sp, lineHeight = 20.sp, color = OCRMuted
                )
            }
            item {
                Button(
                    onClick = onPick,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = OCRRed)
                ) {
                    Text(if (busy) "Распознаю…" else "Выбрать изображение")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Настройки языковых моделей") }
                Spacer(Modifier.height(8.dp))
                Text(status, color = OCRMuted, fontSize = 13.sp, lineHeight = 19.sp)
                Text(
                    "Без постоянного захвата экрана и доступа ко всем фото. " +
                        "Цены и условия покупки сверяй с оригиналом.",
                    color = OCRMuted, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
            if (rows.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = onCopy, modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Копировать результат") }
                }
            }
            itemsIndexed(rows) { _, row ->
                Surface(
                    color = OCRSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF30353D))
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(row.first, color = OCRMuted, fontSize = 13.sp, lineHeight = 18.sp)
                        if (!row.second.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(row.second, color = OCRText, fontSize = 15.sp, lineHeight = 21.sp)
                        }
                    }
                }
            }
        }
    }
}
