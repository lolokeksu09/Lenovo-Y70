#!/usr/bin/env python3
"""Migrate isolated Java translation/overlay policies to Kotlin without changing the APK identity."""
import base64
import io
import zipfile
from pathlib import Path

bundle = Path("pdd-overlay-ru/source_bundle.base64")
root = "pinduoduo_overlay_ru/"
src = root + "app/src/main/java/app/pinduoduo/overlayru/"
test = root + "app/src/test/java/app/pinduoduo/overlayru/"
with zipfile.ZipFile(io.BytesIO(base64.b64decode(bundle.read_text().strip()))) as archive:
    original_infos = archive.infolist()
    files = {i.filename: archive.read(i) for i in original_infos if not i.is_dir()}

gradle = files[root + "app/build.gradle"].decode("utf-8")
assert "versionName '0.2.0-beta.2'" in gradle, "Expected existing beta2 source"
assert "id 'org.jetbrains.kotlin.plugin.compose'" in gradle
assert "include ':app'" in files[root + "settings.gradle"].decode("utf-8") or "include(':app')" in files[root + "settings.gradle"].decode("utf-8")

kotlin = {
    "TranslationPolicy": r'''package app.pinduoduo.overlayru

import java.util.Locale
import java.util.regex.Pattern

/** Local UI-only translation guard; never forwards user content to a server. */
object TranslationPolicy {
    const val NONE = 0
    const val CHINESE = 1
    const val ENGLISH = 2

    private val DIGITS = Pattern.compile("""[+\d\s()\-]{7,}""")
    private val TOKEN = Pattern.compile("""(?i).*\b(?:https?://|www\.|@|\w+@\w+\.)\S*.*""")
    private val LATIN_WORD = Pattern.compile("[A-Za-z]{2,}")
    private val AMOUNT = Pattern.compile(""".*\d+\s*元.*""")
    private val OTP = Pattern.compile(".*[0-9]{4,8}.*")
    private val SINGLE_WORD_UI = setOf(
        "search", "home", "cart", "orders", "order", "profile", "login",
        "logout", "pay", "settings", "coupons", "discount", "delivery",
        "shipping", "confirm", "cancel", "save", "continue", "back",
        "next", "address", "support", "help", "checkout", "free", "sale",
        "buy", "view", "more", "details", "filter", "sort", "close",
        "open", "track", "tracking", "pending", "received", "total",
        "notifications", "feedback", "partnership"
    )

    @JvmStatic
    fun containsChinese(value: String?): Boolean {
        if (value == null) return false
        return value.any { it in '\u3400'..'\u9FFF' || it in '\uF900'..'\uFAFF' }
    }

    /** Conservative UI label routing, not generic language detection. */
    @JvmStatic
    fun sourceLanguage(value: String?): Int {
        if (value == null) return NONE
        if (containsChinese(value)) return CHINESE
        if (value.any { it.code > 127 }) return NONE

        val matcher = LATIN_WORD.matcher(value)
        var count = 0
        var only: String? = null
        while (matcher.find()) {
            count++
            only = matcher.group().lowercase(Locale.ROOT)
        }
        return if (count >= 2 || (count == 1 && only in SINGLE_WORD_UI)) ENGLISH else NONE
    }

    @JvmStatic
    fun shouldTranslate(text: String?, editable: Boolean, password: Boolean): Boolean {
        if (editable || password || text == null) return false
        val s = text.trim()
        if (s.isEmpty() || s.length > 180 || sourceLanguage(s) == NONE) return false
        // Do not obscure prices, order totals, URLs, contacts or one-time codes.
        if (s.contains('¥') || s.contains('￥') || s.contains('₽') ||
            s.contains('\u0024') || AMOUNT.matcher(s).matches()) return false
        if (DIGITS.matcher(s).matches() || TOKEN.matcher(s).matches()) return false
        if (OTP.matcher(s).matches()) return false
        return true
    }
}
''',
    "OverlayPlacement": r'''package app.pinduoduo.overlayru

import kotlin.math.ceil

/** Source-anchored caption geometry. Never moves a caption into an adjacent row. */
object OverlayPlacement {
    /** Returns left, top, width, height, or null if the source is not visible. */
    @JvmStatic
    fun fit(
        x: Int, y: Int, sourceWidth: Int, sourceHeight: Int,
        desiredWidth: Int, viewportWidth: Int, viewportHeight: Int, edgeMargin: Int
    ): IntArray? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidth <= 0 ||
            viewportHeight <= 0 || x >= viewportWidth || y >= viewportHeight ||
            x + sourceWidth <= 0 || y + sourceHeight <= 0) return null

        val left = maxOf(0, x)
        val top = maxOf(0, y)
        val onLeft = x < viewportWidth * 0.70f
        val right = if (onLeft) minOf(viewportWidth - edgeMargin, (viewportWidth * 0.83f).toInt())
                    else viewportWidth - edgeMargin
        val allowedWidth = maxOf(0, right - left)
        val expandedWidth = ceil((sourceWidth * (if (onLeft) 1.55f else 1.10f)).toDouble()).toInt()
        val width = minOf(allowedWidth,
            maxOf(sourceWidth, minOf(maxOf(sourceWidth, desiredWidth), expandedWidth)))
        val height = minOf(sourceHeight, viewportHeight - top)
        if (width <= 0 || height <= 0) return null
        return intArrayOf(left, top, width, height)
    }
}
''',
    "OverlayCoordinates": r'''package app.pinduoduo.overlayru

/** Screen coordinates of Accessibility nodes to overlay-local pixels. */
object OverlayCoordinates {
    @JvmStatic
    fun toLocalBounds(
        left: Int, top: Int, right: Int, bottom: Int,
        overlayScreenX: Int, overlayScreenY: Int
    ): IntArray = intArrayOf(
        left - overlayScreenX, top - overlayScreenY,
        maxOf(0, right - left), maxOf(0, bottom - top)
    )
}
''',
    "OverlayUpdatePolicy": r'''package app.pinduoduo.overlayru

/** Late ML Kit results may redraw only while their source is still visible. */
object OverlayUpdatePolicy {
    @JvmStatic
    fun shouldRedraw(
        targetForeground: Boolean, enabled: Boolean,
        sourceStillVisible: Boolean, validTranslation: Boolean
    ): Boolean = targetForeground && enabled && sourceStillVisible && validTranslation
}
'''
}

tests = {
    "TranslationPolicyKotlinTest": r'''package app.pinduoduo.overlayru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPolicyKotlinTest {
    @Test fun routesChineseAndEnglishWithoutChangingRussianOrBrands() {
        assertEquals(TranslationPolicy.CHINESE, TranslationPolicy.sourceLanguage("去购物"))
        assertEquals(TranslationPolicy.ENGLISH, TranslationPolicy.sourceLanguage("Sign in"))
        assertEquals(TranslationPolicy.ENGLISH, TranslationPolicy.sourceLanguage("Search"))
        assertEquals(TranslationPolicy.NONE, TranslationPolicy.sourceLanguage("Подписка"))
        assertEquals(TranslationPolicy.NONE, TranslationPolicy.sourceLanguage("Pinduoduo"))
        assertEquals(TranslationPolicy.NONE, TranslationPolicy.sourceLanguage(null))
        assertTrue(TranslationPolicy.containsChinese("包邮"))
        assertFalse(TranslationPolicy.containsChinese("Подписка"))
    }

    @Test fun preservesExistingShoppingAndPrivacyGuards() {
        assertTrue(TranslationPolicy.shouldTranslate("查看物流", false, false))
        assertTrue(TranslationPolicy.shouldTranslate("  待付款  ", false, false))
        assertTrue(TranslationPolicy.shouldTranslate("English text", false, false))
        assertTrue(TranslationPolicy.shouldTranslate("Search", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Pinduoduo", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Order total 4356", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("123456", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("¥99 包邮", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("￥19 包邮", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("付款密码", false, true))
        assertFalse(TranslationPolicy.shouldTranslate("验证码", true, false))
        assertFalse(TranslationPolicy.shouldTranslate("验证码 453278", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("订单 https://example.com", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("a".repeat(200) + "你好", false, false))
        assertFalse(TranslationPolicy.shouldTranslate(null, false, false))
    }

    @Test fun rejectsLatinContactAndPriceWithoutObscuringMoney() {
        assertFalse(TranslationPolicy.shouldTranslate("Email admin@example.com", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("订单 19 元", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Цена 100 ₽", false, false))
        assertFalse(TranslationPolicy.shouldTranslate("Pay 100 $", false, false))
    }
}
''',
    "OverlayPlacementKotlinTest": r'''package app.pinduoduo.overlayru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementKotlinTest {
    @Test fun doesNotOverlapAdjacentRowsOrTrailingControls() {
        val first = OverlayPlacement.fit(64, 210, 170, 40, 360, 830, 1850, 8)
        assertNotNull(first)
        first!!
        assertEquals(64, first[0])
        assertEquals(210, first[1])
        assertTrue(first[2] > 170 && first[0] + first[2] <= 690)
        assertTrue(first[3] <= 40)

        val right = OverlayPlacement.fit(710, 350, 110, 36, 360, 830, 1850, 8)
        assertNotNull(right)
        assertTrue(right!![0] + right[2] <= 822)

        val bottom = OverlayPlacement.fit(64, 1830, 160, 36, 260, 830, 1850, 8)
        assertNotNull(bottom)
        assertTrue(bottom!![1] + bottom[3] <= 1850)
    }

    @Test fun ignoresInvalidAndOffscreenNodes() {
        assertNull(OverlayPlacement.fit(100, 100, 0, 30, 200, 830, 1850, 8))
        assertNull(OverlayPlacement.fit(900, 100, 60, 30, 200, 830, 1850, 8))
        assertNull(OverlayPlacement.fit(100, 1850, 60, 30, 200, 830, 1850, 8))
    }
}
''',
    "OverlayCoordinatesKotlinTest": r'''package app.pinduoduo.overlayru

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayCoordinatesKotlinTest {
    @Test fun subtractsScreenInsetsExactlyOnce() {
        val rect = OverlayCoordinates.toLocalBounds(64, 118, 270, 146, 0, 80)
        assertArrayEquals(intArrayOf(64, 38, 206, 28), rect)
        assertEquals(118, rect[1] + 80)
        assertArrayEquals(intArrayOf(88, 350, 190, 36),
            OverlayCoordinates.toLocalBounds(120, 350, 310, 386, 32, 0))
        assertArrayEquals(intArrayOf(20, 40, 10, 5),
            OverlayCoordinates.toLocalBounds(20, 40, 30, 45, 0, 0))
    }
    @Test fun malformedBoundsCannotProduceNegativeSize() {
        assertArrayEquals(intArrayOf(10, 10, 0, 0),
            OverlayCoordinates.toLocalBounds(10, 10, 0, 0, 0, 0))
    }
}
''',
    "OverlayUpdatePolicyKotlinTest": r'''package app.pinduoduo.overlayru

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayUpdatePolicyKotlinTest {
    @Test fun acceptsOnlyCurrentVisibleValidOverlay() {
        assertTrue(OverlayUpdatePolicy.shouldRedraw(true, true, true, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(true, true, false, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(false, true, true, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(true, false, true, true))
        assertFalse(OverlayUpdatePolicy.shouldRedraw(true, true, true, false))
    }
}
'''
}

migrated = tuple(kotlin)
for name, body in kotlin.items():
    old = src + name + ".java"
    new = src + name + ".kt"
    if old not in files or new in files:
        raise RuntimeError("Unexpected migration state for " + name)
    files.pop(old)
    files[new] = body.encode("utf-8")

for name, body in tests.items():
    target = test + name + ".kt"
    if target in files:
        raise RuntimeError("Test already exists: " + target)
    files[target] = body.encode("utf-8")

for name in migrated:
    old = root + "tests/" + name + "Test.java"
    if old not in files:
        raise RuntimeError("Legacy test missing: " + old)
    files.pop(old)

script_key = root + "scripts/test_local.sh"
lines = files[script_key].decode("utf-8").splitlines(keepends=True)
for name in migrated:
    matches = [i for i, line in enumerate(lines)
               if line.startswith("javac ") and (name + ".java") in line]
    if len(matches) != 1:
        raise RuntimeError("Expected exactly one javac test command for " + name)
    i = matches[0]
    if i + 1 >= len(lines) or not lines[i + 1].startswith("java ") or (name + "Test") not in lines[i + 1]:
        raise RuntimeError("Expected paired Java test command for " + name)
    del lines[i:i + 2]
files[script_key] = ("".join(lines) +
    "\n# Four migrated Kotlin test suites run under Gradle :app:testReleaseUnitTest.\n").encode("utf-8")

architecture = root + "ONE_MODULE_ARCHITECTURE.md"
files[architecture] += (
    "\n## Kotlin core migration\n\n"
    "- TranslationPolicy, OverlayPlacement, OverlayCoordinates and OverlayUpdatePolicy "
    "are native Kotlin objects with Java-compatible @JvmStatic entry points.\n"
    "- Their Java main-method regressions are replaced with Kotlin/JUnit tests.\n"
    "- MainActivity, TranslationEngine, PddAccessibilityService and licensing "
    "remain unchanged until their behavior is tested independently.\n"
    "- Android applicationId, versionCode 12, certificate configuration and "
    "the single Gradle module remain unchanged.\n"
).encode("utf-8")

with zipfile.ZipFile(io.BytesIO(), "w") as _:  # verify ZIP support before replacing bundle
    pass
output = io.BytesIO()
with zipfile.ZipFile(output, "w") as dst:
    present = set()
    for info in original_infos:
        if info.is_dir():
            dst.writestr(info, b"")
        elif info.filename in files:
            dst.writestr(info, files[info.filename])
            present.add(info.filename)
    for path in sorted(files.keys() - present):
        dst.writestr(path, files[path], compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(io.BytesIO(output.getvalue())) as check:
    assert check.testzip() is None
    for name in migrated:
        assert src + name + ".java" not in check.namelist()
        assert src + name + ".kt" in check.namelist()
    assert len(check.namelist()) == len(set(check.namelist()))
bundle.write_text(base64.b64encode(output.getvalue()).decode("ascii") + "\n")
print("Kotlin core migration: 4 Java classes and their Java harness tests replaced; app identity unchanged")
