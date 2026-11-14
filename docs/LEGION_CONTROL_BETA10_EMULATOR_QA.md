# Legion Control 1.8.0-beta10 — Android emulator QA

Scope: **real APK installation and UI interactions, not source-only tests**.

## Signed artifact under test

- GitHub Release CI run: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43593196500
- Exact APK: `Legion-Control-v1.8.0-beta10-release.apk`
- SHA-256: `743a79ba2f9f10bca14b698e7c0ffa1147b865e7ef404c1c9d790a67b3c27649`.
- Package `dev.legion.control`, versionCode `19`.

## Test environment

- GitHub Actions `ubuntu-latest`, hardware-accelerated Android Emulator.
- Google APIs x86_64, **Android 15 / API 35**, emulated Pixel 5 hardware profile, 1080x2340 window.
- This environment **does not reproduce Motorola Android 17 firmware** on the user's Lenovo Y70 XT2611-1.

## UI smoke — PASS 8/8

Actual run: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43598932518

- **PASS** install signed APK via `adb install -r` and verify versionCode 19 in package manager
- **PASS** cold-start `MainActivity` via `am start -W`, observe visible Android UI
- **PASS** tap `Система` tab via UIAutomator bounds
- **PASS** tap `Открыть центр доступа`, observe Android Accessibility inventory
- **PASS** tap `Проверить снова`, observe `Проверка №1: ... Изменений нет` (3 services; ~85–102ms)
- **PASS** tap **actual** `Самопроверка кнопки` button, confirm counter increments to 1
- **PASS** tap `Копировать отчёт`, app remains active (not a validation of clipboard content)
- **PASS** capture screenshots and UI hierarchy

Full evidence: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43598932518/artifacts/14115871501

**Important:** That workflow's overall job is marked **FAIL** because a *separate Shizuku integration step* could not launch Shizuku v13.6 on the emulator. Its official manager APK installed, and the Legion permission was granted via `pm grant`, but the test launcher command was parsed incorrectly, yielding `NO_LAUNCHER`. This does NOT invalidate the 8/8 earlier UI checks.

The Shizuku launcher command was corrected and independently tested on Android Emulator:
https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43599526358

## Shizuku / Android Binder integration — PASS

- **PASS** official Shizuku 13.6.0 APK installed on the emulator.
- **PASS** ADB shell native Shizuku starter launched; log confirmed `shizuku_server pid is 2484`, starter exited 0.
- **PASS** Legion Control Beta 10 relaunched with a Shizuku API grant.
- **PASS** Accessibility Center read real service inventory (3 services) and displayed
  `Shizuku: автоматически проверено 2 приложений`.
- **PASS** the resulting state summary reported 3 services available for enabling, none restricted; this only tests the *allowed* path.
- **PASS** isolated GitHub Actions integration run **completed successfully**.
- Evidence: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43599526358/artifacts/14115716542

## Remaining gates

- **Unverified:** the **blocked** verdict path through the actual Android Binder service; the emulator returned only allowed results.
- **Unverified:** the Android 17 / Motorola ROM Binder interface and compatibility, especially ECM-specific restrictions.
- Kimi Claw restricted versus SCENE allowed-off state: cannot be tested without realistic installed services and the user's Motorola Android 17 ROM.
- Actual user device touch events, ECM and Motorola Settings UX: NOT RUN.
- Do not claim the APK is fully device-verified for 4PDA until physical device testing passes.

## Reproducibility

`.github/workflows/emulator-smoke-beta10.yml` runs the signed APK UI smoke test and optional official Shizuku integration test. `.github/workflows/emulator-shizuku-beta10.yml` isolates Shizuku ADB testing. Both collect screenshots, dumps and AndroidRuntime logcat on failure. No privileged Accessibility settings are modified.
