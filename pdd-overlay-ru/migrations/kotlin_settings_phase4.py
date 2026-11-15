#!/usr/bin/env python3
"""Migrate Android advanced settings and stateless helpers from beta2 Java to Kotlin.

Preserve original app identity, signing, existing PRO implementation and release version.
The input is a versioned ZIP encoded in pdd-overlay-ru/source_bundle.base64.
"""
import base64
import io
from pathlib import Path
import zipfile

bundle = Path("pdd-overlay-ru/source_bundle.base64")
root = "pinduoduo_overlay_ru/"
src = root + "app/src/main/java/app/pinduoduo/overlayru/"
test = root + "app/src/test/java/app/pinduoduo/overlayru/"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(bundle.read_text().strip()))) as archive:
    old_infos = archive.infolist()
    files = {info.filename: archive.read(info) for info in old_infos if not info.is_dir()}

gradle = files[root + "app/build.gradle"].decode("utf-8")
assert "versionCode 12" in gradle and "versionName '0.2.0-beta.2'" in gradle
assert "id 'org.jetbrains.kotlin.plugin.compose'" in gradle
assert files[root + "app/src/main/AndroidManifest.xml"].count(b'MainActivity') == 1
assert src + "MainActivity.java" in files
assert src + "TranslationEngine.kt" in files
assert src + "PddAccessibilityService.kt" in files

kotlin = {
    "AccessibilityServiceIds": r'''package app.pinduoduo.overlayru

/** Exact ComponentName match, including Android's abbreviated service-class format. */
object AccessibilityServiceIds {
    @JvmStatic
    fun contains(enabled: String?, packageName: String, serviceClassName: String): Boolean {
        if (enabled.isNullOrEmpty()) return false
        for (entry in enabled.split(':')) {
            val parts = entry.trim().split('/')
            if (parts.size != 2 || parts[0] != packageName) continue
            val actualClass =
                if (parts[1].startsWith('.')) packageName + parts[1] else parts[1]
            if (actualClass == serviceClassName) return true
        }
        return false
    }
}
''',
    "DownloadAttemptGate": r'''package app.pinduoduo.overlayru

/**
 * Single flight for non-cancellable ML Kit model requests.
 * A delayed task is still in flight even after the advisory timeout.
 */
class DownloadAttemptGate {
    private var generation = 0
    private var active = false
    private var overdue = false

    fun begin(): Int {
        if (active) return -1
        active = true
        overdue = false
        return ++generation
    }

    fun markOverdue(attemptId: Int): Boolean {
        if (!active || generation != attemptId) return false
        overdue = true
        return true
    }

    fun finish(attemptId: Int): Boolean {
        if (!active || generation != attemptId) return false
        active = false
        overdue = false
        return true
    }

    fun inFlight(): Boolean = active
    fun overdue(): Boolean = overdue
}
''',
    "ModelAvailability": r'''package app.pinduoduo.overlayru

/** Chinese/English → Russian offline translation requires both language models. */
object ModelAvailability {
    @JvmStatic
    fun usable(sourceDownloaded: Boolean, russianDownloaded: Boolean): Boolean =
        sourceDownloaded && russianDownloaded
}
''',
    "ModelDownloadConditionsFactory": r'''package app.pinduoduo.overlayru

import com.google.mlkit.common.model.DownloadConditions

/** Respect the user's model-download network preference for every ML Kit request. */
object ModelDownloadConditionsFactory {
    @JvmStatic
    fun create(wifiOnly: Boolean): DownloadConditions =
        DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build()
}
''',
    "EnglishUiGlossary": r'''package app.pinduoduo.overlayru

import java.util.Locale

/** Deterministic exact-match English UI labels; unknown labels fall back to ML Kit. */
object EnglishUiGlossary {
    private val UI = mapOf(
        "settings" to "Настройки",
        "linked phone" to "Привязанный телефон",
        "account & security" to "Аккаунт и безопасность",
        "account and security" to "Аккаунт и безопасность",
        "buy now, pay later" to "Купить сейчас — оплатить позже",
        "region & language" to "Регион и язык",
        "region and language" to "Регион и язык",
        "media & calls" to "Медиа и звонки",
        "media and calls" to "Медиа и звонки",
        "notifications" to "Уведомления",
        "font size" to "Размер шрифта",
        "dark mode" to "Тёмная тема",
        "feedback" to "Обратная связь",
        "partnership" to "Партнёрство",
        "teen mode" to "Подростковый режим",
        "clear cache" to "Очистить кеш",
        "data exchange" to "Обмен данными",
        "information collection" to "Сбор информации"
    )

    @JvmStatic
    fun lookup(source: String?): String? =
        source?.trim()?.lowercase(Locale.ROOT)?.let(UI::get)
}
''',
    "Glossary": r'''package app.pinduoduo.overlayru

import android.content.Context
import org.json.JSONObject

/** Cached translation dictionary loaded only from the APK assets. */
object Glossary {
    private var entries: JSONObject? = null

    @JvmStatic
    @Synchronized
    fun lookup(context: Context, text: String): String? {
        var dictionary = entries
        if (dictionary == null) {
            dictionary = try {
                context.assets.open("glossary_zh_ru.json").bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
            } catch (_: Exception) {
                JSONObject()
            }
            entries = dictionary
        }
        return dictionary.optString(text.trim(), null)
    }
}
'''
}

tests = {
    "SettingsKotlinMigrationTest": r'''package app.pinduoduo.overlayru

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsKotlinMigrationTest {
    @Test fun accessibilityComponentMatchIsStrictAndSupportsAbbreviations() {
        val pkg = "app.pinduoduo.overlayru"
        val service = "$pkg.PddAccessibilityService"
        assertTrue(AccessibilityServiceIds.contains("$pkg/.PddAccessibilityService", pkg, service))
        assertTrue(AccessibilityServiceIds.contains("$pkg/$service", pkg, service))
        assertFalse(AccessibilityServiceIds.contains("other/$service", pkg, service))
        assertFalse(AccessibilityServiceIds.contains("$pkg/.PddAccessibilityServiceEvil", pkg, service))
        assertFalse(AccessibilityServiceIds.contains("$pkg/.OtherService", pkg, service))
        assertFalse(AccessibilityServiceIds.contains(null, pkg, service))
    }

    @Test fun repeatedRequestsAndLateCompletionsCannotResetTheGate() {
        val gate = DownloadAttemptGate()
        val first = gate.begin()
        assertTrue(first > 0)
        assertEquals(-1, gate.begin())
        assertFalse(gate.markOverdue(first + 1))
        assertTrue(gate.markOverdue(first))
        assertTrue(gate.overdue())
        assertTrue(gate.inFlight()) // UI timeout does not cancel the ML Kit Task.
        assertFalse(gate.finish(first + 1))
        assertTrue(gate.finish(first))
        assertFalse(gate.inFlight())
        assertFalse(gate.overdue())
        val second = gate.begin()
        assertEquals(first + 1, second)
        assertFalse(gate.finish(first)) // stale callback must not finish a newer request
        assertTrue(gate.inFlight())
        assertTrue(gate.finish(second))
    }

    @Test fun languagesNeedLocalSourceAndRussianModels() {
        assertTrue(ModelAvailability.usable(true, true))
        assertFalse(ModelAvailability.usable(false, true))
        assertFalse(ModelAvailability.usable(true, false))
        assertFalse(ModelAvailability.usable(false, false))
    }

    @Test fun englishExactMatchIsCaseInsensitiveOnly() {
        assertEquals("Настройки", EnglishUiGlossary.lookup(" SETTINGS "))
        assertEquals("Очистить кеш", EnglishUiGlossary.lookup("clear cache"))
        assertEquals("Аккаунт и безопасность", EnglishUiGlossary.lookup("account & security"))
        assertEquals(null, EnglishUiGlossary.lookup("new unknown menu"))
        assertEquals(null, EnglishUiGlossary.lookup(null))
    }
}
'''
}

# Java-to-Kotlin source files: remove the old bytecode and keep the manifest class names.
targets = ["MainActivity", *kotlin]
for name in targets:
    old = src + name + ".java"
    new = src + name + ".kt"
    if old not in files or new in files:
        raise RuntimeError(f"Unexpected migration source state: {name}")
    files.pop(old)
    if name == "MainActivity":
        data = Path("pdd-overlay-ru/migrations/settings/MainActivity.kt").read_text("utf-8")
    else:
        data = kotlin[name]
    if not data.startswith("package app.pinduoduo.overlayru\n"):
        raise RuntimeError("Unexpected source package: " + name)
    files[new] = data.encode("utf-8")

for name, data in tests.items():
    path = test + name + ".kt"
    if path in files:
        raise RuntimeError("Existing Kotlin regression test: " + name)
    files[path] = data.encode("utf-8")

# The original standalone shell checks were Java-only. Replace them with Android
# Kotlin/JUnit suites rather than running a stub or claiming zero discovered tests.
shell = root + "scripts/test_local.sh"
lines = files[shell].decode("utf-8").splitlines(keepends=True)
old_checks = set(kotlin)
for name in old_checks:
    for index, line in enumerate(lines):
        if line.startswith("javac ") and (name + ".java") in line:
            if index + 1 >= len(lines) or "java " not in lines[index + 1]:
                raise RuntimeError("Java shell test not paired for " + name)
            if name + "Test" not in lines[index + 1]:
                raise RuntimeError("Expected original test name for " + name)
            lines[index] = ""
            lines[index + 1] = ""
            break
    else:
        raise RuntimeError("Legacy Java shell regression command not found for " + name)
files[shell] = (
    "".join(lines) +
    "\n# Kotlin migration of Java helpers is covered by :app:testReleaseUnitTest.\n"
).encode("utf-8")

# Keep existing source-level safety assertions, updating ONLY Java syntax/patterns
# whose protected Kotlin behavior was preserved.
validator_path = root + "scripts/validate_sources.py"
validator = files[validator_path].decode("utf-8")
for before, after in {
    "MainActivity.java": "MainActivity.kt",
    "ModelDownloadConditionsFactory.java": "ModelDownloadConditionsFactory.kt",
    "AccessibilityServiceIds.java": "AccessibilityServiceIds.kt",
    "DownloadAttemptGate.java": "DownloadAttemptGate.kt",
    "EnglishUiGlossary.java": "EnglishUiGlossary.kt",
    "ModelAvailability.java": "ModelAvailability.kt",
    "Glossary.java": "Glossary.kt",
    "ComponentName.unflattenFromString(info.getId())":
        "ComponentName.unflattenFromString(info.id)",
    "mine.equals(listed)": "mine == listed",
    "moreBody.setVisibility(View.GONE)": "visibility = View.GONE",
}.items():
    if before not in validator:
        # Not every reference exists in each validator version.
        continue
    validator = validator.replace(before, after)
files[validator_path] = validator.encode("utf-8")

main = files[src + "MainActivity.kt"].decode("utf-8")
for invariant in (
    "Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES",
    "AccessibilityServiceIds.contains(",
    "PddAccessibilityService.isConnected()",
    "TranslationEngine.PREF_MODEL_ALLOWED",
    "TranslationEngine.PREF_EN_MODEL_ALLOWED",
    "TranslationEngine.PREF_WIFI_ONLY",
    "TranslationEngine.PREF_FLOAT_BUTTON",
    "TranslationEngine.PREF_ENABLED",
    "engine.prepare(",
    "engine.prepareEnglish(",
    "engine.inspectModels",
    "LicenseManager.get(this).state()",
    "WindowInsets.Type.statusBars()",
):
    if invariant not in main:
        raise RuntimeError("Kotlin settings lost required behavior: " + invariant)

assert src + "LicenseManager.java" in files
assert src + "LicenseTokenVerifier.java" in files
assert src + "LicenseActivity.java" in files
assert "applicationId 'app.pinduoduo.overlayru'" in gradle
assert "signingConfigs" in gradle

architecture = root + "ONE_MODULE_ARCHITECTURE.md"
files[architecture] += (
    "\n## Kotlin advanced settings migration (phase 4)\n\n"
    "- MainActivity is Kotlin and preserves the original imperative Android layout,"
    " Accessibility permission checks, manual model consent, settings persistence,"
    " model inspection and optional PRO navigation.\n"
    "- Java-free supporting helpers: AccessibilityServiceIds, DownloadAttemptGate,"
    " ModelAvailability, ModelDownloadConditionsFactory, EnglishUiGlossary, Glossary.\n"
    "- Existing Java license transport, token verifier and license screen are intentionally"
    " unchanged pending separate security-focused migration.\n"
    "- No applicationId, certificate configuration, versionCode or module changes.\n"
).encode("utf-8")

result = io.BytesIO()
with zipfile.ZipFile(result, "w") as out:
    previously_written = set()
    for info in old_infos:
        if info.is_dir():
            out.writestr(info, b"")
        elif info.filename in files:
            out.writestr(info, files[info.filename])
            previously_written.add(info.filename)
    for path in sorted(files.keys() - previously_written):
        out.writestr(path, files[path], compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(io.BytesIO(result.getvalue())) as check:
    assert check.testzip() is None
    assert len(check.namelist()) == len(set(check.namelist()))
    for name in targets:
        assert src + name + ".java" not in check.namelist()
        assert src + name + ".kt" in check.namelist()
    remain = sorted(x for x in check.namelist() if x.startswith(src) and x.endswith(".java"))
    assert remain == [
        src + "LicenseActivity.java",
        src + "LicenseManager.java",
        src + "LicenseTokenVerifier.java",
    ], remain

bundle.write_text(base64.b64encode(result.getvalue()).decode("ascii") + "\n")
print("Settings Kotlin migration: 7 Java files replaced, only 3 license Java files remain")
