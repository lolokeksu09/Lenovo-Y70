package app.pinduoduo.overlayru;

import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.UUID;

import static org.junit.Assert.*;

public class LicenseTokenVerifierTest {
    private static KeyPair key;
    private static String publicKey;
    private static final String ID = "3b957f11-3c87-40d7-b741-1cde69b08abc";
    private static final String LICENSE_ID = "c210dd39-cc56-4c5b-a243-c806ec42ce9b";
    private static final long NOW = 1794660000L;

    @BeforeClass public static void setUpKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        key = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(key.getPublic().getEncoded());
    }

    private JSONObject claims() throws Exception {
        return new JSONObject()
                .put("iss", "overlay-ru-license")
                .put("aud", "app.pinduoduo.overlayru")
                .put("sub", LICENSE_ID)
                .put("install_id", ID)
                .put("plan", "pro_30d")
                .put("iat", NOW - 20)
                .put("nbf", NOW - 20)
                .put("exp", NOW + 3600)
                .put("jti", UUID.randomUUID().toString());
    }

    private String signed(JSONObject claims, String algorithm) throws Exception {
        JSONObject header = new JSONObject().put("alg", algorithm).put("typ", "JWT");
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String left = encoder.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(claims.toString().getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.getPrivate());
        signature.update(left.getBytes(StandardCharsets.US_ASCII));
        return left + "." + encoder.encodeToString(signature.sign());
    }

    private void rejected(String token) throws Exception {
        try {
            LicenseTokenVerifier.verify(token, publicKey, ID, NOW);
            fail("Malformed or unauthorized token was accepted");
        } catch (LicenseTokenVerifier.InvalidLicenseException expected) { /* expected */ }
    }

    @Test public void acceptsServerCompatibleRs256TokenOffline() throws Exception {
        LicenseTokenVerifier.Entitlement entitlement = LicenseTokenVerifier.verify(
                signed(claims(), "RS256"), publicKey, ID, NOW);
        assertEquals("pro_30d", entitlement.plan);
        assertEquals(LICENSE_ID, entitlement.licenseId);
        assertEquals(NOW + 3600, entitlement.expiresAt);
    }

    @Test public void rejectsModifiedSignedPayload() throws Exception {
        String value = signed(claims(), "RS256");
        String[] segments = value.split("\\.");
        byte[] decoded = Base64.getUrlDecoder().decode(segments[1]);
        String changed = new String(decoded, StandardCharsets.UTF_8).replace("pro_30d", "pro_365d");
        segments[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(changed.getBytes(StandardCharsets.UTF_8));
        rejected(String.join(".", segments));
    }

    @Test public void rejectsWrongInstallationOrAudience() throws Exception {
        rejected(signed(claims().put("install_id", UUID.randomUUID().toString()), "RS256"));
        rejected(signed(claims().put("aud", "other.app"), "RS256"));
    }

    @Test public void rejectsExpiredAndNotYetValidLease() throws Exception {
        rejected(signed(claims().put("exp", NOW), "RS256"));
        rejected(signed(claims().put("nbf", NOW + 120), "RS256"));
    }

    @Test public void rejectsUnsupportedPlanAndAlgorithm() throws Exception {
        rejected(signed(claims().put("plan", "admin"), "RS256"));
        rejected(signed(claims(), "none"));
    }

    @Test public void rejectsExcessiveLeaseAndStringTimestamps() throws Exception {
        rejected(signed(claims().put("exp", NOW + LicenseTokenVerifier.LEASE_LIMIT_SECONDS + 1000), "RS256"));
        rejected(signed(claims().put("iat", String.valueOf(NOW - 20)), "RS256"));
    }

    @Test public void rejectsMalformedHeaderAndWrongKey() throws Exception {
        String token = signed(claims(), "RS256");
        rejected("invalid.jwt");
        KeyPairGenerator other = KeyPairGenerator.getInstance("RSA");
        other.initialize(2048);
        String notTrusted = Base64.getEncoder().encodeToString(other.generateKeyPair().getPublic().getEncoded());
        try {
            LicenseTokenVerifier.verify(token, notTrusted, ID, NOW);
            fail("Untrusted key accepted");
        } catch (LicenseTokenVerifier.InvalidLicenseException expected) { /* expected */ }
    }
}
