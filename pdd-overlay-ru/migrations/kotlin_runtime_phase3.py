#!/usr/bin/env python3
"""Phase 3: Kotlin ML Kit engine + accessibility service, preserving beta2 APK identity.

The archive is the versioned build input. Kotlin source files in migrations/runtime/
are reviewable inputs; CI repacks the archive only after compilation and regression tests.
"""
from __future__ import annotations

import base64
import io
from pathlib import Path
import zipfile

bundle = Path("pdd-overlay-ru/source_bundle.base64")
root = "pinduoduo_overlay_ru/"
source_dir = root + "app/src/main/java/app/pinduoduo/overlayru/"
with zipfile.ZipFile(io.BytesIO(base64.b64decode(bundle.read_text().strip()))) as source_zip:
    old_infos = source_zip.infolist()
    files = {entry.filename: source_zip.read(entry) for entry in old_infos if not entry.is_dir()}

gradle = files[root + "app/build.gradle"].decode("utf-8")
assert "versionCode 12" in gradle and "versionName '0.2.0-beta.2'" in gradle
assert "id 'org.jetbrains.kotlin.plugin.compose'" in gradle
assert root + "app/src/main/java/app/pinduoduo/overlayru/TranslationPolicy.kt" in files
assert root + "app/src/main/java/app/pinduoduo/overlayru/OverlayPlacement.kt" in files

for name in ("TranslationEngine", "PddAccessibilityService"):
    legacy = source_dir + name + ".java"
    migrated = source_dir + name + ".kt"
    assert legacy in files and migrated not in files, "Unexpected migration state for " + name
    kotlin_text = Path("pdd-overlay-ru/migrations/runtime/" + name + ".kt").read_text("utf-8")
    assert kotlin_text.startswith("package app.pinduoduo.overlayru\n")
    assert "@JvmStatic" in kotlin_text
    files.pop(legacy)
    files[migrated] = kotlin_text.encode("utf-8")

# Preserve the existing source guard, but check actual Kotlin lines, not removed Java.
validator_path = root + "scripts/validate_sources.py"
validator = files[validator_path].decode("utf-8")
replacements = {
    "PddAccessibilityService.java": "PddAccessibilityService.kt",
    "TranslationEngine.java": "TranslationEngine.kt",
    "ModelDownloadConditionsFactory.create(preparingWifiOnly)": "ModelDownloadConditionsFactory.create(modelPreparingWifiOnly)",
    "PDD.contentEquals(root.getPackageName())": "PDD.contentEquals(root.packageName)",
    "node.isPassword()": "node.isPassword",
    "node.isEditable()": "node.isEditable",
    "overlay.isLaidOut()": "panel.isLaidOut",
    "overlay.getLocationOnScreen(origin)": "panel.getLocationOnScreen(origin)",
    "activeLabels.get(key)": "activeLabels[key]",
    "if (!thisFrame.contains(key))": "if (key !in thisFrame)",
    "new InsetDrawable(bg, (int) dp(6))": "InsetDrawable(bg, dp(6f).toInt())",
}
for before, after in replacements.items():
    assert before in validator, "Validator drift: " + before
    validator = validator.replace(before, after)
files[validator_path] = validator.encode("utf-8")

# The migration must not weaken Android's privacy/surface restrictions or licensing.
engine = files[source_dir + "TranslationEngine.kt"].decode("utf-8")
service = files[source_dir + "PddAccessibilityService.kt"].decode("utf-8")
for required in (
    "getDownloadedModels(TranslateRemoteModel::class.java)",
    "downloadGate.inFlight()", "englishDownloadGate.inFlight()",
    "ModelDownloadConditionsFactory.create(modelPreparingWifiOnly)",
    "ModelDownloadConditionsFactory.create(englishModelPreparingWifiOnly)",
    "TranslationPolicy.shouldTranslate(clean, false, false)",
    "failure", "pending.remove(source)", "PddAccessibilityService.requestRedraw()",
):
    assert required in engine, "Missing engine invariant: " + required
for required in (
    "PDD.contentEquals(root.packageName)", "node.isPassword", "node.isEditable",
    "WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE",
    "WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY",
    "view.performClick()", "panel.getLocationOnScreen(origin)",
    "OverlayCoordinates.toLocalBounds", "OverlayPlacement.fit",
    "OverlayUpdatePolicy.shouldRedraw", "handler.postDelayed(foregroundGuard, 1500L)",
    "WeakReference(this)", "handler.removeCallbacks(redraw)", "hideFloatingButton()",
):
    assert required in service, "Missing accessibility invariant: " + required
assert "LicenseManager" not in service and "LicenseManager" not in engine

architecture = root + "ONE_MODULE_ARCHITECTURE.md"
files[architecture] += (
    "\n## Kotlin runtime migration (phase 3)\n\n"
    "- TranslationEngine and PddAccessibilityService now compile from Kotlin sources.\n"
    "- Both retain their Java-callable public names, methods and PREF_* constants.\n"
    "- ML Kit model downloads remain explicit and single-flight, with timeout and late callbacks.\n"
    "- Accessibility translation remains Pinduoduo-only; the text layer is non-touchable.\n"
    "- Existing MainActivity, license classes, signer, versionCode and :app build are unchanged.\n"
).encode("utf-8")

output = io.BytesIO()
with zipfile.ZipFile(output, "w") as dest:
    previous = set()
    for entry in old_infos:
        if entry.is_dir():
            dest.writestr(entry, b"")
        elif entry.filename in files:
            dest.writestr(entry, files[entry.filename])
            previous.add(entry.filename)
    for name in sorted(files.keys() - previous):
        dest.writestr(name, files[name], compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(io.BytesIO(output.getvalue())) as verified:
    assert verified.testzip() is None
    assert len(set(verified.namelist())) == len(verified.namelist())
    for name in ("TranslationEngine", "PddAccessibilityService"):
        assert source_dir + name + ".java" not in verified.namelist()
        assert source_dir + name + ".kt" in verified.namelist()

bundle.write_text(base64.b64encode(output.getvalue()).decode("ascii") + "\n")
print("Kotlin runtime phase 3: engine and accessibility service migrated; version/signing unchanged")
