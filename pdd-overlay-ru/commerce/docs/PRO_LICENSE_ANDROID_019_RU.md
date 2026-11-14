# PDD Overlay RU v0.1.9 — PRO client integration

## Implementation and branch
- Base Android: v0.1.8, applicationId `app.pinduoduo.overlayru`.
- Integration branch: `pdd-overlay-ru-pro-client-20261114`, separate from `main`.
- Source ZIP encoded as ASCII Base64: `pdd-overlay-ru/source_bundle.base64`. This contains the complete `pinduoduo_overlay_ru/` project.
- Tested backend: `pdd-overlay-ru/commerce/license-server/`.

## Functional scope
- `MainActivity`: standalone PRO/License navigation, no gate in translation path.
- `LicenseActivity`: activation key entry, Free/PRO status, expiration, renewal, human-readable errors.
- `LicenseTokenVerifier`: RS256 signature verification first, then exact issuer, audience, installation UUIDv4, plan, iat, nbf, exp, jti, and at most 7-day lease.
- `LicenseManager`: HTTPS transport (no redirects, 10-second connect/read limits), install UUIDv4 and signed entitlement stored in private preferences, non-sensitive server errors, manual renew and once/day limited auto renew when needed. No screen text sent to API.
- Existing Chinese and English translation, ML Kit models and the floating button are **not license-gated**.

## Server API (from the existing backend)
`POST /v1/activate`: request `{ "key": "...", "install_id": "uuidv4" }`.
`POST /v1/renew`: request `{ "token": "JWT", "install_id": "uuidv4" }`.
Both return `{ "token": "JWT", "expires_at": UnixTimeSeconds, "plan": "pro_30d|pro_365d|pro_lifetime" }`.

Signing must remain on the VDS. The Android app must contain only the public key.

## Configure a fully active release (not done)
1. Deploy `pdd-overlay-ru/commerce/license-server` with an HTTPS reverse proxy, access controls and rate limiting; create and retain the RSA-3072 signing private/public key pair on the VDS. Protect SQLite DB, lookup secret and signing key with backups.
2. In repository Settings → Secrets and variables → Actions → **Variables** add:
   - `PDD_LICENSE_API_BASE_URL`: complete HTTPS origin of your own license host, no trailing slash.
   - `PDD_LICENSE_PUBLIC_KEY_B64`: X.509 SPKI **public** key as Base64(DER), from `openssl pkey -pubin -in public.pem -outform DER | base64 -w0`.
3. Both variables are required together. With neither, the screen explicitly shows an unavailable server and never offers fake activation; the translator remains free.
4. In Actions **Secrets**, optionally set `PDD_KEYSTORE_B64`, `PDD_RELEASE_STORE_PASSWORD`, `PDD_RELEASE_KEY_ALIAS`, `PDD_RELEASE_KEY_PASSWORD` to enable signing with the permanent release certificate. Never commit these values to source or logs.
5. Rebuild the Android artifact with the pinned public key and HTTPS host; distribute the signed APK. Test a real server-issued key, activation on one installation, a second denied installation, expiry and renewal, revocation, airplane mode and restored connectivity.

## Verification completed
- `scripts/test_local.sh`: PASS for translation/overlay/privacy invariants.
- `:app:testReleaseUnitTest`: 7 new RS256 token-verification tests PASS.
- `:app:assembleRelease`: PASS on GitHub Actions 43622819301, revision `fc1f5814dec36871d9b66059d85fdc2a770cef80`.
- Backend `PYTHONPATH=. python -m pytest -q`: 12 PASS.
- APK v2/v3 signing: valid; signature certificate matches the existing v0.1.8 APK.
- Android device runtime, real deployed server and real payment flows: **NOT RUN**.

## Security and operational caveats
- A revoked subscription may continue functioning until the last signed 7-day lease expires. This is the intended offline trade-off.
- Clock rollback detection is best-effort and cannot resist full APK or device compromise. The on-device signature verifier is not unbreakable DRM.
- No license keys, token bodies or payment details belong in logs/analytics. The public API does not receive translated screen content.
- Rate limiting, webhook verification, backups, HTTPS and production operations must be completed before selling access.
- Payment and cancellation/refund handling are **not implemented**. Do not advertise auto-renewal or OCR as existing features.
- Do not change the application ID or signing certificate of production updates unintentionally; an ID change forces a separate install and separate licensing state.

## Manual acceptance on a phone
1. Install v0.1.9 over v0.1.8 and confirm that accessibility permission and dictionaries remain.
2. Verify free Chinese and English translation in Pinduoduo and move the floating RU button.
3. Open the new license screen, confirm server-not-configured is explicit, and confirm no network request is emitted with empty configuration.
4. After production configuration, activate a real key and test 7-day offline lease, renewal, invalid key, second-device limit, revoked key and clock rollback.
