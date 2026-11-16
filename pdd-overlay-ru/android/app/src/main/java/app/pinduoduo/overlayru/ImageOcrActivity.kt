package app.pinduoduo.overlayru

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import app.pinduoduo.overlayru.ui.design.PddFrame
import app.pinduoduo.overlayru.ui.design.PddNav
import app.pinduoduo.overlayru.ui.design.PddTab
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicit, user-selected image OCR. Never requests screen-capture, storage or
 * additional Accessibility permissions; image pixels are processed on-device.
 * ML Kit translation uses the existing user-consented offline model.
 */
class ImageOcrActivity : ComponentActivity() {
    private data class OcrLine(
        val source: String,
        val translation: String? = null,
        val region: OcrRegion? = null
    )

    private val engine by lazy { TranslationEngine.get(this) }
    private var recognizerOpened = false
    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            .also { recognizerOpened = true }
    }
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var busy by mutableStateOf(false)
    private var notice by mutableStateOf(
        "Выбери скриншот или изображение товара. OCR выполняется на устройстве."
    )
    private var rows by mutableStateOf<List<OcrLine>>(emptyList())
    private var thumbnail by mutableStateOf<Bitmap?>(null)
    private var errorTitle by mutableStateOf<String?>(null)
    private var errorHint by mutableStateOf("")
    private val mainHandler = Handler(Looper.getMainLooper())
    private var translationCursor = 0
    private var translationInflight = 0

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) analyze(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(6, 8, 13)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(6, 8, 13)
        setContent {
            PddFrame(PddTab.PHOTO, onChoose = { PddNav.open(this,it) }) {
                OcrScreenV2(
                busy = busy,
                preview = thumbnail,
                status = notice,
                errorTitle = errorTitle,
                errorHint = errorHint,
                rows = rows.map { it.source to it.translation },
                regions = rows.map { it.region },
                modelReady = engine.ready() || engine.englishReady(),
                onPick = { picker.launch("image/*") },
                onSettings = {
                    PddNav.open(this, PddTab.SETTINGS)
                },
                onCopy = { copyResults() },
                onBack = { finish() }
                )
            }
        }
    }

    private fun analyze(uri: Uri) {
        val scan = ++generation
        busy = true
        rows = emptyList()
        thumbnail = null
        translationCursor = 0
        translationInflight = 0
        notice = "Открываем изображение…"
        errorTitle = null
        errorHint = ""
        worker.execute {
            val opened = OcrImageOpener.open(this, uri)
            runOnUiThread {
                if (isDestroyed || scan != generation) {
                    if (opened is OcrImageOpenResult.Ready) {
                        opened.full.recycle()
                        opened.preview.recycle()
                    }
                    return@runOnUiThread
                }
                when (opened) {
                    is OcrImageOpenResult.Failed -> showImageOpenFailure(opened.error)
                    is OcrImageOpenResult.Ready -> recognizeImage(scan, opened)
                }
            }
        }
    }

    private fun showImageOpenFailure(error: OcrOpenError) {
        busy = false
        notice = ""
        when (error) {
            OcrOpenError.PERMISSION -> {
                errorTitle = "Нет доступа к фотографии"
                errorHint = "Повторно выбери изображение в системной галерее."
            }
            OcrOpenError.UNAVAILABLE -> {
                errorTitle = "Не получилось прочитать файл"
                errorHint = "Если фото в облаке, сохрани его на телефоне и выбери снова."
            }
            OcrOpenError.TOO_LARGE -> {
                errorTitle = "Изображение слишком большое"
                errorHint = "Сделай скриншот или сохрани копию размером до 24 МБ."
            }
            OcrOpenError.INVALID_IMAGE -> {
                errorTitle = "Формат изображения не распознан"
                errorHint = "Попробуй скриншот в JPG, PNG или WebP."
            }
        }
    }

    private fun recognizeImage(scan: Int, opened: OcrImageOpenResult.Ready) {
        thumbnail = opened.preview
        notice = "Распознаём китайский текст…"
        errorHint = "Обработка выполняется на устройстве"
        try {
            recognizer.process(InputImage.fromBitmap(opened.full, 0))
                .addOnSuccessListener { document ->
                    if (!isDestroyed && scan == generation) {
                        val recognized = document.textBlocks.flatMap { it.lines }
                        val lines = OcrTextPolicy.cleaned(recognized.map { it.text })
                        busy = false
                        rows = lines.map { text ->
                            val raw = recognized.firstOrNull { it.text.trim() == text }
                            val rect = raw?.boundingBox
                            val region = if (rect != null && OcrTextPolicy.canTranslate(text)) {
                                OcrRegion.fromPixels(
                                    rect.left, rect.top, rect.right, rect.bottom,
                                    opened.full.width, opened.full.height
                                )
                            } else null
                            OcrLine(source = text, region = region)
                        }
                        if (lines.isEmpty()) {
                            notice = "Текст не найден"
                            errorHint = "Попробуй более чёткий скриншот или приблизь текст."
                        } else {
                            notice = "Распознано строк: " + lines.size
                            errorHint = if (engine.ready() || engine.englishReady()) {
                                "Переводим доступными офлайн-моделями"
                            } else {
                                "Известные надписи переводятся локально; для новых фраз загрузи модель в настройках"
                            }
                            translateRows(scan)
                        }
                    }
                }
                .addOnFailureListener {
                    if (!isDestroyed && scan == generation) {
                        busy = false
                        notice = ""
                        errorTitle = "Не удалось распознать текст"
                        errorHint = "Попробуй другой скриншот с более чёткими надписями."
                    }
                }
                .addOnCompleteListener { opened.full.recycle() }
        } catch (_: RuntimeException) {
            opened.full.recycle()
            busy = false
            notice = ""
            errorTitle = "Распознавание не запустилось"
            errorHint = "Выбери изображение ещё раз."
        }
    }

    /**
     * OCR can return up to 60 lines, whereas the shared ML Kit queue accepts 24.
     * Feed only four at a time; a callback or timeout releases each slot.
     * Old selection callbacks cannot update the newer image.
     */
    private fun translateRows(scan: Int) {
        if (scan != generation || isDestroyed) return
        while (translationInflight < 4 && translationCursor < rows.size) {
            val index = translationCursor++
            val source = rows[index].source
            if (!OcrTextPolicy.canTranslate(source)) continue
            // Allow user corrections and built-in glossary translations even without
            // ML Kit language models. translateOcr() checks glossary first and only
            // calls ML Kit when the relevant model is actually ready.
            if (TranslationPolicy.sourceLanguage(source) == TranslationPolicy.NONE) continue

            translationInflight++
            val finished = AtomicBoolean(false)
            var timeout: Runnable? = null
            val complete: (String?) -> Unit = { value ->
                if (finished.compareAndSet(false, true)) {
                    timeout?.let(mainHandler::removeCallbacks)
                    if (!isDestroyed && scan == generation) {
                        if (!value.isNullOrBlank() && rows.getOrNull(index)?.source == source) {
                            rows = rows.toMutableList().also {
                                it[index] = it[index].copy(translation = value)
                            }
                        }
                        translationInflight--
                        mainHandler.post { translateRows(scan) }
                    }
                }
            }
            timeout = Runnable { complete(null) }
            timeout?.let { mainHandler.postDelayed(it, 35_000L) }
            try {
                val immediate = engine.translateOcr(source) { translated -> complete(translated) }
                if (immediate != null) complete(immediate)
            } catch (_: RuntimeException) {
                complete(null)
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
        mainHandler.removeCallbacksAndMessages(null)
        if (recognizerOpened) recognizer.close()
        super.onDestroy()
    }
}

