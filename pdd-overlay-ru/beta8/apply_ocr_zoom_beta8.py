#!/usr/bin/env python3
"""PDD Overlay RU beta8: bounded gestures for photo OCR, ARM64-only release.

Strict migration of canonical beta7 Kotlin ZIP. Preserve application ID,
permissions, signer settings, local glossary, offline models and PRO logic.
"""
import base64
import io
from pathlib import Path
import zipfile

source = Path("pdd-overlay-ru/source_bundle.base64")
feature = Path("pdd-overlay-ru/beta8")
root = "pinduoduo_overlay_ru/"
main = root + "app/src/main/java/app/pinduoduo/overlayru/"
test = root + "app/src/test/java/app/pinduoduo/overlayru/"
gradle = root + "app/build.gradle"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(source.read_text().strip()))) as archive:
    members = [x for x in archive.infolist() if not x.is_dir()]
    files = {x.filename: archive.read(x) for x in members}

def change_exact(path, old, new):
    text = files[path].decode("utf-8")
    if text.count(old) != 1:
        raise RuntimeError(f"Expected exactly one occurrence: {path} {old[:70]!r}")
    files[path] = text.replace(old, new, 1).encode("utf-8")

change_exact(gradle, "versionCode 17", "versionCode 18")
change_exact(gradle, "versionName '0.2.0-beta.7'", "versionName '0.2.0-beta.8'")
old_view = files[main+"OcrImageViewer.kt"].decode("utf-8")
assert "remember(preview, lines, regions) { mutableIntStateOf(-1) }" in old_view
assert "detectTransformGestures" not in old_view
new_view = (feature/"OcrImageViewer.kt").read_bytes()
assert "remember(preview) { mutableIntStateOf(-1) }" in new_view.decode()
assert "detectTransformGestures(panZoomLock = true)" in new_view.decode()
assert "viewport.toImageCanvas(" in new_view.decode()
assert new_view.count("graphicsLayer {") == 2
assert "onDoubleTap" in new_view.decode()
assert "eligible[position + 1]" in new_view.decode()
files[main+"OcrImageViewer.kt"] = new_view

for destination, name in [
    (main,"OcrPhotoViewport.kt"),(test,"OcrPhotoViewportTest.kt")
]:
    target = destination + name
    assert target not in files, target
    content = (feature/name).read_bytes()
    assert content.startswith(b"package app.pinduoduo.overlayru\n")
    files[target] = content

grad = files[gradle].decode()
assert "applicationId 'app.pinduoduo.overlayru'" in grad
assert "ndk { abiFilters 'arm64-v8a' }" in grad
assert "PDD_RELEASE_KEYSTORE" in grad
assert "LICENSE_API_BASE_URL" in grad
assert "com.google.mlkit:text-recognition-chinese:16.0.1" in grad
assert "com.google.mlkit:translate:17.0.3" in grad
manifest = files[root+"app/src/main/AndroidManifest.xml"].decode()
assert 'android:allowBackup="false"' in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest
for name in ["OcrImageOpener.kt","OcrRegion.kt","CustomGlossary.kt","LicenseTokenVerifier.kt"]:
    assert main+name in files

out = io.BytesIO()
with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for member in members:
        archive.writestr(member,files.pop(member.filename))
    for name,data in sorted(files.items()):
        archive.writestr(name,data)
source.write_text(base64.b64encode(out.getvalue()).decode("ascii") + "\n")
print("PASS: ARM64 beta8 photo zoom/pan and stable selection Kotlin source repacked")
