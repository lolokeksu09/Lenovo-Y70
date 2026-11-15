#!/usr/bin/env python3
"""PDD Overlay RU beta7: user-selected OCR regions on photo (ARM64-only).

Repack current beta6 Kotlin source without modifying payment/Accessibility,
signing, license, dictionary or model-consent behavior.
"""
import base64
import io
from pathlib import Path
import zipfile

source=Path("pdd-overlay-ru/source_bundle.base64")
feature=Path("pdd-overlay-ru/beta7")
root="pinduoduo_overlay_ru/"
pkg=root+"app/src/main/java/app/pinduoduo/overlayru/"
tests=root+"app/src/test/java/app/pinduoduo/overlayru/"
gradle=root+"app/build.gradle"

with zipfile.ZipFile(io.BytesIO(base64.b64decode(source.read_text().strip()))) as archive:
    members=[x for x in archive.infolist() if not x.is_dir()]
    contents={x.filename:archive.read(x) for x in members}

def replace_one(path, old, new):
    s=contents[path].decode("utf-8")
    count=s.count(old)
    if count!=1:
        raise RuntimeError(f"Expected one anchor in {path}: count={count}, anchor={old[:80]}")
    contents[path]=s.replace(old,new,1).encode("utf-8")

replace_one(gradle, "versionCode 16", "versionCode 17")
replace_one(gradle, "versionName '0.2.0-beta.6'", "versionName '0.2.0-beta.7'")

for name in ["ImageOcrActivity.kt","OcrScreenV2.kt","OcrImagePolicy.kt"]:
    path=pkg+name
    previous=contents[path].decode()
    updated=(feature/name).read_bytes()
    assert updated.startswith(b"package app.pinduoduo.overlayru\n"),name
    if name=="ImageOcrActivity.kt":
        assert "OcrImageOpener.open(this, uri)" in previous
        assert "OcrRegion.fromPixels(" in updated.decode()
        assert "it[index] = it[index].copy(translation = value)" in updated.decode()
    if name=="OcrScreenV2.kt":
        assert "Surface(modifier = Modifier.fillMaxSize(), color = bg)" in previous
        assert "OcrImageViewer(" in updated.decode()
    if name=="OcrImagePolicy.kt":
        assert "PREVIEW_EDGE = 620" in previous
        assert "PREVIEW_EDGE = 1600" in updated.decode()
    contents[path]=updated

for name in ["OcrRegion.kt","OcrImageViewer.kt"]:
    path=pkg+name
    assert path not in contents, "Already patched: "+path
    contents[path]=(feature/name).read_bytes()
path=tests+"OcrRegionTest.kt"
assert path not in contents
contents[path]=(feature/"OcrRegionTest.kt").read_bytes()

grad=contents[gradle].decode()
assert "applicationId 'app.pinduoduo.overlayru'" in grad
assert "ndk { abiFilters 'arm64-v8a' }" in grad
assert "PDD_RELEASE_KEYSTORE" in grad
assert "LICENSE_API_BASE_URL" in grad and "LICENSE_PUBLIC_KEY_B64" in grad
assert "com.google.mlkit:text-recognition-chinese:16.0.1" in grad
assert "com.google.mlkit:translate:17.0.3" in grad

manifest=contents[root+"app/src/main/AndroidManifest.xml"].decode()
assert 'android:allowBackup="false"' in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest
assert 'android:name=".ImageOcrActivity"' in manifest
assert pkg+"CustomGlossary.kt" in contents
assert pkg+"TranslationQueueGate.kt" in contents
assert pkg+"LicenseTokenVerifier.kt" in contents

output=io.BytesIO()
with zipfile.ZipFile(output,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for item in members:
        archive.writestr(item,contents.pop(item.filename))
    for name,data in sorted(contents.items()):
        archive.writestr(name,data)
source.write_text(base64.b64encode(output.getvalue()).decode("ascii")+"\n")
print("PASS: beta7 user-selected OCR on-image translation, ARM64-only, source repacked")
