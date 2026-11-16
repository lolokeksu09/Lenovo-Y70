# Implementation and verification status — PDD Premium AMOLED 3D

Updated: 2026-11-16. Canonical PDD branch: `pdd-overlay-ru-compose-single-v020`.
Stabilization PR #17: **merged**, squash commit `8b6d16e1097a92c1e97b1cc399cc9a0b297b3a70`.
Additional Android interaction test: [draft PR #18](https://github.com/lolokeksu09/Lenovo-Y70/pull/18).

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

## C. Stabilization — MERGED INTO MAIN (beta11)
- [x] Fix dictionary edit to atomically replace the original key; reject collisions and stale edits.
- [x] Add five JVM edit-policy regression cases.
- [x] Route top-level tab selections consistently with `PddNav` and clear intermediate tabs.
- [x] Provide a one-time guarded migration to readable `pdd-overlay-ru/android/` source files.
- [x] Configure CI with 71-test, privacy, ARM64 and native JNI checks.
- [x] Add a separate Android 35 emulator smoke workflow for all four Activities.
- [x] Confirm beta11 CI 71/71 and readable project source tree committed, run `43821463490`.
- [x] Android 35 emulator four-tab smoke and content-aware screenshots passed, run `43821463539`.
- [x] Android 35 emulator four-tab smoke with font scale 1.3× passed, run `43823298958`.
- [ ] Real-device visual confirmation at 1.3× on Lenovo remains pending.

## D. User-facing beta11 QA — SIGNED, DEVICE ACCEPTANCE PENDING
- [ ] Verify UI/Back/keyboard/OCR/permission recovery on the real Lenovo Legion Y70.
- [ ] Verify device font scaling 1.3 and system status/navigation insets.
- [ ] Verify live Pinduoduo Accessibility service, model downloads and real-world OCR.
- [x] Sign ARM64 APK using **the existing beta9/beta10 signing key**, certificate SHA-256
      `d0d4e5dfb604917e1896c9a4697f2743296e3fa7b137d5bacefdf7a8c7b519d3`.
- [x] Verify official Android SDK apksigner v2/v3, ARM64-only payload, and 16 KiB library alignment.
- [ ] Confirm versionCode 21 performs an actual in-place signed beta10 upgrade without data loss on Lenovo.
- [x] Produce one **corrected signed ARM64 APK** after restoring PRO BuildConfig environment; obsolete earlier beta11 artifact must not be redistributed. SHA-256: `04ac0e85edc4a75176ffd1e0cdff3b1561ac93257b0444293d3def66d57abd71`. CI unsigned APK remains internal.

## E. Real Android UI interactions — TESTING IN PR #18
- [x] Automated dictionary add → persistence across process restart → rename (old key removed) → delete cancel → confirmed delete in Android 35 emulator. PASS in run `43825854300`.
- [x] System image picker opens and cancel returns safely to OCR without restarting the process. PASS in run `43825854300`.
- [x] Review 5 screenshots (empty/create/rename/delete/OCR cancellation), app process and logs. All match expected transitions; scoped AndroidRuntime errors absent. CI run `43825854300` PASS.
- [x] Merge test-only PR #18 into canonical branch after PASS; squash commit `6028b01034b2d768c07230fdac057179ebe6cb33`. No app binaries or Kotlin production code changed.
- [ ] Real OCR of Chinese goods screenshot with explicit user image selection, and consented model download on Legion Y70.
- [ ] Scope and security of live Accessibility against official Pinduoduo on Lenovo, excluding payment/OTP actions.
- [ ] Live PRO activation and entitlement renewal remain unverified. CI `43821463490` only confirms configured URL/public key.

## Invariants
- Package `app.pinduoduo.overlayru`, minSdk 26, targetSdk 35, ARM64 release only.
- Chinese and English ML Kit model downloads require explicit separate consent.
- No screenshot capture, broad storage permission, payment-label replacement or silent Accessibility grant.
- User dictionary remains local, encrypted PRO license verification is unchanged.
- The old `source_bundle.base64` is historical beta10 input only once the normalized Android tree is committed.

A green JVM build does not prove rendered-screen fidelity or physical-device behavior.
