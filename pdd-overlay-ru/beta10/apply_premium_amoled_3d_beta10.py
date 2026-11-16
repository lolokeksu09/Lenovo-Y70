#!/usr/bin/env python3
"""PDD Overlay RU beta10 — design reference implemented as real Android UI.

Canonical source starts from beta9; update only Android UI, signing-safe
version and the two source validators that referenced obsolete View layouts.
Preserve all models, local glossary storage, PRO crypto and PDD-only overlay.
"""
import base64
import io
from pathlib import Path
import zipfile

bundle = Path("pdd-overlay-ru/source_bundle.base64")
feature = Path("pdd-overlay-ru/beta10")
root = "pinduoduo_overlay_ru/"
main = root + "app/src/main/java/app/pinduoduo/overlayru/"
design = main + "ui/design/"
gradle = root + "app/build.gradle"
tests = root + "scripts/validate_sources.py"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(bundle.read_text().strip()))) as z:
    original = [i for i in z.infolist() if not i.is_dir()]
    files = {i.filename: z.read(i) for i in original}

def once(path,old,new):
    content=files[path].decode("utf-8")
    n=content.count(old)
    if n!=1: raise RuntimeError(f"Expected one match {path}: {n} {old[:65]!r}")
    files[path]=content.replace(old,new,1).encode("utf-8")

once(gradle,"versionCode 19","versionCode 20")
once(gradle,"versionName '0.2.0-beta.9'","versionName '0.2.0-beta.10'")

# Replace Home and canonical app palette; reuse the shipped OCR UI and engine.
for name,path in [
    ("HomeScreen.kt", main+"ui/home/HomeScreen.kt"),
    ("PddDesign.kt",design+"PddDesign.kt"),
    ("MainActivity.kt",main+"MainActivity.kt"),
    ("GlossaryEditorActivity.kt",main+"GlossaryEditorActivity.kt"),
    ("ImageOcrActivity.kt",main+"ImageOcrActivity.kt"),
]:
    assert path in files,("expected baseline source",path)
    current=(feature/name).read_bytes()
    assert b"package app.pinduoduo.overlayru" in current[:120],name
    files[path]=current

for name,path in [
    ("PddNavigation.kt",design+"PddNavigation.kt"),
    ("PddBagArt.kt",design+"PddBagArt.kt"),
    ("PremiumSettingsScreen.kt",main+"PremiumSettingsScreen.kt"),
]:
    assert path not in files,("duplicate new source",path)
    files[path]=(feature/name).read_bytes()

# The test contracts that used to require specific legacy Android View chrome
# must instead verify the new real Compose UI and permissions. Security tests
# (package restrictions, license boundaries, model-consent, payment exclusions)
# stay intact.
old_checks=files[tests].decode("utf-8")
assert "assert 'ColorStateList' in activity" in old_checks
assert "assert 'visibility = View.GONE' in activity" in old_checks
assert "assert 'Скачать модель английский' in activity" in old_checks
assert "assert 'Настройки перевода' in activity and 'OCR доступен на главном экране:' in activity" in old_checks
settings_ui="settings_ui=(a/'java/app/pinduoduo/overlayru/PremiumSettingsScreen.kt').read_text()\n"
old_checks=old_checks.replace("activity=(a/'java/app/pinduoduo/overlayru/MainActivity.kt').read_text()\n",
                              "activity=(a/'java/app/pinduoduo/overlayru/MainActivity.kt').read_text()\n"+settings_ui,1)
old_checks=old_checks.replace("assert 'Скачать модель английский' in activity",
                              "assert 'englishPreparing' in activity and 'ModelItem(' in settings_ui",1)
old_checks=old_checks.replace(
    "assert 'putBoolean(TranslationEngine.PREF_WIFI_ONLY, checked)' in activity",
    "assert 'putBoolean(TranslationEngine.PREF_WIFI_ONLY,checked)' in activity",1
)
old_checks=old_checks.replace("assert 'ColorStateList' in activity",
                              "assert 'PddFrame(PddTab.SETTINGS' in activity",1)
old_checks=old_checks.replace("assert 'visibility = View.GONE' in activity",
                              "assert 'diagnosticsExpanded' in settings_ui",1)
old_checks=old_checks.replace(
    "assert 'Настройки перевода' in activity and 'OCR доступен на главном экране:' in activity",
    "assert 'Настройки перевода' in settings_ui and 'Открыть настройки службы' in settings_ui",1
)
files[tests]=old_checks.encode("utf-8")

# Strict product guarantees, including two independent ML Kit consents.
g=files[gradle].decode("utf-8")
assert "applicationId 'app.pinduoduo.overlayru'" in g
assert "ndk { abiFilters 'arm64-v8a' }" in g
assert "PDD_RELEASE_KEYSTORE" in g
assert "LICENSE_API_BASE_URL" in g
assert "com.google.mlkit:translate:17.0.3" in g
assert "com.google.mlkit:text-recognition-chinese:16.0.1" in g
manifest=files[root+"app/src/main/AndroidManifest.xml"].decode("utf-8")
assert 'android:allowBackup="false"' in manifest
assert 'android:exported="false"' in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest
for name in ("OcrImageOpener.kt","OcrPhotoViewport.kt","PaymentActionPolicy.kt",
             "LicenseTokenVerifier.kt","CustomGlossary.kt"):
    assert main+name in files,name

settings=files[main+"MainActivity.kt"].decode("utf-8")
for term in (
    "Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES",
    "AccessibilityServiceIds.contains(", "PddAccessibilityService.isConnected()",
    "ComponentName.unflattenFromString(info.id)",
    "TranslationEngine.PREF_ENABLED","TranslationEngine.PREF_FLOAT_BUTTON",
    "TranslationEngine.PREF_WIFI_ONLY","TranslationEngine.PREF_MODEL_ALLOWED",
    "TranslationEngine.PREF_EN_MODEL_ALLOWED",
    "engine.prepare(", "engine.prepareEnglish(",
    "LicenseActivity::class.java",
):
    assert term in settings,term
nav=files[design+"PddNavigation.kt"].decode("utf-8")
for term in ("PddTab.HOME","PddTab.PHOTO","PddTab.DICTIONARY","PddTab.SETTINGS",
             "NavigationBarItem(","PddFrame("):
    assert term in nav,term
assert "PddBottomBar(PddTab.HOME)" in files[main+"ui/home/HomeScreen.kt"].decode("utf-8")
assert "PddFrame(PddTab.PHOTO" in files[main+"ImageOcrActivity.kt"].decode("utf-8")
assert "PddFrame(PddTab.DICTIONARY" in files[main+"GlossaryEditorActivity.kt"].decode("utf-8")
assert "PddFrame(PddTab.SETTINGS" in settings

result=io.BytesIO()
with zipfile.ZipFile(result,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for entry in original:
        z.writestr(entry,files.pop(entry.filename))
    for name,data in sorted(files.items()):
        z.writestr(name,data)
bundle.write_text(base64.b64encode(result.getvalue()).decode("ascii")+"\n")
print("PASS: beta10 Premium AMOLED 3D integrated across 4 real Android tabs")
