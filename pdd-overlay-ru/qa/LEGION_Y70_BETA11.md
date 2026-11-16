# Lenovo Legion Y70 — PDD Overlay RU Beta 11 acceptance

Target: Lenovo Legion Y70 / Motorola-firmware identity, Android 13, ARM64.
Package: `app.pinduoduo.overlayru`, versionCode `21`, versionName `0.2.0-beta.11`.
Do not uninstall Beta 10 first: the production signing identity is unchanged.

## Installation

1. Download and install `PDD-Overlay-RU-v0.2.0-beta.11-ARM64-SIGNED.apk` over Beta 10.
2. Confirm Android accepts an in-place update and retains the user's local dictionary and translation settings.
3. Launch normally. No debug/system privilege, Root or Shizuku is required for translation.

APK SHA-256: `191055071ce444d008e5c9f77df945bb21835537c36f03107b573bca9bfa43a9`
Certificate SHA-256 (unchanged Beta 10): `d0d4e5dfb604917e1896c9a4697f2743296e3fa7b137d5bacefdf7a8c7b519d3`

## Quick functional matrix (real device only)

| Path | Test | Pass condition |
| --- | --- | --- |
| Home | launch app, return from Android settings | Real readiness state and actionable next step |
| Tabs | Home → Photo → Dictionary → Settings → Home; press Android Back | No crash, stale tab, broken back behavior or unexpected duplicates |
| OCR | Pick a non-sensitive Pinduoduo product screenshot, translate detected Chinese, zoom/pan | Offline OCR and displayed translation correspond to actual content |
| Dictionary | Add A and B; edit A source to C; search all three | Old A absent, C present, B unchanged |
| Dictionary collision | Attempt edit C source into B | Rejected, existing B unchanged |
| Dictionary delete | Delete C and cancel once before confirming | Cancel preserves C; confirm removes only C |
| Accessibility | Toggle access in Android, return, enable translation | Real states distinguish connected and merely allowed; Pinduoduo-only overlay |
| Model state | No model, download consent, offline ready | No false green state; no silent model download |
| Font scale | Standard and 1.3× system font | Actions readable, tappable, not covered by navigation bars |

Never test overlay substitution on payment, OTP, or real checkout confirmation.

## Minimal device diagnosis from Termux / Shizuku

Run **only if a defect occurs**. This collects focused application failure logs to a user-owned local file, not server-side:

```sh
cd ~
./rish -c 'logcat -d -s AndroidRuntime:E PddOverlayRU:W' > /storage/emulated/0/Download/PDD-beta11-diagnostics.txt
```

Review the file before sending: logs may include device details. Do not send credentials, orders, payment details or screenshots of personal purchases.

## Validation distinctions

- PASS: 71/71 Gradle JVM tests, source/privacy gate, and ARM64 native library verification in GitHub Actions `43811452976`.
- PASS: APK content ZIP/CRC, 4K shared-library alignment, cryptographically valid APK Signature Scheme v2/v3 using the same pinned certificate.
- NOT RUN: in-place install, font 1.3×, manual OCR and Accessibility inside Pinduoduo on a physical Legion Y70.
- Android 35 GitHub emulator: prior failure was before app launch due to missing KVM access (CI runner environment). The runner permission fix is tested separately and should not be counted as a device validation.
