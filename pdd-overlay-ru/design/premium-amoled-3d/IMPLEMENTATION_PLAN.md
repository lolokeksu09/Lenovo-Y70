# Implementation and verification status — PDD Premium AMOLED 3D

Updated: 2026-11-16. Canonical PDD branch: `pdd-overlay-ru-compose-single-v020`.
Stabilization PR: `pdd-overlay-ru-stabilization-beta11`.

## A. Design contract — COMPLETE (beta10)
- [x] Store four UI references and refinement board as WebP.
- [x] Define an AMOLED palette, navigation, empty/loading/error contracts and privacy constraints.
- [x] Keep screenshots as references, not interactive Android screens.

## B. Real Android UI — IMPLEMENTED (beta10)
- [x] Shared Jetpack Compose Material 3 palette/theme.
- [x] Four visible destinations: Главная, Фото, Словарь, Настройки.
- [x] Drawn PDD bag hero (not a flattened reference screenshot).
- [x] Main readiness state, model consent actions, existing translation and license controls.
- [x] System image picker, offline Chinese OCR, interactive 1–5x image view.
- [x] Local dictionary search, add, edit, delete with confirmation.
- [x] Settings migrated to state-driven Compose without changing protected runtime settings.
- [ ] Rendered screenshot comparison at 1.0 and 1.3 font scale on an actual device.

## C. Stabilization — IMPLEMENTED IN BETA11 BRANCH, VERIFY BEFORE MERGE
- [x] Fix dictionary edit to atomically replace the original key; reject collisions and stale edits.
- [x] Add five JVM edit-policy regression cases.
- [x] Route top-level tab selections consistently with `PddNav` and clear intermediate tabs.
- [x] Provide a one-time guarded migration to readable `pdd-overlay-ru/android/` source files.
- [x] Configure CI with 71-test, privacy, ARM64 and native JNI checks.
- [x] Add a separate Android 35 emulator smoke workflow for all four Activities.
- [ ] Confirm new revision CI is green and readable project source tree is committed.
- [ ] Confirm emulator smoke reports no startup failures and inspect its screenshots.

## D. User-facing beta11 release — NOT COMPLETE
- [ ] Verify UI/Back/keyboard/OCR/permission recovery on the real Lenovo Legion Y70.
- [ ] Verify device font scaling 1.3 and system status/navigation insets.
- [ ] Verify live Pinduoduo Accessibility service, model downloads and real-world OCR.
- [ ] Sign ARM64 APK using **the existing beta9/beta10 signing key**, certificate SHA-256
      `d0d4e5dfb604917e1896c9a4697f2743296e3fa7b137d5bacefdf7a8c7b519d3`.
- [ ] Verify APK signer v2/v3 and that `versionCode 21` upgrades a signed beta10 without data loss.
- [ ] Distribute **only one signed ARM64 APK**; CI unsigned APK is internal, not user-facing.

## Invariants
- Package `app.pinduoduo.overlayru`, minSdk 26, targetSdk 35, ARM64 release only.
- Chinese and English ML Kit model downloads require explicit separate consent.
- No screenshot capture, broad storage permission, payment-label replacement or silent Accessibility grant.
- User dictionary remains local, encrypted PRO license verification is unchanged.
- The old `source_bundle.base64` is historical beta10 input only once the normalized Android tree is committed.

A green JVM build does not prove rendered-screen fidelity or physical-device behavior.
