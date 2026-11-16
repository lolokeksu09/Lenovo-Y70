package app.pinduoduo.overlayru

import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

/**
 * Offline RS256 lease verifier; never accepts merely decoded (unverified) claims.
 * Rejects tokens with an unsupported algorithm, audience, installation, plan or lease period.
 * All failures intentionally collapse to InvalidLicenseException without leaking JWT contents.
 */
object LicenseTokenVerifier {
    const val LEASE_LIMIT_SECONDS = 7L * 24L * 60L * 60L
    private const val CLOCK_SKEW_SECONDS = 60L
    private const val ISSUER = "overlay-ru-license"
    private const val AUDIENCE = "app.pinduoduo.overlayru"
    private val tokenPartPattern = Regex("[A-Za-z0-9_-]+")

    class Entitlement(
        @JvmField val plan: String,
        @JvmField val licenseId: String,
        @JvmField val issuedAt: Long,
        @JvmField val expiresAt: Long
    )

    class InvalidLicenseException : Exception("invalid_signed_entitlement")

    @JvmStatic
    @Throws(InvalidLicenseException::class)
    fun verify(
        jwt: String?,
        publicKeyDerB64: String?,
        installationId: String?,
        nowSeconds: Long
    ): Entitlement {
        try {
            if (jwt == null || jwt.length < 100 || jwt.length > 8192 ||
                publicKeyDerB64.isNullOrEmpty() || installationId == null) {
                throw InvalidLicenseException()
            }

            val installUuid = UUID.fromString(installationId)
            if (installUuid.version() != 4 ||
                installUuid.toString() != installationId) throw InvalidLicenseException()

            // Limit to at most four parts: anything but exactly three fails closed.
            val parts = jwt.split('.', limit = 4)
            if (parts.size != 3 || parts.any { !tokenPartPattern.matches(it) }) {
                throw InvalidLicenseException()
            }

            val signatureBytes = Base64.getUrlDecoder().decode(parts[2])
            val trustedDer = Base64.getDecoder().decode(publicKeyDerB64)
            val trustedKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(trustedDer))
            val signingInput = (parts[0] + "." + parts[1])
                .toByteArray(StandardCharsets.US_ASCII)
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(trustedKey)
            verifier.update(signingInput)
            if (!verifier.verify(signatureBytes)) throw InvalidLicenseException()

            val header = parseJson(parts[0])
            if (header.optString("alg") != "RS256" ||
                header.optString("typ") != "JWT") throw InvalidLicenseException()

            val claims = parseJson(parts[1])
            if (claims.optString("iss") != ISSUER ||
                claims.optString("aud") != AUDIENCE ||
                claims.optString("install_id") != installationId) {
                throw InvalidLicenseException()
            }
            val plan = claims.optString("plan")
            if (plan != "pro_30d" && plan != "pro_365d" && plan != "pro_lifetime") {
                throw InvalidLicenseException()
            }
            val licenseId = claims.getString("sub")
            val jti = claims.getString("jti")
            if (UUID.fromString(licenseId).toString() != licenseId ||
                UUID.fromString(jti).toString() != jti) {
                throw InvalidLicenseException()
            }

            val issuedAt = timestamp(claims, "iat")
            val notBefore = timestamp(claims, "nbf")
            val expiresAt = timestamp(claims, "exp")
            if (issuedAt > nowSeconds + CLOCK_SKEW_SECONDS ||
                notBefore > nowSeconds + CLOCK_SKEW_SECONDS ||
                nowSeconds >= expiresAt ||
                issuedAt <= 0L || notBefore <= 0L || expiresAt <= issuedAt ||
                expiresAt - issuedAt > LEASE_LIMIT_SECONDS ||
                nowSeconds < issuedAt - CLOCK_SKEW_SECONDS) {
                throw InvalidLicenseException()
            }
            return Entitlement(plan, licenseId, issuedAt, expiresAt)
        } catch (error: InvalidLicenseException) {
            throw error
        } catch (_: Exception) {
            // Never expose token, key, claims, parser exceptions or identifiers in logs.
            throw InvalidLicenseException()
        }
    }

    private fun parseJson(encoded: String): JSONObject {
        val decoded = Base64.getUrlDecoder().decode(encoded)
        if (decoded.size > 8192) throw JSONException("oversized_jwt_json")
        return JSONObject(String(decoded, StandardCharsets.UTF_8))
    }

    private fun timestamp(claims: JSONObject, key: String): Long {
        val raw = claims.get(key)
        // org.json accepts string-to-long coercion; JWT timestamps must be JSON integers.
        if (raw !is Int && raw !is Long) throw JSONException("invalid_timestamp_type")
        return (raw as Number).toLong()
    }
}
