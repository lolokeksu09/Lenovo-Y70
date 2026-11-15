#!/usr/bin/env python3
"""Last Java-to-Kotlin migration for PDD Overlay RU v0.2.0-beta.2.

Repackage the versioned source ZIP without changing app ID, version, install UUID,
signed-entitlement preference keys, signing configuration or network trust anchor.
CI must verify security regressions before committing the canonical archive.
"""
from __future__ import annotations

import base64
import io
from pathlib import Path
import zipfile

bundle = Path("pdd-overlay-ru/source_bundle.base64")
root = "pinduoduo_overlay_ru/"
src = root + "app/src/main/java/app/pinduoduo/overlayru/"
test = root + "app/src/test/java/app/pinduoduo/overlayru/"
sources = Path("pdd-overlay-ru/migrations/license")

with zipfile.ZipFile(io.BytesIO(base64.b64decode(bundle.read_text().strip()))) as archive:
    prior = archive.infolist()
    files = {entry.filename: archive.read(entry) for entry in prior if not entry.is_dir()}

gradle = files[root + "app/build.gradle"].decode("utf-8")
manifest = files[root + "app/src/main/AndroidManifest.xml"].decode("utf-8")
assert "versionCode 12" in gradle and "versionName '0.2.0-beta.2'" in gradle
assert "applicationId 'app.pinduoduo.overlayru'" in gradle
assert "signingConfigs" in gradle and "PDD_RELEASE_KEYSTORE" in gradle
assert "LICENSE_API_BASE_URL" in gradle and "LICENSE_PUBLIC_KEY_B64" in gradle
assert "android:name=\".LicenseActivity\"" in manifest
assert "android:allowBackup=\"false\"" in manifest

names = ("LicenseTokenVerifier", "LicenseManager", "LicenseActivity")
for name in names:
    old = src + name + ".java"
    new = src + name + ".kt"
    if old not in files or new in files:
        raise RuntimeError("Unexpected migration input: " + name)
    text = (sources / (name + ".kt")).read_text("utf-8")
    assert text.startswith("package app.pinduoduo.overlayru\n"), name
    files.pop(old)
    files[new] = text.encode("utf-8")

verifier = files[src + "LicenseTokenVerifier.kt"].decode("utf-8")
manager = files[src + "LicenseManager.kt"].decode("utf-8")
screen = files[src + "LicenseActivity.kt"].decode("utf-8")
service = files[src + "PddAccessibilityService.kt"].decode("utf-8")
engine = files[src + "TranslationEngine.kt"].decode("utf-8")

# Guard the trust boundary; a Kotlin rewrite must not silently relax these controls.
for required in (
    'Signature.getInstance("SHA256withRSA")',
    "verifier.verify(signatureBytes)",
    "KeyFactory.getInstance(\"RSA\")",
    "header.optString(\"alg\") != \"RS256\"",
    "header.optString(\"typ\") != \"JWT\"",
    'claims.optString("iss") != ISSUER',
    'claims.optString("aud") != AUDIENCE',
    'claims.optString("install_id") != installationId',
    "expiresAt - issuedAt > LEASE_LIMIT_SECONDS",
    "nowSeconds >= expiresAt",
    "raw !is Int && raw !is Long",
    'throw InvalidLicenseException()',
):
    assert required in verifier, "License verifier invariant missing: " + required

for required in (
    '"overlay_ru_license"', '"install_uuid"', '"signed_entitlement"',
    '"max_seen_wall_clock_sec"', '"last_renew_attempt_sec"',
    "compareAndSet(false, true)",
    "response.getString(\"plan\")",
    "response.getLong(\"expires_at\")",
    "LicenseTokenVerifier.verify(",
    ".commit()",
    "url.userInfo != null",
    "url.protocol.equals(\"https\", ignoreCase = true)",
    "connection.instanceFollowRedirects = false",
    "connection.connectTimeout = 10_000",
    "connection.readTimeout = 10_000",
    "connection.setFixedLengthStreamingMode(bytes.size)",
    "if (output.size() + count > 16_384)",
    'status == "license_revoked"',
    'status == "invalid_token"',
    "status = \"connection_error\"",
):
    assert required in manager, "License transport invariant missing: " + required

for required in (
    "WindowManager.LayoutParams.FLAG_SECURE",
    "View.IMPORTANT_FOR_AUTOFILL_NO",
    "manager.activate(",
    "manager.renew(",
    "manager.renewIfNeeded()",
    "WeakReference(this)",
    "manager.configured() && !manager.busy()",
    "Free",
):
    assert required in screen, "License screen invariant missing: " + required

assert "LicenseManager" not in service
assert "LicenseManager" not in engine
assert "LicenseTokenVerifier" not in service
assert "LicenseTokenVerifier" not in engine

validator_key = root + "scripts/validate_sources.py"
validator = files[validator_key].decode("utf-8")
for name in names:
    legacy = name + ".java"
    migrated = name + ".kt"
    assert legacy in validator, "Missing legacy safety assertion " + legacy
    validator = validator.replace(legacy, migrated)
files[validator_key] = validator.encode("utf-8")

# Keep pre-existing Java/JUnit ABI tests that compile against Kotlin's @JvmStatic
# entrypoint, const LEASE_LIMIT_SECONDS and @JvmField Entitlement members.
assert test + "LicenseTokenVerifierTest.java" in files

new_test = test + "LicenseSecurityMigrationTest.kt"
assert new_test not in files
files[new_test] = r'''package app.pinduoduo.overlayru

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.UUID

/**
 * Negative trust-boundary regressions complement the retained Java/JUnit ABI suite.
 * Tokens are created with ephemeral, synthetic test keys; never real entitlements.
 */
class LicenseSecurityMigrationTest {
    companion object {
        private const val NOW = 1794660000L
        private const val ID = "3b957f11-3c87-40d7-b741-1cde69b08abc"
        private val LICENSE_ID = "c210dd39-cc56-4c5b-a243-c806ec42ce9b"
        private lateinit var key: KeyPair
        private lateinit var publicKey: String

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            key = generator.generateKeyPair()
            publicKey = Base64.getEncoder().encodeToString(key.public.encoded)
        }
    }

    private fun claims(): JSONObject = JSONObject()
        .put("iss", "overlay-ru-license")
        .put("aud", "app.pinduoduo.overlayru")
        .put("sub", LICENSE_ID)
        .put("install_id", ID)
        .put("plan", "pro_30d")
        .put("iat", NOW - 20)
        .put("nbf", NOW - 20)
        .put("exp", NOW + 3600)
        .put("jti", UUID.randomUUID().toString())

    private fun signed(payload: JSONObject, alg: String = "RS256"): String {
        val header = JSONObject().put("alg", alg).put("typ", "JWT")
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val input = encoder.encodeToString(header.toString().toByteArray(StandardCharsets.UTF_8)) +
            "." + encoder.encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(key.private)
        signer.update(input.toByteArray(StandardCharsets.US_ASCII))
        return input + "." + encoder.encodeToString(signer.sign())
    }

    private fun rejects(token: String?, installId: String? = ID) {
        try {
            LicenseTokenVerifier.verify(token, publicKey, installId, NOW)
            fail("Invalid or unauthorized signed entitlement accepted")
        } catch (_: LicenseTokenVerifier.InvalidLicenseException) {
            // Secure denial is expected.
        }
    }

    @Test
    fun acceptsSyntheticValidServerCompatibleLease() {
        val entitlement = LicenseTokenVerifier.verify(signed(claims()), publicKey, ID, NOW)
        assertEquals("pro_30d", entitlement.plan)
        assertEquals(NOW + 3600L, entitlement.expiresAt)
    }

    @Test
    fun rejectsMalformedAndEmptySegmentsWithoutCrashing() {
        rejects(null)
        rejects("")
        rejects("e30.e30.")
        rejects("e30.e30.invalid.extra")
        rejects("garbage.jwt")
    }

    @Test
    fun rejectsOtherDeviceEvenWithValidSignature() {
        val valid = signed(claims())
        rejects(valid, UUID.randomUUID().toString())
        rejects(valid, "INVALID-DEVICE-ID")
    }

    @Test
    fun rejectsTimeBoundViolationsAndNonIntegerTimestamps() {
        rejects(signed(claims().put("exp", NOW)))
        rejects(signed(claims().put("iat", NOW + 120)))
        rejects(signed(claims().put("nbf", NOW + 120)))
        rejects(signed(claims().put("exp", NOW + LicenseTokenVerifier.LEASE_LIMIT_SECONDS + 5)))
        rejects(signed(claims().put("exp", (NOW + 3600).toString())))
        rejects(signed(claims().put("iat", (NOW - 20).toDouble())))
    }

    @Test
    fun rejectsUnsupportedIdentityAndPlanClaims() {
        rejects(signed(claims().put("iss", "wrong-issuer")))
        rejects(signed(claims().put("aud", "another.app")))
        rejects(signed(claims().put("plan", "superadmin")))
        rejects(signed(claims().put("jti", "not-a-uuid")))
        rejects(signed(claims(), "none"))
    }
}
'''.encode("utf-8")

architecture = root + "ONE_MODULE_ARCHITECTURE.md"
files[architecture] += (
    "\n## Complete Android application Kotlin migration (phase 5)\n\n"
    "- LicenseTokenVerifier, LicenseManager and LicenseActivity are Kotlin.\n"
    "- Java-callable verifier API and the old Java/JUnit RS256 regression tests remain intact.\n"
    "- Release trust anchor, HTTPS transport, request body limits, single-flight operations,\n"
    "  signed-lease cache, 7-day limit, renewal policy and SharedPreferences keys are unchanged.\n"
    "- Accessibility/ML Kit text remains entirely separate from licensing transport.\n"
    "- The entire app/src/main/java source folder now contains Kotlin code only;\n"
    "  Java language may remain in JVM test fixtures and historical build tooling.\n"
    "- ApplicationId, versionCode 12, manifest entry points, Gradle module, and signing\n"
    "  configuration remain unchanged. Device deployment is not established by CI.\n"
).encode("utf-8")

output = io.BytesIO()
with zipfile.ZipFile(output, "w") as out:
    written = set()
    for entry in prior:
        if entry.is_dir():
            out.writestr(entry, b"")
        elif entry.filename in files:
            out.writestr(entry, files[entry.filename])
            written.add(entry.filename)
    for filename in sorted(files.keys() - written):
        out.writestr(filename, files[filename], compress_type=zipfile.ZIP_DEFLATED)

with zipfile.ZipFile(io.BytesIO(output.getvalue())) as check:
    assert check.testzip() is None
    assert len(check.namelist()) == len(set(check.namelist()))
    assert not any(
        path.startswith(src) and path.endswith(".java")
        for path in check.namelist()
    ), "Android app production Java files remain"
    for name in names:
        assert src + name + ".kt" in check.namelist(), name
    assert new_test in check.namelist()

bundle.write_text(base64.b64encode(output.getvalue()).decode("ascii") + "\n")
print("Phase 5: license verifier, transport and screen migrated; no Java application sources remain")
