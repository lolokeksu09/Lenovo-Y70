package app.pinduoduo.overlayru

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.text.StaticLayout
import android.text.Layout
import android.text.TextPaint
import android.graphics.Paint
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Translate only visible, non-editable Pinduoduo nodes.
 * All captions use TYPE_ACCESSIBILITY_OVERLAY + FLAG_NOT_TOUCHABLE; interactive
 * floating control remains an independent overlay, hidden outside the target app.
 */
class PddAccessibilityService : AccessibilityService() {
    companion object {
        private const val PDD = "com.xunmeng.pinduoduo"
        private const val TAG = "PddOverlayRU"
        private var current: WeakReference<PddAccessibilityService> = WeakReference(null)

        @JvmStatic
        fun isConnected(): Boolean = current.get() != null

        @JvmStatic
        fun refreshRunningService() {
            current.get()?.onSettingChanged()
        }

        @JvmStatic
        fun requestRedraw() {
            current.get()?.scheduleRedraw()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val redraw = Runnable { render() }
    private var windows: WindowManager? = null
    private var overlay: FrameLayout? = null
    private var floatingButton: TextView? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var engine: TranslationEngine? = null
    private var generation = 0
    private val visibleChineseTexts = HashSet<String>()
    /** Reuse captions across accessibility events to avoid flickering. */
    private val activeLabels = HashMap<String, TextView>()

    private val foregroundGuard = object : Runnable {
        override fun run() {
            if (floatingButton == null) return
            if (!isPddForeground()) {
                handler.removeCallbacks(redraw)
                removeOverlay()
                hideFloatingButton()
            } else {
                handler.postDelayed(this, 1500L)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        Log.i(TAG, "accessibility service connected")
        windows = getSystemService(WINDOW_SERVICE) as? WindowManager
        val e = TranslationEngine.get(this)
        engine = e
        if (e.prefs().getBoolean(TranslationEngine.PREF_MODEL_ALLOWED, false)) {
            e.prepare(Runnable { scheduleRedraw() })
        }
        if (e.prefs().getBoolean(TranslationEngine.PREF_EN_MODEL_ALLOWED, false)) {
            e.prepareEnglish(Runnable { scheduleRedraw() })
        }
        scheduleRedraw()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = engine ?: return
        if (event == null) return
        val pkg = event.packageName
        val targetEvent = pkg != null && PDD.contentEquals(pkg)
        if (targetEvent && isPddForeground()) {
            showFloatingButton()
            if (e.enabled()) scheduleRedraw() else removeOverlay()
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            if (!isPddForeground()) {
                handler.removeCallbacks(redraw)
                removeOverlay()
                hideFloatingButton()
            }
        }
    }

    private fun isPddForeground(): Boolean {
        val root = rootInActiveWindow
        return root != null && root.packageName != null && PDD.contentEquals(root.packageName)
    }

    override fun onInterrupt() {
        removeOverlay()
        hideFloatingButton()
    }

    override fun onDestroy() {
        handler.removeCallbacks(redraw)
        handler.removeCallbacks(foregroundGuard)
        removeOverlay()
        hideFloatingButton()
        if (current.get() === this) current = WeakReference(null)
        Log.i(TAG, "accessibility service destroyed")
        super.onDestroy()
    }

    private fun onSettingChanged() {
        val e = engine ?: return
        if (isPddForeground()) showFloatingButton() else hideFloatingButton()
        if (!e.enabled()) {
            handler.removeCallbacks(redraw)
            removeOverlay()
        } else {
            if (e.prefs().getBoolean(TranslationEngine.PREF_MODEL_ALLOWED, false)) {
                e.prepare(Runnable { scheduleRedraw() })
            }
            if (e.prefs().getBoolean(TranslationEngine.PREF_EN_MODEL_ALLOWED, false)) {
                e.prepareEnglish(Runnable { scheduleRedraw() })
            }
            scheduleRedraw()
        }
    }

    private fun scheduleRedraw() {
        handler.removeCallbacks(redraw)
        handler.postDelayed(redraw, 260L)
    }

    private fun render() {
        val window = windows
        val e = engine
        if (window == null || e == null || !e.enabled()) {
            removeOverlay()
            return
        }

        val root = rootInActiveWindow
        if (root == null || root.packageName == null || !PDD.contentEquals(root.packageName)) {
            removeOverlay()
            hideFloatingButton()
            return
        }
        showFloatingButton()

        generation++
        val displaySize = Point()
        window.defaultDisplay.getRealSize(displaySize)
        if (displaySize.x <= 0 || displaySize.y <= 0) return
        val discovered = findText(root, displaySize.x, displaySize.y)
        val selected = OverlayDensityPolicy.select(
            discovered.map {
                OverlayDensityPolicy.Candidate(
                    it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom,
                    it.isReview
                )
            },
            displaySize.x, displaySize.y,
            dp(32f).toInt(), dp(104f).toInt(), dp(84f).toInt()
        )
        val targets = selected.map { discovered[it] }
        if (targets.isEmpty()) {
            clearLabels()
            visibleChineseTexts.clear()
            return
        }
        visibleChineseTexts.clear()
        for (target in targets) visibleChineseTexts.add(target.text)
        ensureOverlay()

        val panel = overlay ?: return
        // New WindowManager views do not have valid screen coordinates before layout.
        if (!panel.isLaidOut || panel.width <= 0 || panel.height <= 0) {
            panel.post { scheduleRedraw() }
            return
        }

        val origin = IntArray(2)
        panel.getLocationOnScreen(origin)
        val screenWidth = panel.width
        val screenHeight = panel.height
        val thisFrame = HashSet<String>()
        val occupiedLabels = ArrayList<Rect>()
        val weakService = WeakReference(this)
        for (target in targets) {
            val source = target.text
            val translated = if (target.isReview) {
                e.translateReview(source) { value ->
                    weakService.get()?.onTranslationReady(source, value)
                }
            } else {
                e.translate(source) { value ->
                    weakService.get()?.onTranslationReady(source, value)
                }
            }
            if (translated.isNullOrEmpty() || translated == source) continue
            val key = target.bounds.toShortString() + "::" + source
            if (addLabel(
                key, translated, target.bounds, origin, screenWidth,
                screenHeight, occupiedLabels, target.isReview
            )) thisFrame.add(key)
        }

        for (key in ArrayList(activeLabels.keys)) {
            if (key !in thisFrame) {
                val stale = activeLabels.remove(key)
                if (stale != null && stale.parent === panel) panel.removeView(stale)
            }
        }
    }

    private fun onTranslationReady(source: String, result: String?) {
        val root = rootInActiveWindow
        val targetForeground = root != null && root.packageName != null &&
            PDD.contentEquals(root.packageName)
        if (OverlayUpdatePolicy.shouldRedraw(
                targetForeground, engine?.enabled() == true,
                visibleChineseTexts.contains(source), !result.isNullOrBlank()
            )) scheduleRedraw()
    }

    private data class Target(val text: String, val bounds: Rect, val isReview: Boolean)

    private fun findText(root: AccessibilityNodeInfo, width: Int, height: Int): List<Target> {
        val list = ArrayList<Target>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val seen = HashSet<String>()
        queue.add(root)
        var scanned = 0
        while (queue.isNotEmpty() && scanned++ < 320 && list.size < 48) {
            val node = queue.removeFirst()
            if (!node.isVisibleToUser) continue
            val className = node.className
            val editable = node.isEditable || className?.toString()?.contains("EditText") == true
            if (editable || node.isPassword) continue // skip sensitive subtree
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty()) {
                val paragraph = ReviewTranslationPolicy.chunks(text) != null
                val regular = TranslationPolicy.shouldTranslate(text, false, false)
                if ((regular || paragraph) && !PaymentActionPolicy.isProtected(text)) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.intersect(0, 0, width, height) &&
                        rect.width() >= 24 && rect.height() >= 13 &&
                        rect.width() <= width * (if (paragraph) 1.0f else .96f) &&
                        (!paragraph || rect.height() >= dp(48f))) {
                        val id = rect.toShortString() + "::" + text
                        if (seen.add(id)) list.add(Target(text, rect, paragraph))
                    }
                }
            }
            val childCount = min(node.childCount, 80)
            for (index in 0 until childCount) {
                node.getChild(index)?.let(queue::add)
            }
        }
        return list
    }

    /** Touchable 48dp floating control, independent of the non-touchable text layer. */
    private fun showFloatingButton() {
        val window = windows
        val e = engine
        if (window == null || e == null ||
            !e.prefs().getBoolean(TranslationEngine.PREF_FLOAT_BUTTON, true)) {
            hideFloatingButton()
            return
        }
        if (floatingButton != null) {
            refreshFloatingButton()
            return
        }

        val button = TextView(this)
        button.text = "РУ"
        button.setTextColor(Color.WHITE)
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        button.setTypeface(null, Typeface.BOLD)
        button.gravity = Gravity.CENTER
        button.elevation = dp(10f)
        button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        val diameter = dp(48f).toInt()
        val displaySize = Point()
        window.defaultDisplay.getRealSize(displaySize)
        val params = WindowManager.LayoutParams(
            diameter, diameter, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = e.prefs().getInt(
            "float_x", displaySize.x - diameter - dp(12f).toInt()
        ).coerceIn(0, (displaySize.x - diameter).coerceAtLeast(0))
        val upper = dp(32f).toInt()
        val lower = (displaySize.y - diameter - dp(34f).toInt()).coerceAtLeast(upper)
        params.y = e.prefs().getInt("float_y", displaySize.y / 2).coerceIn(upper, lower)

        button.setOnClickListener {
            val enabled = !e.enabled()
            e.prefs().edit().putBoolean(TranslationEngine.PREF_ENABLED, enabled).apply()
            refreshFloatingButton()
            if (enabled) {
                if (!e.ready() && !e.englishReady()) {
                    Toast.makeText(
                        this, "Сначала скачай языковую модель в PDD · Русский",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                scheduleRedraw()
            } else {
                handler.removeCallbacks(redraw)
                removeOverlay()
            }
        }

        button.setOnTouchListener(object : View.OnTouchListener {
            private var startRawX = 0f
            private var startRawY = 0f
            private var startX = 0
            private var startY = 0
            private var dragged = false

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startRawX
                        val dy = event.rawY - startRawY
                        if (!dragged && (abs(dx) > dp(8f) || abs(dy) > dp(8f))) {
                            dragged = true
                        }
                        if (dragged && floatingButton === button) {
                            val bounds = Point()
                            window.defaultDisplay.getRealSize(bounds)
                            params.x = (startX + dx.roundToInt())
                                .coerceIn(0, (bounds.x - diameter).coerceAtLeast(0))
                            val bottom = (bounds.y - diameter - dp(34f).toInt())
                                .coerceAtLeast(upper)
                            params.y = (startY + dy.roundToInt()).coerceIn(upper, bottom)
                            try {
                                window.updateViewLayout(button, params)
                            } catch (_: IllegalArgumentException) { }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragged) {
                            val bounds = Point()
                            window.defaultDisplay.getRealSize(bounds)
                            val margin = dp(3f).toInt()
                            params.x = if (params.x + diameter / 2 < bounds.x / 2) margin
                                       else (bounds.x - diameter - margin).coerceAtLeast(margin)
                            try {
                                window.updateViewLayout(button, params)
                            } catch (_: IllegalArgumentException) { }
                            e.prefs().edit().putInt("float_x", params.x)
                                .putInt("float_y", params.y).apply()
                        } else {
                            view.performClick()
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                    else -> return false
                }
            }
        })

        try {
            window.addView(button, params)
            floatingButton = button
            floatingParams = params
            handler.removeCallbacks(foregroundGuard)
            handler.postDelayed(foregroundGuard, 1500L)
            refreshFloatingButton()
        } catch (error: RuntimeException) {
            floatingButton = null
            floatingParams = null
            Log.w(TAG, "Floating button could not be attached: " + error.javaClass.simpleName)
        }
    }

    private fun refreshFloatingButton() {
        val button = floatingButton ?: return
        val e = engine ?: return
        val enabled = e.enabled()
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.OVAL
        bg.setColor(if (enabled) Color.rgb(229, 55, 76) else Color.rgb(43, 47, 56))
        bg.setStroke(
            dp(1.4f).toInt(),
            if (enabled) Color.rgb(255, 141, 153) else Color.rgb(116, 123, 134)
        )
        button.background = InsetDrawable(bg, dp(6f).toInt())
        button.alpha = if (enabled) 1f else .82f
        button.contentDescription =
            if (enabled) "Выключить русский перевод Pinduoduo"
            else "Включить русский перевод Pinduoduo"
    }

    private fun hideFloatingButton() {
        handler.removeCallbacks(foregroundGuard)
        val button = floatingButton
        val window = windows
        if (button != null && window != null) {
            try {
                window.removeViewImmediate(button)
            } catch (_: RuntimeException) { }
        }
        floatingButton = null
        floatingParams = null
    }

    private fun ensureOverlay() {
        if (overlay != null || windows == null) return
        val window = windows ?: return
        try {
            val panel = FrameLayout(this)
            panel.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            panel.isFocusable = false
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 0
            params.y = 0
            panel.addOnLayoutChangeListener {
                    _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                if (right - left != oldRight - oldLeft ||
                    bottom - top != oldBottom - oldTop) {
                    scheduleRedraw()
                }
            }
            window.addView(panel, params)
            overlay = panel
        } catch (_: Exception) {
            overlay = null
        }
    }

    /** Reuse source-anchored captions without covering adjacent rows or actions. */
    private fun addLabel(
        key: String, translation: String, rect: Rect, origin: IntArray,
        maxX: Int, maxY: Int, occupied: MutableList<Rect>,
        paragraph: Boolean
    ): Boolean {
        val panel = overlay ?: return false
        val local = OverlayCoordinates.toLocalBounds(
            rect.left, rect.top, rect.right, rect.bottom, origin[0], origin[1]
        )
        val sourceX = local[0]
        val sourceY = local[1]
        val widthGoal = ceil(translation.length * dp(7.5f) + dp(16f)).toInt()
        val fit = (if (paragraph) {
            OverlayPlacement.fitParagraph(
                sourceX, sourceY, local[2], local[3], maxX, maxY
            )
        } else OverlayPlacement.fit(
            sourceX, sourceY, local[2], local[3],
            widthGoal, maxX, maxY, dp(8f).toInt()
        )) ?: return false
        if (fit[2] < dp(24f) || fit[3] < dp(17f)) return false
        // Reject ellipsized captions: an incomplete RU label is less useful
        // than leaving its original readable. Minimum 10sp is a11y-safe.
        if (!paragraph) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics
                )
            }
            if (paint.measureText(translation) > fit[2] - dp(12f)) return false
        }
        val placement = Rect(fit[0], fit[1], fit[0] + fit[2], fit[1] + fit[3])
        // Keep a small gap around neighboring captions; this is *not* a
        // location to cover nearby tappable elements or the shop footer.
        val spaced = Rect(placement).apply {
            inset(-dp(4f).toInt(), -dp(3f).toInt())
        }
        if (overlaps(spaced, occupied)) return false

        // Never cover an entire prose paragraph with a clipped translation.
        // Measure full multiline content and retain original on any overflow.
        // Scale translated paragraphs into their source rectangle when possible,
        // rather than leaving a large dark empty panel after a short translation.
        val paragraphFont = if (paragraph) (20 downTo 10).map { it.toFloat() }
            .firstOrNull { sp ->
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = android.util.TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics
                    )
                }
                val layout = StaticLayout.Builder.obtain(
                    translation, 0, translation.length, paint,
                    (fit[2] - dp(12f)).toInt().coerceAtLeast(1)
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).build()
                layout.height <= fit[3] - dp(6f)
            } ?: return false else 12f
        // A paragraph that cannot fit must not reserve space or hide other UI.
        occupied.add(placement)
        var label = activeLabels[key]
        if (label == null) {
            label = TextView(this)
            label.setTextColor(Color.WHITE)
            label.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            label.isSingleLine = !paragraph
            label.ellipsize = null
            label.includeFontPadding = false
            label.setPadding(
                dp(6f).toInt(), if (paragraph) dp(3f).toInt() else 0,
                dp(6f).toInt(), if (paragraph) dp(3f).toInt() else 0
            )
            label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            val bg = GradientDrawable()
            bg.setColor(Color.argb(252, 22, 25, 31))
            bg.cornerRadius = dp(5f)
            bg.setStroke(dp(1f).toInt(), Color.rgb(65, 74, 84))
            label.background = bg
            activeLabels[key] = label
            panel.addView(label)
        }
        if (translation != label.text.toString()) label.text = translation
        val sizeSp = paragraphFont
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (!paragraph) {
            val available = (fit[2] - dp(14f)).coerceAtLeast(1f)
            val measured = label.paint.measureText(translation)
            if (measured > available) {
                label.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    (sizeSp * available / measured).coerceAtLeast(10f)
                )
            }
        }
        val params = FrameLayout.LayoutParams(fit[2], fit[3])
        params.leftMargin = fit[0]
        params.topMargin = fit[1]
        label.layoutParams = params
        return true
    }

    private fun clearLabels() {
        activeLabels.clear()
        overlay?.removeAllViews()
    }

    private fun overlaps(candidate: Rect, others: List<Rect>): Boolean =
        others.any { Rect.intersects(candidate, it) }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun removeOverlay() {
        generation++
        visibleChineseTexts.clear()
        activeLabels.clear()
        val panel = overlay
        val window = windows
        if (panel != null && window != null) {
            try {
                window.removeViewImmediate(panel)
            } catch (_: Exception) { }
        }
        overlay = null
    }
}
