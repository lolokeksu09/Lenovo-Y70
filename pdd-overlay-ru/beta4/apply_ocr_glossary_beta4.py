#!/usr/bin/env python3
"""Non-destructive beta4 migration: local glossary + explicit user-selected OCR.

Source ZIP is the canonical versioned Android project. This migration refuses
unexpected input instead of silently applying partial source rewrites.
"""
from __future__ import annotations
import base64
import io
from pathlib import Path
import zipfile

SOURCE = Path("pdd-overlay-ru/source_bundle.base64")
FEATURES = Path("pdd-overlay-ru/beta4")
BASE = "pinduoduo_overlay_ru/"
PKG = BASE + "app/src/main/java/app/pinduoduo/overlayru/"
TEST = BASE + "app/src/test/java/app/pinduoduo/overlayru/"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(SOURCE.read_text().strip()))) as archive:
    old_members = [member for member in archive.infolist() if not member.is_dir()]
    files = {member.filename: archive.read(member) for member in old_members}

def replace_exact(filename: str, before: str, after: str) -> None:
    old = files[filename].decode("utf-8")
    matches = old.count(before)
    if matches != 1:
        raise RuntimeError(f"{filename}: expected exactly one original anchor, got {matches}: {before[:70]!r}")
    files[filename] = old.replace(before, after, 1).encode("utf-8")

gradle = BASE + "app/build.gradle"
replace_exact(gradle, "versionCode 13", "versionCode 14")
replace_exact(gradle, "versionName '0.2.0-beta.3'", "versionName '0.2.0-beta.4'")
replace_exact(
    gradle,
    "    implementation 'com.google.mlkit:translate:17.0.3'",
    "    implementation 'com.google.mlkit:translate:17.0.3'\n"
    "    // Bundled OCR avoids a first-run Google Play Services download.\n"
    "    implementation 'com.google.mlkit:text-recognition-chinese:16.0.1'",
)
assert "applicationId 'app.pinduoduo.overlayru'" in files[gradle].decode()
assert "PDD_RELEASE_KEYSTORE" in files[gradle].decode()
assert "LICENSE_API_BASE_URL" in files[gradle].decode()

manifest = BASE + "app/src/main/AndroidManifest.xml"
replace_exact(
    manifest,
    '        <activity\n            android:name=".LicenseActivity"\n            android:exported="false" />',
    '        <activity\n            android:name=".LicenseActivity"\n            android:exported="false" />\n'
    '        <activity\n            android:name=".GlossaryEditorActivity"\n            android:exported="false" />\n'
    '        <activity\n            android:name=".ImageOcrActivity"\n            android:exported="false" />',
)

activity = PKG + "HomeActivity.kt"
replace_exact(
    activity,
    '                onLicense = { startActivity(Intent(this, LicenseActivity::class.java)) },',
    '                onLicense = { startActivity(Intent(this, LicenseActivity::class.java)) },\n'
    '                onOcr = { startActivity(Intent(this, ImageOcrActivity::class.java)) },\n'
    '                onDictionary = { startActivity(Intent(this, GlossaryEditorActivity::class.java)) },',
)

screen = PKG + "ui/home/HomeScreen.kt"
replace_exact(
    screen,
    '    onLicense: () -> Unit,\n)',
    '    onLicense: () -> Unit,\n'
    '    onOcr: () -> Unit,\n'
    '    onDictionary: () -> Unit,\n)',
)
replace_exact(
    screen,
    '''                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(52.dp)) {''',
    '''                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryTile(
                        title = "OCR товара",
                        subtitle = "Перевести фото",
                        modifier = Modifier.weight(1f),
                        onClick = onOcr
                    )
                    SecondaryTile(
                        title = "Мой словарь",
                        subtitle = "Исправить перевод",
                        modifier = Modifier.weight(1f),
                        onClick = onDictionary
                    )
                }
                Spacer(Modifier.height(18.dp))
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth().height(52.dp)) {''',
)

settings = PKG + "MainActivity.kt"
replace_exact(
    settings,
    '''        })
        spacer(root, 16)

        val controls = panel(root)''',
    '''        })
        spacer(root, 10)
        root.addView(actionButton("Перевод изображения (OCR)  →", false).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ImageOcrActivity::class.java))
            }
        })
        spacer(root, 10)
        root.addView(actionButton("Мой словарь переводов  →", false).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GlossaryEditorActivity::class.java))
            }
        })
        spacer(root, 16)

        val controls = panel(root)''',
)

engine = PKG + "TranslationEngine.kt"
replace_exact(
    engine,
    '''    fun translate(text: String?, onReady: Consumer<String?>?): String? {
        if (text == null) return null''',
    '''    fun translate(text: String?, onReady: Consumer<String?>?): String? =
        translateInternal(text, onReady, requireOverlayEnabled = true)

    /** Explicit image-picker OCR may translate while Accessibility overlay is disabled. */
    fun translateOcr(text: String?, onReady: Consumer<String?>?): String? =
        translateInternal(text, onReady, requireOverlayEnabled = false)

    private fun translateInternal(
        text: String?, onReady: Consumer<String?>?, requireOverlayEnabled: Boolean
    ): String? {
        if (text == null) return null''',
)
replace_exact(
    engine,
    '''        if (!TranslationPolicy.shouldTranslate(clean, false, false)) return null
        val sourceLanguage = TranslationPolicy.sourceLanguage(clean)''',
    '''        if (!TranslationPolicy.shouldTranslate(clean, false, false) ||
            PaymentActionPolicy.isProtected(clean)) return null
        // User edits have priority over glossary and ML Kit cache, without
        // granting edits to prices, OTPs or payment confirmation actions.
        CustomGlossary.lookup(context, clean)?.let { return it }
        val sourceLanguage = TranslationPolicy.sourceLanguage(clean)''',
)
replace_exact(
    engine,
    '''        if (!enabled() ||
            (sourceLanguage == TranslationPolicy.CHINESE && !chineseModelReady) ||''',
    '''        if ((requireOverlayEnabled && !enabled()) ||
            (sourceLanguage == TranslationPolicy.CHINESE && !chineseModelReady) ||''',
)

for kotlin in (
    "CustomGlossaryPolicy.kt", "CustomGlossary.kt", "GlossaryEditorActivity.kt",
    "ImageOcrActivity.kt", "OcrTextPolicy.kt",
):
    target = PKG + kotlin
    if target in files:
        raise RuntimeError(f"Unexpected pre-existing beta4 file: {target}")
    new = (FEATURES / kotlin).read_bytes()
    assert new.startswith(b"package app.pinduoduo.overlayru\n"), kotlin
    files[target] = new

for kotlin in ("CustomGlossaryPolicyTest.kt", "OcrTextPolicyTest.kt"):
    target = TEST + kotlin
    if target in files:
        raise RuntimeError("Unexpected pre-existing test: " + target)
    files[target] = (FEATURES / kotlin).read_bytes()

assert 'android.permission.READ_MEDIA_IMAGES' not in files[manifest].decode()
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in files[manifest].decode()
assert 'android:allowBackup="false"' in files[manifest].decode()
assert files[manifest].decode().count('android:exported="false"') >= 4
assert 'PDD.contentEquals(root.packageName)' in files[PKG+"PddAccessibilityService.kt"].decode()
assert "LicenseManager" not in files[engine].decode()
assert "LicenseManager" not in files[PKG+"PddAccessibilityService.kt"].decode()
assert "PaymentActionPolicy.isProtected(clean)" in files[engine].decode()
assert "CustomGlossary.lookup(context, clean)" in files[engine].decode()
assert "translateInternal(text, onReady, requireOverlayEnabled = false)" in files[engine].decode()

encoded = io.BytesIO()
with zipfile.ZipFile(encoded, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for entry in old_members:
        archive.writestr(entry, files.pop(entry.filename))
    for name, content in sorted(files.items()):
        archive.writestr(name, content)
SOURCE.write_text(base64.b64encode(encoded.getvalue()).decode("ascii") + "\n")
print("PASS beta4: manual image OCR + local editable dictionary, free translation preserved")
