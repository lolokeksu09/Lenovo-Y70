package app.pinduoduo.overlayru

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.lang.ref.WeakReference
import java.util.function.Consumer
import java.util.LinkedHashMap

/**
 * Main-thread-owned ML Kit translation state.
 * Preserve: offline model inspection, explicit download consent, single-flight noncancellable
 * tasks, failure backoff, limited callback fan-out and a bounded translation cache.
 */
class TranslationEngine private constructor(private val context: Context) {

    companion object {
        const val PREFS = "local_settings"
        const val PREF_ENABLED = "enabled"
        const val PREF_MODEL_ALLOWED = "model_allowed"
        const val PREF_WIFI_ONLY = "model_wifi_only"
        const val PREF_EN_MODEL_ALLOWED = "en_model_allowed"
        const val PREF_FLOAT_BUTTON = "float_button"

        private const val TAG = "PddOverlayRU"
        private const val SLOW_MS = 60_000L
        private const val INSPECTION_TIMEOUT_MS = 15_000L
        private const val ERROR_BACKOFF_MS = 30_000L

        @Volatile private var instance: TranslationEngine? = null

        /** Java and Kotlin callers retain TranslationEngine.get(context). */
        @JvmStatic
        @Synchronized
        fun get(context: Context): TranslationEngine =
            instance ?: TranslationEngine(context.applicationContext).also { instance = it }

        private fun safeError(error: Exception): String =
            if (error is MlKitException) "код " + error.errorCode
            else error.javaClass.simpleName
    }

    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
    )
    private val englishTranslator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.RUSSIAN)
            .build()
    )

    private val main = Handler(Looper.getMainLooper())
    private val downloadGate = DownloadAttemptGate()
    private val englishDownloadGate = DownloadAttemptGate()

    private var chineseModelReady = false
    private var englishModelReady = false
    private var englishDownloadRequestedAfterInspection = false
    private var englishLastError = ""
    private var englishModelPreparingWifiOnly = false
    private var modelPreparingWifiOnly = false
    private var inspectionInProgress = false
    private var inspectionCompleted = false
    private var downloadRequestedAfterInspection = false
    private var inspectionGeneration = 0
    private var lastError = ""
    private var lastInspection = "Модели: проверка при запуске…"
    private var translationFailures = 0
    private var inspectionListener: WeakReference<Consumer<String>> = WeakReference(null)

    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 350
    }
    private val failuresUntil = object : LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > 200
    }
    private val pending = TranslationQueueGate()

    init {
        restoreLocalModels()
    }

    fun prefs(): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun enabled(): Boolean = prefs().getBoolean(PREF_ENABLED, false)
    fun ready(): Boolean = chineseModelReady
    fun englishReady(): Boolean = englishModelReady
    fun englishPreparing(): Boolean = englishDownloadGate.inFlight()
    fun englishOverdue(): Boolean = englishDownloadGate.overdue()
    fun englishError(): String = englishLastError
    fun englishPreparingWifiOnly(): Boolean = englishModelPreparingWifiOnly
    fun preparing(): Boolean = downloadGate.inFlight()
    fun overdue(): Boolean = downloadGate.overdue()
    fun wifiOnly(): Boolean = prefs().getBoolean(PREF_WIFI_ONLY, false)
    fun preparingWifiOnly(): Boolean = modelPreparingWifiOnly
    fun inspecting(): Boolean = inspectionInProgress
    fun error(): String = lastError
    fun inspection(): String = lastInspection
    fun translationFailures(): Int = translationFailures

    /** Inspect locally cached models without requesting a download. */
    fun inspectModels(listener: Consumer<String>) {
        if (inspectionInProgress) {
            listener.accept("Проверка локальных моделей уже выполняется…")
            return
        }
        inspectionListener = WeakReference(listener)
        restoreLocalModels()
    }

    private fun restoreLocalModels() {
        if (inspectionInProgress) return
        inspectionInProgress = true
        val scan = ++inspectionGeneration
        main.postDelayed({
            if (inspectionInProgress && scan == inspectionGeneration) {
                finishInspection(
                    scan, false, false, false,
                    "ML Kit не ответил на локальную проверку за 15 секунд", true
                )
            }
        }, INSPECTION_TIMEOUT_MS)

        try {
            RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { models ->
                    var zh = false
                    var ru = false
                    var en = false
                    for (model in models) {
                        when (model.language) {
                            TranslateLanguage.CHINESE -> zh = true
                            TranslateLanguage.RUSSIAN -> ru = true
                            TranslateLanguage.ENGLISH -> en = true
                        }
                    }
                    finishInspection(
                        scan, zh, ru, en,
                        "Локальные модели: китайская — " + (if (zh) "есть" else "нет") +
                            ", английская — " + (if (en) "есть" else "нет") +
                            ", русская — " + (if (ru) "есть" else "нет"),
                        false
                    )
                }
                .addOnFailureListener { error ->
                    finishInspection(
                        scan, false, false, false,
                        "Проверка моделей: " + safeError(error), true
                    )
                }
        } catch (error: RuntimeException) {
            finishInspection(
                scan, false, false, false,
                "Проверка моделей: " + safeError(error), true
            )
        }
    }

    private fun finishInspection(
        scan: Int, zh: Boolean, ru: Boolean, en: Boolean,
        status: String, failed: Boolean
    ) {
        if (!inspectionInProgress || scan != inspectionGeneration) return
        inspectionInProgress = false
        inspectionCompleted = true
        lastInspection = status

        if (ModelAvailability.usable(en, ru)) {
            englishModelReady = true
            englishLastError = ""
        }
        if (ModelAvailability.usable(zh, ru)) {
            chineseModelReady = true
            lastError = ""
            Log.i(TAG, "cached translation models found; ready restored")
        } else if (failed) {
            Log.w(TAG, "local model scan failed: " + status)
        } else {
            Log.i(TAG, "local models zh=" + zh + " ru=" + ru)
        }

        val listener = inspectionListener.get()
        inspectionListener.clear()
        listener?.accept(status)
        notifyStatus()
        if (downloadRequestedAfterInspection) {
            downloadRequestedAfterInspection = false
            prepare(Runnable { })
        }
        if (englishDownloadRequestedAfterInspection) {
            englishDownloadRequestedAfterInspection = false
            prepareEnglish(Runnable { })
        }
    }

    /** Do not duplicate a non-cancellable SDK request, even after its UI deadline. */
    fun prepare(listener: Runnable) {
        if (!prefs().getBoolean(PREF_MODEL_ALLOWED, false) || chineseModelReady) {
            listener.run()
            return
        }
        if (downloadGate.inFlight()) {
            if (downloadGate.overdue()) {
                lastError =
                    "ML Kit ещё выполняет прежний запрос. Повтор не отправлен; проверь локальные модели."
                restoreLocalModels()
            }
            listener.run()
            return
        }
        if (!inspectionCompleted || inspectionInProgress) {
            downloadRequestedAfterInspection = true
            restoreLocalModels()
            listener.run()
            return
        }
        if (!hasInternetNetwork()) {
            lastError = "Сеть недоступна. Если модели скачаны, нажми «Проверить модели»."
            listener.run()
            return
        }

        val attempt = downloadGate.begin()
        if (attempt < 0) {
            listener.run()
            return
        }
        lastError = ""
        modelPreparingWifiOnly = wifiOnly()
        Log.i(TAG, "ML Kit download requested, Wi-Fi-only=" + modelPreparingWifiOnly)
        main.postDelayed({
            if (downloadGate.markOverdue(attempt)) {
                lastError =
                    "ML Kit не ответил за 60 секунд. Запрос не отменён; повторная загрузка не запускается."
                Log.w(TAG, "ML Kit model task still pending after 60 seconds")
                notifyStatus()
            }
        }, SLOW_MS)

        try {
            translator.downloadModelIfNeeded(
                ModelDownloadConditionsFactory.create(modelPreparingWifiOnly)
            )
                .addOnSuccessListener {
                    if (downloadGate.finish(attempt)) {
                        chineseModelReady = true
                        lastError = ""
                        lastInspection = "Модели готовы: ML Kit подтвердил загрузку"
                        Log.i(TAG, "ML Kit model ready")
                        notifyStatus()
                    }
                }
                .addOnFailureListener { error ->
                    if (downloadGate.finish(attempt)) {
                        lastError = "ML Kit: " + safeError(error)
                        Log.w(TAG, "ML Kit download failure: " + safeError(error))
                        notifyStatus()
                    }
                }
        } catch (error: RuntimeException) {
            if (downloadGate.finish(attempt)) {
                lastError = "Не удалось запустить ML Kit: " + safeError(error)
                Log.w(TAG, "ML Kit download rejected: " + safeError(error))
                notifyStatus()
            }
        }
        listener.run()
    }

    /** English → Russian requires separate user approval and a separate model. */
    fun prepareEnglish(listener: Runnable) {
        if (!prefs().getBoolean(PREF_EN_MODEL_ALLOWED, false) || englishModelReady) {
            listener.run()
            return
        }
        if (englishDownloadGate.inFlight()) {
            if (englishDownloadGate.overdue()) {
                englishLastError =
                    "Английская модель: ML Kit не ответил за 60 секунд; второй запрос не запущен"
                restoreLocalModels()
            }
            listener.run()
            return
        }
        if (!inspectionCompleted || inspectionInProgress) {
            englishDownloadRequestedAfterInspection = true
            restoreLocalModels()
            listener.run()
            return
        }
        if (!hasInternetNetwork()) {
            englishLastError = "Нет интернета для английской модели"
            listener.run()
            return
        }
        val attempt = englishDownloadGate.begin()
        if (attempt < 0) {
            listener.run()
            return
        }
        englishLastError = ""
        englishModelPreparingWifiOnly = wifiOnly()
        Log.i(TAG, "ML Kit English model requested, Wi-Fi-only=" + englishModelPreparingWifiOnly)
        main.postDelayed({
            if (englishDownloadGate.markOverdue(attempt)) {
                englishLastError =
                    "Английская модель: нет ответа ML Kit за 60 секунд. Проверь VPN и сеть."
                Log.w(TAG, "English model still pending after 60 seconds")
                notifyStatus()
            }
        }, SLOW_MS)

        try {
            englishTranslator.downloadModelIfNeeded(
                ModelDownloadConditionsFactory.create(englishModelPreparingWifiOnly)
            )
                .addOnSuccessListener {
                    if (englishDownloadGate.finish(attempt)) {
                        englishModelReady = true
                        englishLastError = ""
                        lastInspection = "ML Kit: английская модель готова офлайн"
                        Log.i(TAG, "English to Russian model ready")
                        notifyStatus()
                    }
                }
                .addOnFailureListener { error ->
                    if (englishDownloadGate.finish(attempt)) {
                        englishLastError = "Английская модель: " + safeError(error)
                        Log.w(TAG, "English model download failed: " + safeError(error))
                        notifyStatus()
                    }
                }
        } catch (error: RuntimeException) {
            if (englishDownloadGate.finish(attempt)) {
                englishLastError = "Английская модель: " + safeError(error)
                Log.w(TAG, "English model download rejected: " + safeError(error))
                notifyStatus()
            }
        }
        listener.run()
    }

    /** VPN transport is a hint, never proof that ML Kit endpoints are blocked. */
    fun vpnActive(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun hasInternetNetwork(): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return true
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network)
        return capabilities == null ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun notifyStatus() {
        PddAccessibilityService.requestRedraw()
    }

    /**
     * Returns an immediate glossary/cache value or null.
     * Unknown labels are resolved by ML Kit and sent to bounded pending callbacks.
     */
    fun translate(text: String?, onReady: Consumer<String?>?): String? =
        translateInternal(text, onReady, requireOverlayEnabled = true)

    /** Explicit image-picker OCR may translate while Accessibility overlay is disabled. */
    fun translateOcr(text: String?, onReady: Consumer<String?>?): String? =
        translateInternal(text, onReady, requireOverlayEnabled = false)

    private fun translateInternal(
        text: String?, onReady: Consumer<String?>?, requireOverlayEnabled: Boolean
    ): String? {
        if (text == null) return null
        val clean = text.trim()
        if (!TranslationPolicy.shouldTranslate(clean, false, false) ||
            PaymentActionPolicy.isProtected(clean)) return null
        // User edits have priority over glossary and ML Kit cache, without
        // granting edits to prices, OTPs or payment confirmation actions.
        CustomGlossary.lookup(context, clean)?.let { return it }
        val sourceLanguage = TranslationPolicy.sourceLanguage(clean)
        if (sourceLanguage == TranslationPolicy.NONE) return null

        if (sourceLanguage == TranslationPolicy.CHINESE) {
            val known = Glossary.lookup(context, clean)
            if (!known.isNullOrEmpty()) return known
        } else if (sourceLanguage == TranslationPolicy.ENGLISH) {
            val known = EnglishUiGlossary.lookup(clean)
            if (known != null) return known
        }

        val key = (if (sourceLanguage == TranslationPolicy.CHINESE) "zh:" else "en:") + clean
        cache[key]?.let { return it }
        if ((requireOverlayEnabled && !enabled()) ||
            (sourceLanguage == TranslationPolicy.CHINESE && !chineseModelReady) ||
            (sourceLanguage == TranslationPolicy.ENGLISH && !englishModelReady)) return null

        val until = failuresUntil[key]
        if (until != null && SystemClock.uptimeMillis() < until) return null

        // Never retain unlimited pending ML Kit tasks or publish expired results.
        val ticket = pending.reserve(key, SystemClock.uptimeMillis(), onReady).ticket
            ?: return null
        val activeTranslator = if (sourceLanguage == TranslationPolicy.CHINESE)
            translator else englishTranslator
        try {
            activeTranslator.translate(clean)
                .addOnSuccessListener { result -> complete(key, ticket, result, null) }
                .addOnFailureListener { error -> complete(key, ticket, null, error) }
        } catch (error: RuntimeException) {
            complete(key, ticket, null, error)
        }
        return null
    }

    private fun complete(source: String, ticket: Long, result: String?, error: Exception?) {
        // An expired ML Kit Task cannot overwrite a newer request or leak callbacks.
        val callbacks = pending.finish(source, ticket, SystemClock.uptimeMillis())
            ?: return
        if (!result.isNullOrBlank()) {
            cache[source] = result.trim()
            failuresUntil.remove(source)
        } else if (error != null) {
            translationFailures++
            failuresUntil[source] = SystemClock.uptimeMillis() + ERROR_BACKOFF_MS
            Log.w(TAG, "ML Kit text translation failed: " + safeError(error))
        }
        callbacks.forEach { callback -> callback.accept(result) }
    }
}
