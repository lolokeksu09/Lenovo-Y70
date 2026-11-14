# Legion Control — permanent APK signing

The release application ID is **`dev.legion.control`**. Release certificate SHA-256:

`c912747bc79e33adfc327817e36c667719937de59dcad1c22bf154e44bb56f5a`

The private keystore and its passwords are deliberately **not stored in the repository**. The keystore must be backed up offline and uploaded as encrypted GitHub Actions repository secrets using the owner's private signing kit.

Required secrets:

- `LEGION_RELEASE_KEYSTORE_BASE64` — base64-encoded PKCS12 keystore, no line wraps
- `LEGION_RELEASE_STORE_PASSWORD`
- `LEGION_RELEASE_KEY_PASSWORD`
- `LEGION_RELEASE_KEY_ALIAS`

The signing kit includes `activate-github-secrets.sh` for uploading these via GitHub CLI. This operation requires the repository owner's authentication. A connected read-only GitHub integration cannot set Actions Secrets.

The workflow `.github/workflows/release-control-center.yml` checks a pinned source archive SHA-256, runs command-policy tests, builds `assembleRelease` (not `assembleDebug`), zipaligns, signs with `apksigner`, verifies the signature and pinned public certificate digest, checks package/version, and uploads the signed APK.

## First release

- Version: `1.1.0`, `versionCode=2`.
- App ID: `dev.legion.control` (no preview suffix).
- Trigger: `workflow_dispatch` on the default branch after the workflow is merged; or a release tag `legion-control-v1.1.0` pointing at a commit that contains the workflow and pinned archive.
- The workflow will intentionally fail before signing if any required secret is missing.

## Every subsequent release

1. Keep **the same application ID**, PKCS12 keystore and alias.
2. Increase `versionCode` monotonically; update `versionName`.
3. Update the source archive and its pinned SHA-256 in the release workflow, plus package/version assertions and output filename. Change the certificate fingerprint **only** for a deliberate signing-key rotation, which requires a separate migration plan.
4. Build via the release workflow and verify package, `versionCode` and signer SHA-256 prior to distribution.

## Migration from debug APKs

Previously distributed debug and preview APKs use other package IDs or different signing certificates. The new permanent release cannot silently replace those builds. Back up any relevant app settings and remove only the conflicting old package after the user has confirmed the migration. A different preview package can coexist with the release.

## Security

Do not commit `.p12`, `.jks`, `*.keystore`, `*.env` or the signing-kit ZIP. Release workflow does not run for pull requests and does not print secret values. A GitHub Actions secret is not a replacement for a private offline backup of the original keystore.