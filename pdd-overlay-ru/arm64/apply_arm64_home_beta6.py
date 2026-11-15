#!/usr/bin/env python3
"""Deliver beta6: ARM64-only native packaging and streamlined AMOLED Home screen.

The canonical app lives in source_bundle.base64. Reject mismatch and keep the
same app ID, prior signing convention, Accessibility constraints, PRO/Free state,
offline OCR and editable private dictionary. No new permissions or libraries.
"""
from __future__ import annotations
import base64
import io
from pathlib import Path
import zipfile

bundle = Path("pdd-overlay-ru/source_bundle.base64")
pkg = "pinduoduo_overlay_ru/"
src = pkg + "app/src/main/java/app/pinduoduo/overlayru/"
home = src + "ui/home/HomeScreen.kt"
gradle = pkg + "app/build.gradle"
feature = Path("pdd-overlay-ru/arm64/HomeScreen.kt")
with zipfile.ZipFile(io.BytesIO(base64.b64decode(bundle.read_text().strip()))) as archive:
    members = [m for m in archive.infolist() if not m.is_dir()]
    files = {m.filename: archive.read(m) for m in members}

def change_one(filename: str, old: str, new: str):
    body = files[filename].decode("utf-8")
    count = body.count(old)
    if count != 1:
        raise RuntimeError(f"{filename}: expected one match, got {count}: {old[:70]}")
    files[filename] = body.replace(old, new, 1).encode("utf-8")

change_one(gradle, "versionCode 15", "versionCode 16")
change_one(gradle, "versionName '0.2.0-beta.5'", "versionName '0.2.0-beta.6'")
change_one(
    gradle,
    "        minSdk 26\n        targetSdk 35",
    "        minSdk 26\n        targetSdk 35\n"
    "        // The distributed APK is exclusively for 64-bit ARM Android devices.\n"
    "        ndk { abiFilters 'arm64-v8a' }",
)

existing = files[home].decode("utf-8")
for contract in [
    "fun HomeScreen(", "onPrimaryAction: () -> Unit", "onCheckAgain: () -> Unit",
    "onAppInfo: () -> Unit", "onSettings: () -> Unit", "onLicense: () -> Unit",
    "onOcr: () -> Unit", "onDictionary: () -> Unit",
    "HomeBlocker.ACCESSIBILITY_DISABLED", "HomeBlocker.MODEL_MISSING"
]:
    assert contract in existing, contract

replacement = feature.read_bytes()
assert b"package app.pinduoduo.overlayru.ui.home\n" in replacement
assert replacement.count(b"fun HomeScreen(") == 1
for contract in [
    "onPrimaryAction", "onCheckAgain", "onAppInfo", "onSettings", "onLicense",
    "onOcr", "onDictionary", "state.action", "state.status", "state.blocker",
    "state.isPro", "state.floatingButtonEnabled",
    "HomeBlocker.ACCESSIBILITY_DISABLED", "HomeBlocker.MODEL_MISSING"
]:
    assert contract.encode() in replacement, contract
files[home] = replacement

g = files[gradle].decode()
assert "applicationId 'app.pinduoduo.overlayru'" in g
assert "PDD_RELEASE_KEYSTORE" in g
assert "LICENSE_API_BASE_URL" in g and "LICENSE_PUBLIC_KEY_B64" in g
assert "com.google.mlkit:text-recognition-chinese:16.0.1" in g
assert "com.google.mlkit:translate:17.0.3" in g
assert "ndk { abiFilters 'arm64-v8a' }" in g
assert "minSdk 26" in g
manifest = files[pkg + "app/src/main/AndroidManifest.xml"].decode()
assert "android:allowBackup=\"false\"" in manifest
assert "android.permission.READ_MEDIA_IMAGES" not in manifest
assert "android.permission.SYSTEM_ALERT_WINDOW" not in manifest
assert "android:name=\".ImageOcrActivity\"" in manifest
assert "android:name=\".GlossaryEditorActivity\"" in manifest
assert "LicenseTokenVerifier.kt" in [p.rsplit("/",1)[1] for p in map(Path,files) if p.name=="LicenseTokenVerifier.kt"]
assert "OcrImageOpener.kt" in [p.rsplit("/",1)[1] for p in map(Path,files) if p.name=="OcrImageOpener.kt"]

out = io.BytesIO()
with zipfile.ZipFile(out,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for member in members:
        z.writestr(member,files.pop(member.filename))
    for name,data in sorted(files.items()):
        z.writestr(name,data)
bundle.write_text(base64.b64encode(out.getvalue()).decode("ascii")+"\n")
print("PASS: PDD beta6 source bundle updated to ARM64-only and redesigned Home screen")
