package app.pinduoduo.overlayru

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
