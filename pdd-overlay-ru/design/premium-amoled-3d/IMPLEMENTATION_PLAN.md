# Implementation plan — PDD Premium AMOLED 3D

Target: current Kotlin ARM64-only PDD Overlay RU (starting from `v0.2.0-beta.9`), not a new application. Design reference: [DESIGN.md](DESIGN.md) and the five committed .webp assets.

## Milestones

### A. References and design contracts — COMPLETE
- [x] Preserve four visual references, each 941×1672, in GitHub.
- [x] Generate and save a cohesive refinement board.
- [x] Record asset hashes, colors, information hierarchy and privacy/product constraints.
- [x] Identify concept-only fake values and missing behaviors so they are not shipped as false UI.

### B. UI foundation — NEXT
- [ ] Establish a shared `PddTheme` covering Home, OCR, Dictionary and Settings, with semantic surface/status roles.
- [ ] Build one Material3 `NavigationBar` (4 destinations) with stable selected state, screen actions, system Back and Android window insets.
- [ ] Produce independent, transparent PDD bag/Chinese text/icon assets; **do not** copy flat screenshots into the app.
- [ ] Capture real screenshot smoke on an Android emulator or on the target phone and verify 1.3 system font scale.

### C. Screen integration — sequential, always functional
- [ ] **Home:** retain `HomeUiState` readiness and actions, configure foreground illustrations that do not mask service controls; remove duplicate setting entries.
- [ ] **Фото/OCR:** keep system image picker, `OcrImageOpener` and `OcrPhotoViewport`; align layout to new cards and bottom nav. Retain model missing / file unavailable / success behavior.
- [ ] **Мой словарь:** preserve `CustomGlossary`, corrections, editing and private persistence; implement real search before showing search UI. Optional chips only with genuine item categories.
- [ ] **Настройки:** migrate existing `MainActivity.kt` Android Views to Compose while preserving every control, model-consent flag, app detail link and license flow. This is a **functional migration** not a screenshot replacement.
- [ ] Re-evaluate 3D illustration assets and motion after real screenshots. One strong focal visual per screen max.

### D. Validation, APK and release
- [ ] Kotlin compile and focused regression tests on the **actual merged project**, not only mock state.
- [ ] Functional test of Accessibility service transitions and manual OCR image selection with a real product screenshot.
- [ ] Real screenshot review at normal/large font scale; no status bar or navigation overlap.
- [ ] Build only `arm64-v8a`, sign with the existing new Kotlin certificate (matching installed beta9), verify v2/v3 APK signature and a monotonically increased versionCode.
- [ ] Only after completed, publish exactly **one signed ARM64 APK** in chat; avoid unsiged/debug builds as user-facing downloads.

## Engineering constraints

- Canonical source archive currently stored in `pdd-overlay-ru/source_bundle.base64`. Read and patch the current extracted Android project with an idempotent guarded migration, run Gradle via GitHub Actions, then commit **tested** archive.
- Use repository workflow and branch naming conventions, avoid altering package names, model download consent, Pinduoduo Accessibility scope or local user data.
- Keep key material off GitHub. App signing cert SHA-256 for the new Kotlin series: `d0d4e5dfb604917e1896c9a4697f2743296e3fa7b137d5bacefdf7a8c7b519d3`.
- CI passing compilation doesn't imply mockups actually render on Lenovo Legion Y70. Keep release checks PASS / FAIL / NOT RUN explicit.
- Missing PRO merchant/backend functionality cannot be simulated by a design badge or a premium-screen marketing claim.

## Current artifact status

This branch archives **art and approved design rules**. It is not an APK release and does not change current users' installed app. Implementation will be delivered separately in tested, signed ARM64 builds.
