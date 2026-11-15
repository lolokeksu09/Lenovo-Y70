#!/usr/bin/env python3
"""Apply targeted runtime hardening to the pinned Kotlin PDD Overlay RU source ZIP.

Changes only the payment-label visibility guard, ML Kit request scheduling and
beta version; maintains package name, license token trust, manifest, user prefs
and PRO/Free separation. The resulting source is committed only after CI tests.
"""
from __future__ import annotations

import base64
import io
from pathlib import Path
import zipfile

BUNDLE = Path("pdd-overlay-ru/source_bundle.base64")
BASE = "pinduoduo_overlay_ru/"
MAIN = BASE + "app/src/main/java/app/pinduoduo/overlayru/"
TEST = BASE + "app/src/test/java/app/pinduoduo/overlayru/"
FEATURE = Path("pdd-overlay-ru/hardening")

with zipfile.ZipFile(io.BytesIO(base64.b64decode(BUNDLE.read_text().strip()))) as original:
    members = [entry for entry in original.infolist() if not entry.is_dir()]
    files = {entry.filename: original.read(entry) for entry in members}

def patch(name: str, old: str, new: str) -> None:
    content = files[name].decode("utf-8")
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{name}: expected one match, found {count}: {old[:80]}")
    files[name] = content.replace(old, new, 1).encode("utf-8")

# A new release version allows an install over the earlier beta2 *only when the
# same signing certificate was used*. The signer is not part of the source ZIP.
gradle = BASE + "app/build.gradle"
patch(gradle, "versionCode 12", "versionCode 13")
patch(gradle, "versionName '0.2.0-beta.2'", "versionName '0.2.0-beta.3'")
assert "applicationId 'app.pinduoduo.overlayru'" in files[gradle].decode()
assert "PDD_RELEASE_KEYSTORE" in files[gradle].decode()

service = MAIN + "PddAccessibilityService.kt"
engine = MAIN + "TranslationEngine.kt"
assert service in files and engine in files

patch(
    service,
    "if (text != null && TranslationPolicy.shouldTranslate(text.toString(), false, false)) {",
    "if (text != null && TranslationPolicy.shouldTranslate(text.toString(), false, false) &&\n"
    "                !PaymentActionPolicy.isProtected(text.toString())) {",
)
patch(
    engine,
    "    private val pending = HashMap<String, MutableList<Consumer<String?>>>()",
    "    private val pending = TranslationQueueGate()",
)
patch(
    engine,
    "        if (!TranslationPolicy.shouldTranslate(clean, false, false)) return null",
    "        if (!TranslationPolicy.shouldTranslate(clean, false, false) ||\n"
    "            PaymentActionPolicy.isProtected(clean)) return null",
)
patch(
    engine,
    """        val alreadyPending = pending[key]
        if (alreadyPending != null) {
            if (onReady != null && alreadyPending.size < 4) alreadyPending.add(onReady)
            return null
        }

        val waiting = ArrayList<Consumer<String?>>()
        if (onReady != null) waiting.add(onReady)
        pending[key] = waiting
""",
    """        // Never retain unlimited pending ML Kit tasks or publish expired results.
        val ticket = pending.reserve(key, SystemClock.uptimeMillis(), onReady).ticket
            ?: return null
""",
)
patch(
    engine,
    """                .addOnSuccessListener { result -> complete(key, result, null) }
                .addOnFailureListener { error -> complete(key, null, error) }
        } catch (error: RuntimeException) {
            complete(key, null, error)
""",
    """                .addOnSuccessListener { result -> complete(key, ticket, result, null) }
                .addOnFailureListener { error -> complete(key, ticket, null, error) }
        } catch (error: RuntimeException) {
            complete(key, ticket, null, error)
""",
)
patch(
    engine,
    """    private fun complete(source: String, result: String?, error: Exception?) {
        if (!result.isNullOrBlank()) {
            cache[source] = result.trim()
            failuresUntil.remove(source)
        } else if (error != null) {
            translationFailures++
            failuresUntil[source] = SystemClock.uptimeMillis() + ERROR_BACKOFF_MS
            Log.w(TAG, "ML Kit text translation failed: " + safeError(error))
        }
        pending.remove(source)?.forEach { callback -> callback.accept(result) }
    }
""",
    """    private fun complete(source: String, ticket: Long, result: String?, error: Exception?) {
        // An expired ML Kit Task cannot overwrite a newer request or leak callbacks.
        val callbacks = pending.finish(source, ticket, SystemClock.uptimeMillis())
            ?: return
        if (!result.isNullOrBlank()) {
            cache[source] = result.trim()
            failuresUntil.remove(source)
        } else if (error != null) {
            translationFailures++
            failuresUntil[source] = SystemClock.uptimeMillis() + ERROR_BACKOFF_MS
            Log.w(TAG, "ML Kit text translation failed: " + safeError(error))
        }
        callbacks.forEach { callback -> callback.accept(result) }
    }
""",
)

for filename in ("TranslationQueueGate.kt", "PaymentActionPolicy.kt"):
    path = MAIN + filename
    if path in files:
        raise RuntimeError("Hardening already applied: " + path)
    source = (FEATURE / filename).read_bytes()
    assert source.startswith(b"package app.pinduoduo.overlayru\n")
    files[path] = source

for filename in ("TranslationQueueGateTest.kt", "PaymentActionPolicyTest.kt"):
    path = TEST + filename
    if path in files:
        raise RuntimeError("Test already injected: " + path)
    files[path] = (FEATURE / filename).read_bytes()

# Old and new runtime regressions, installation identity, license security.
manifest = files[BASE + "app/src/main/AndroidManifest.xml"].decode()
assert 'com.xunmeng.pinduoduo' in manifest
assert 'android:allowBackup="false"' in manifest
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in manifest
s = files[service].decode()
e = files[engine].decode()
assert 'WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE' in s
assert 'PaymentActionPolicy.isProtected(text.toString())' in s
assert 'PaymentActionPolicy.isProtected(clean)' in e
assert 'pending.finish(source, ticket, SystemClock.uptimeMillis())' in e
assert 'pending.reserve(key, SystemClock.uptimeMillis(), onReady)' in e
assert 'LicenseManager' not in s and 'LicenseManager' not in e
assert MAIN + 'LicenseTokenVerifier.kt' in files

repacked = io.BytesIO()
with zipfile.ZipFile(repacked, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as result:
    for member in members:
        result.writestr(member, files.pop(member.filename))
    for name, data in sorted(files.items()):
        result.writestr(name, data)
BUNDLE.write_text(base64.b64encode(repacked.getvalue()).decode("ascii") + "\n")
print("PASS: v0.2.0-beta.3 Kotlin source repacked, original settings, package and license logic preserved")
