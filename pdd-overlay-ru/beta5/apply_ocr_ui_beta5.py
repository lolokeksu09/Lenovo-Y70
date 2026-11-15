#!/usr/bin/env python3
"""PDD Overlay RU beta5: Android image decoder and OCR screen repair.

Operate on the *current* canonical beta4 Kotlin source bundle. Reject duplicate
migrations and preserve the app ID, signing configuration, preferences, other
screens, ML Kit offline consent, and PRO/license contract.
"""
from __future__ import annotations

import base64
import io
from pathlib import Path
import zipfile

source = Path("pdd-overlay-ru/source_bundle.base64")
beta5 = Path("pdd-overlay-ru/beta5")
root = "pinduoduo_overlay_ru/"
main = root + "app/src/main/java/app/pinduoduo/overlayru/"
test = root + "app/src/test/java/app/pinduoduo/overlayru/"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(source.read_text().strip()))) as archive:
    members = [item for item in archive.infolist() if not item.is_dir()]
    files = {item.filename: archive.read(item) for item in members}

def replace_one(path: str, original: str, updated: str) -> None:
    current = files[path].decode("utf-8")
    amount = current.count(original)
    if amount != 1:
        raise RuntimeError(f"Expected one original anchor in {path}, got {amount}: {original!r}")
    files[path] = current.replace(original, updated).encode("utf-8")

gradle = root + "app/build.gradle"
replace_one(gradle, "versionCode 14", "versionCode 15")
replace_one(gradle, "versionName '0.2.0-beta.4'", "versionName '0.2.0-beta.5'")
assert "applicationId 'app.pinduoduo.overlayru'" in files[gradle].decode()
assert "PDD_RELEASE_KEYSTORE" in files[gradle].decode()
assert "LICENSE_API_BASE_URL" in files[gradle].decode()
assert "com.google.mlkit:text-recognition-chinese:16.0.1" in files[gradle].decode()
assert "com.google.mlkit:translate:17.0.3" in files[gradle].decode()

old_activity = files[main + "ImageOcrActivity.kt"].decode("utf-8")
assert "BitmapFactory.decodeStream" in old_activity
assert "Не удалось открыть изображение. Выбери JPG или PNG." in old_activity
assert "ActivityResultContracts.GetContent()" in old_activity
assert "if (recognizerOpened) recognizer.close()" in old_activity
files[main + "ImageOcrActivity.kt"] = (beta5 / "ImageOcrActivity.kt").read_bytes()

new_files = (
    (main, "OcrImagePolicy.kt"),
    (main, "OcrImageOpener.kt"),
    (main, "OcrScreenV2.kt"),
    (test, "OcrImagePolicyTest.kt"),
)
for destination, filename in new_files:
    path = destination + filename
    if path in files:
        raise RuntimeError("Refusing duplicate beta5 source: " + path)
    content = (beta5 / filename).read_bytes()
    assert content.startswith(b"package app.pinduoduo.overlayru\n"), filename
    files[path] = content

manifest = files[root + "app/src/main/AndroidManifest.xml"].decode("utf-8")
assert 'android:name=".ImageOcrActivity"' in manifest
assert 'android:exported="false"' in manifest
assert 'android:allowBackup="false"' in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest

activity = files[main + "ImageOcrActivity.kt"].decode("utf-8")
opener = files[main + "OcrImageOpener.kt"].decode("utf-8")
screen = files[main + "OcrScreenV2.kt"].decode("utf-8")
assert "OcrImageOpener.open(this, uri)" in activity
assert "OcrScreenV2(" in activity
assert "recognizer.process(InputImage.fromBitmap(opened.full, 0))" in activity
assert "ImageDecoder.createSource(resolver, uri)" in opener
assert "BitmapFactory.decodeByteArray" in opener
assert "MAX_ENCODED_BYTES = 24 * 1024 * 1024" in files[main+"OcrImagePolicy.kt"].decode()
assert "Surface(modifier = Modifier.fillMaxSize(), color = bg)" in screen
assert "Перевод фото" in screen
assert "readMedia" not in activity
assert "LicenseManager" not in activity and "LicenseManager" not in opener
assert "CustomGlossary.lookup(context, clean)" in files[main+"TranslationEngine.kt"].decode()

repacked = io.BytesIO()
with zipfile.ZipFile(repacked, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as result:
    for item in members:
        result.writestr(item, files.pop(item.filename))
    for name, content in sorted(files.items()):
        result.writestr(name, content)
source.write_text(base64.b64encode(repacked.getvalue()).decode("ascii") + "\n")
print("PASS: beta5 URI-aware decoder and AMOLED OCR UI integrated into Kotlin source archive")
