# Implementation and verification status — PDD Premium AMOLED 3D

Updated: 2026-11-16. Canonical PDD branch: `pdd-overlay-ru-compose-single-v020`.
Stabilization PR #17: **merged**, squash commit `8b6d16e1097a92c1e97b1cc399cc9a0b297b3a70`.
Android interaction PR #18 and real Chinese OCR PR #19: **merged**.
Offline glossary/model-state correction: PR #21 **merged**. Current feedback: long English reviews + in-image replacements.

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

## E. Real Android UI interactions — VERIFIED AND MERGED
- [x] Automated dictionary add → persistence across process restart → rename (old key removed) → delete cancel → confirmed delete in Android 35 emulator. PASS in run `43825854300`.
- [x] System image picker opens and cancel returns safely to OCR without restarting the process. PASS in run `43825854300`.
- [x] Review 5 screenshots (empty/create/rename/delete/OCR cancellation), app process and logs. All match expected transitions; scoped AndroidRuntime errors absent. CI run `43825854300` PASS.
- [x] Merge test-only PR #18 into canonical branch after PASS; squash commit `6028b01034b2d768c07230fdac057179ebe6cb33`. No app binaries or Kotlin production code changed.
- [x] Android 35 emulator: synthetic Chinese image selected using actual system gallery → ML Kit reports two lines and renders `商品详情`, `查看物流`. PASS: run `43858964995`, screenshot `chinese-ocr-result.png`. Does NOT download translation models.
- [ ] Real Chinese goods screenshot and separate consented model download on Lenovo Legion Y70.
- [ ] Scope and security of live Accessibility against official Pinduoduo on Lenovo, excluding payment/OTP actions.
- [ ] Validate general OCR Chinese→Russian output after **explicit ML Kit model download** on the Lenovo; the model download has not been exercised.
- [x] Captured beta11 defect: without models, recognized `商品详情` and `查看物流` stayed untranslated despite existing built-in glossary entries (`43858964995`).
- [x] Corrected OCR dispatch in PR #21 to try local correction/bundled glossary regardless of model readiness, leaving the ML Kit downloader gated by user consent.
- [x] Android 35 E2E verifies **two Russian glossary outputs without model download**; run `43885916622` PASS with `Переведено 2 из 2 строк`, visible text, screenshots and no AndroidRuntime errors.
- [x] Final Android 35 E2E `43887705435` PASS (commit `895453f8a9374eaa77399251db60b1edbf5f7067`): Pixel Launcher ANR recovered without modifying PDD, real ML Kit OCR, `2/2` Russian text, focused AndroidRuntime log empty and screenshot reviewed.
- [x] ARM64 Release validation `43887866819` PASS (commit `f0ec7cb4ce27c8119731ccb267b87804369e8319`): 76/76 tests, privacy gate, ARM64 native OCR/translate libs, configured PRO BuildConfig. No signed APK was published.
- [x] PR #21 merged after Android and ARM64 PASS; squash commit `0bb8284532fafbca432711a69d67bf273e18933b`. No signing, APK upload or prerelease asset changes.
- [ ] Live PRO activation and entitlement renewal remain unverified. CI `43821463490` only confirms configured URL/public key.

## F. Device-only acceptance — REMAINS OPEN
- [ ] Physical Lenovo Legion Y70 install/update retains local glossary and settings.
- [ ] Actual Pinduoduo Accessibility/overlay grants, recoveries and protected purchase controls.
- [ ] Consented Chinese→Russian ML Kit model download, translated product text and offline re-use.
- [ ] Live PRO activation and entitlement renewal; build-time configuration is not proof.

## G. Physical-device report — 2026-11-16 (ACTIVE)

Observed on Lenovo Legion Y70 running the user's PDD Overlay RU signed beta11 OCR-fix build:
- [x] Screenshot captured: long English review about kiwifruit remains English. Many shorter translated chip overlays are visible. **Observed**, not proof that the English offline model is available.
- [x] Source audit: generic translation policy rejects text over 180 characters and non-ASCII English punctuation, including an em dash, so a normal English review never reaches model translation.
- [x] Source audit: OCR photo viewer draws recognized bounds and puts Russian in a secondary bottom panel; it does not paint translated words at their image coordinates.
- [x] Added bounded long English review chunking and multiline anchored caption, guarded by explicit English consent/model readiness. Static JVM tests PASS (run `43895561486`). Real review not yet tested on Lenovo.
- [x] Implemented reversible in-image translated text rendering for supported OCR rectangles with explicit original/translation toggle. Price/payment/OTP guards preserved; screenshots reviewed (run `43895561486`).
- [x] 89/89 JVM tests, ARM64 release compile and privacy source gate PASS; Android 35 actual system gallery → ML Kit OCR → inline Russian overlay → original toggle → translation toggle PASS (run `43895561486`).
- [ ] Validate on actual Lenovo screenshot: long review readable, existing short labels no overlaps; OCR viewer can toggle Russian on-image / original, no covered buttons or amounts. Emulator evidence is not phone validation.
- [ ] Check explicit consent and downloaded **English→Russian** ML Kit models on Lenovo. Without them free-form English review translation cannot be claimed complete.
- [ ] Verify any Pinduoduo view exposed as a WebView/Canvas can be reached through the scoped Accessibility tree. Where node.text is missing, require a different explicit on-device OCR mechanism; never enable silent whole-screen recording.

No user screenshot, avatar, order or review personally identifiable data is committed to the repository.

## H. PR #22 verification evidence

- [x] [PR #22](https://github.com/lolokeksu09/Lenovo-Y70/pull/22) squash-merged into the canonical branch, commit `8c18aa15ee10c07e243218f61f4d1f9d48f8d827`; new source changes are not part of the earlier signed beta11 APK.

- ARM64 Gradle release + privacy validation: PASS, 89/89 JVM tests, run `43895561486`.
- Android 35 x86_64 real OCR image selection, two Chinese labels translated from bundled dictionary, screenshot of Russian text actually occupying the image rectangles, and reversible original/translation toggle: PASS, run `43895561486`. Reviewed `inline-russian-caption-viewer.png` and `original-photo-viewer.png`.
- The screenshot's English prose review is a real Lenovo feedback case, but no English model/Accessibility hierarchy data was captured; long review translation on Lenovo remains NOT RUN.
- Do not silently download models, record the screen or upload user content. No change to beta11 release signing or APK.

## I. Locally signed Lenovo test APK — 2026-11-16

- [x] Signed the **verified PR #22 ARM64 CI artifact** from run `43895561486` using the same existing release signing identity as beta10/beta11. This was a private ChatGPT-chat file, **not** a GitHub release upload.
- [x] Official Android SDK apksigner v2/v3 verification, pinned signer certificate `d0d4e5dfb604917e1896c9a4697f2743296e3fa7b137d5bacefdf7a8c7b519d3`, ARM64 JNI, 16 KiB native alignment, ZIP CRC PASS.
- [x] File: `PDD-Overlay-RU-v0.2.0-beta.11-REVIEWS-INLINE-OCR-ARM64-SIGNED.apk`; SHA-256: `76bca3c1023f3b507b3a7a260b3b46494398e29989c3fa6b0f8fb04562218a0f`; 37,991,374 bytes. Version remains beta11/versionCode 21; signing does **not** mean install has been validated.
- [ ] Verify same-version update over the existing beta11 on physical Lenovo without uninstalling or clearing data.
- [ ] Confirm English→Russian model separately consented and ready, actual long review text readable; verify photo viewer inline Russian captions and original toggle on Lenovo.
- [ ] Measure and reduce intrusive short overlay labels from the original user screenshot in the next scoped refinement.

## Invariants
- Package `app.pinduoduo.overlayru`, minSdk 26, targetSdk 35, ARM64 release only.
- Chinese and English ML Kit model downloads require explicit separate consent.
- No screenshot capture, broad storage permission, payment-label replacement or silent Accessibility grant.
- User dictionary remains local, encrypted PRO license verification is unchanged.
- The old `source_bundle.base64` is historical beta10 input only once the normalized Android tree is committed.

A green JVM build does not prove rendered-screen fidelity or physical-device behavior.
