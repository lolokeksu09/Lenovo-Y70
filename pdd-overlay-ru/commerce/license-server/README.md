# Overlay RU — commercial licensing service (foundation v0.1)

This is a **working, tested license backend prototype**. It is not yet wired to the Android app and it does not collect money. The existing signed PDD Overlay RU v0.1.8 continues working without a license.

## Security model

- One 160-bit random activation key per customer; **only HMAC-SHA256 lookup hashes are stored** in SQLite, using a separate server-only secret.
- The first successful activation binds the key to a random **installation UUIDv4** (default one installation, configurable up to five). The client must generate and persist this UUID privately; do not use hardware identifiers, IMEI, Android ID or screen contents.
- API returns a signed **RS256 JWT entitlement**, with issuer, audience, license ID, plan, installation UUID, issued time, activation time, unique token ID and expiration.
- JWT lasts at most **seven days**; subscription expiration may shorten that window. Clients can validate it fully offline with a **pinned RSA public key** embedded in the app, and refresh when internet is available. A revoked key may continue to work until its most recently issued JWT expires: deliberate offline trade-off.
- RSA **3072-bit private key remains only on the VDS**; no private signing key, lookup secret or raw activation keys are committed. The private key must never be bundled in an APK.
- Public API only supports `POST /v1/activate` and `POST /v1/renew`, plus read-only `/health`. **No remotely exposed admin or payment endpoints**.
- Successful token renewal is independent of the original activation key. A token and an installation ID can both be copied from a compromised device, so this is not tamper-proof licensing. Application integrity/device attestation can be evaluated later.
- No translations, Chinese text, screenshots, contact lists, accessibility nodes or financial details are collected by this service.

## API

### POST /v1/activate

JSON body: `{"key":"<customer-provided-key>","install_id":"<uuid-v4>"}`. Returns `{ "token": "<signed JWT>", "expires_at": 1234567890, "plan": "pro_30d" }`. The `key` must never be written to logs or URL query parameters.

### POST /v1/renew

JSON body: `{"token":"<previous-signed-JWT>","install_id":"<uuid-v4>"}`. Returns the same shape with a newly signed entitlement. A successful renew checks the license record again, so expiration and server revocation take effect at renewal; activation count remains unchanged.

Errors use safe enumerated codes: `invalid_request`, `invalid_license`, `invalid_token`, `license_expired`, `license_revoked`, `activation_limit_reached`, `activation_not_found`, `expired_token`. HTTP status 403 except malformed requests (422). Keys are never reflected into error bodies.

**This is not yet a production public service.** It requires an HTTPS reverse proxy, per-IP/credential rate limiting, health monitoring, encrypted offsite backups, audit logs that do not include keys or tokens, and real payment-provider webhooks validated by signature and idempotency before money or access is granted.

## Test locally

```sh
cd license-server
python -m pip install '.[test]'
python -m pytest -q
```

## Provision on VDS (not performed automatically)

Generate signing keys locally **on the VDS** (do not send the private key to GitHub):

```sh
cd license-server
mkdir -p secrets data
python3 -m overlay_license.admin init-keypair --private secrets/private.pem --public secrets/public.pem
sudo chown -R 10001:10001 secrets data
chmod 700 secrets data
chmod 600 secrets/private.pem
export LICENSE_LOOKUP_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
# Persist this environment variable in a protected local .env with chmod 600.
docker compose up --build -d
```

Do not open port 8787 to the public internet. `compose.yaml` binds it to `127.0.0.1`. Configure **HTTPS and rate limiting** in a reverse proxy before connecting Android clients. Back up the SQLite file, private signing key and lookup secret together; losing the signing key invalidates all issued offline tokens and losing the lookup secret prevents any previously issued activation key from being looked up. Never regenerate those casually.

Issue one 30-day activation key **manually after payment has been confirmed**:

```sh
docker compose exec license python -m overlay_license.admin issue --plan pro_30d --days 30 --devices 1
```

Only the issuer output contains the plaintext activation key; provide it to the customer securely. For a one-year key, use `--plan pro_365d --days 365`. `--plan pro_lifetime` is supported **only as a technical license type**; evaluate support and contractual implications before selling lifetime access.

Other local commands:

```sh
docker compose exec license python -m overlay_license.admin extend --license-id UUID --days 30
docker compose exec license python -m overlay_license.admin revoke --license-id UUID
docker compose exec license python -m overlay_license.admin reset-device --license-id UUID --install-id UUID
```

These commands take IDs obtained from the local issue operation or your private sales records; `UUID` above denotes an actual license or installation identifier, not a generated value.

## Android client integration (next stage, not active)

1. Generate a random installation UUIDv4 once using `UUID.randomUUID()` and keep it in app-private preferences. This ID is **not** a device serial number.
2. Include `public.pem` only; verify the JWT signature using `SHA256withRSA`, and independently validate exact issuer, audience, plan, UUID, issued/not-before/expiration claims and clock skew. Never trust decoded claims before signature validation.
3. Send activation over HTTPS and pin the expected server public TLS identity where feasible. Do not log activation keys or JWTs.
4. Refresh once per day when internet is available, rather than at every translation request. Allow local PRO use until the signed lease expires (seven days maximum).
5. Design the FREE/PRO feature flags only after their behavior is actually implemented, and **do not change existing v0.1.8 users' features without a published migration policy**.
6. Never put license requests on the Accessibility text-processing path; payment/license failures must not expose screen contents or interrupt a shopping checkout.

## Payment integration (not implemented)

- For direct APK sales: a payment provider must call a **separate, verified webhook** that persists the payment ID and processes it exactly once before issuing or extending a license.
- For RuStore: use current RuStore Pay SDK and verify purchases server-side. Do not add the retired BillingClient SDK or trust an app-provided paid flag.
- Refunds, chargebacks and subscriptions require explicit policy and reliable revocation/expiry handling. Never auto-renew unless the customer knowingly accepts the terms.
