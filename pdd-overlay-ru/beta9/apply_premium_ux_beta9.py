#!/usr/bin/env python3
"""PDD Overlay RU beta9 — focused premium UX correction, no business logic change.

Canonical zip stays inside source_bundle.base64. Inputs must be beta8 exactly:
new visual tokens, task-first Compose Home, OCR visual alignment, genuinely
editable local corrections, and de-prioritized PRO/advanced settings.
"""
import base64
import io
from pathlib import Path
import zipfile

source = Path("pdd-overlay-ru/source_bundle.base64")
feature = Path("pdd-overlay-ru/beta9")
root = "pinduoduo_overlay_ru/"
main = root + "app/src/main/java/app/pinduoduo/overlayru/"
home = main + "ui/home/HomeScreen.kt"
theme = main + "ui/design/PddDesign.kt"
gradle = root + "app/build.gradle"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(source.read_text().strip()))) as original:
    members = [x for x in original.infolist() if not x.is_dir()]
    files = {x.filename: original.read(x) for x in members}

def replace_once(path, old, new):
    value = files[path].decode("utf-8")
    count = value.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one anchor, got {count}: {old[:64]!r}")
    files[path] = value.replace(old, new, 1).encode("utf-8")

replace_once(gradle, "versionCode 18", "versionCode 19")
replace_once(gradle, "versionName '0.2.0-beta.8'", "versionName '0.2.0-beta.9'")

assert home in files and theme not in files
old_home = files[home].decode("utf-8")
assert '"Pinduoduo"' in old_home
assert '"на русском"' in old_home
assert 'onPrimaryAction: () -> Unit' in old_home
assert "onOcr: () -> Unit" in old_home
files[home] = (feature / "HomeScreen.kt").read_bytes()
files[theme] = (feature / "PddDesign.kt").read_bytes()

for name, old_anchor, new_anchor in [
    ("MainActivity.kt", '"ЛИЦЕНЗИЯ"', '"Настройки перевода"'),
    ("GlossaryEditorActivity.kt", 'Text("Удалить",', 'Text("Править",'),
    ("OcrScreenV2.kt", 'private val bg = Color(0xFF080A0E)',
     'private val bg = PddPalette.Background'),
]:
    target = main + name
    assert target in files
    assert old_anchor in files[target].decode("utf-8"), (name, "wrong baseline")
    new = (feature / name).read_bytes()
    assert new.startswith(b"package app.pinduoduo.overlayru\n"), name
    assert new_anchor.encode("utf-8") in new, (name, "missing improvement")
    files[target] = new

# Source-level UX contracts: all navigation and consent controls remain wired.
h = files[home].decode("utf-8")
for callback in (
    "onPrimaryAction", "onCheckAgain", "onAppInfo",
    "onSettings", "onLicense", "onOcr", "onDictionary"
):
    assert (callback + ": () -> Unit") in h, callback
assert "state.action" in h and "state.blocker" in h and "state.status" in h
assert "HomeBlocker.ACCESSIBILITY_DISABLED" in h
assert "HomeBlocker.MODEL_MISSING" in h
assert "state.floatingButtonEnabled" in h and "state.isPro" in h

settings = files[main + "MainActivity.kt"].decode("utf-8")
assert settings.index('val status = panel(root)') < settings.index('val licenseCard = panel(root)')
assert 'OCR доступен на главном экране:' in settings
assert 'OCR текста на картинках пока нет.' not in settings
assert 'VPN Material Xray' not in settings
for required in (
    "PREF_ENABLED", "PREF_FLOAT_BUTTON", "PREF_WIFI_ONLY",
    "PREF_MODEL_ALLOWED", "PREF_EN_MODEL_ALLOWED",
    "Settings.ACTION_ACCESSIBILITY_SETTINGS", "startStatusPolling",
    "LicenseActivity::class.java"
):
    assert required in settings, required
assert "scope.launch { listState.animateScrollToItem(1) }" in files[main+"GlossaryEditorActivity.kt"].decode()
assert "CustomGlossary.put(context, source, translation)" in files[main+"GlossaryEditorActivity.kt"].decode()
assert "CustomGlossary.delete(context, item.first)" in files[main+"GlossaryEditorActivity.kt"].decode()
assert "OcrImageViewer(" in files[main+"OcrScreenV2.kt"].decode()

g = files[gradle].decode()
assert "applicationId 'app.pinduoduo.overlayru'" in g
assert "ndk { abiFilters 'arm64-v8a' }" in g
assert "PDD_RELEASE_KEYSTORE" in g
assert "LICENSE_API_BASE_URL" in g and "LICENSE_PUBLIC_KEY_B64" in g
assert "com.google.mlkit:text-recognition-chinese:16.0.1" in g
assert "com.google.mlkit:translate:17.0.3" in g
manifest = files[root+"app/src/main/AndroidManifest.xml"].decode()
assert 'android:allowBackup="false"' in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest
for name in (
    "OcrImageOpener.kt", "OcrImageViewer.kt", "OcrPhotoViewport.kt",
    "CustomGlossary.kt", "LicenseTokenVerifier.kt"
):
    assert main + name in files

result = io.BytesIO()
with zipfile.ZipFile(result, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as dest:
    for item in members:
        dest.writestr(item, files.pop(item.filename))
    for name, content in sorted(files.items()):
        dest.writestr(name, content)
source.write_text(base64.b64encode(result.getvalue()).decode("ascii") + "\n")
print("PASS: beta9 premium UX Home, Settings, OCR, Glossary; all business invariants retained")
