# Legion Control 1.7.0

A native Android utility for Lenovo Legion Y70 (`XT2611-1`) running Motorola firmware. Requires Shizuku (ADB Shell); root is not required.

## Features

- Device summary separates Lenovo hardware SKU from Motorola ROM identity.
- Read-only SoC, Android, power-saving and configured refresh-rate values; configured min/peak are **not** measured current Hz or FPS.
- Motorola module and font overlay checks with structured, human-readable results.
- Wild Rift GameManager status, user-controlled display and animation profiles.
- Selected AppOps background permissions; local write-ahead snapshots and last-change undo.
- Persistent 25-event history, exported only by explicit action.
- AMOLED design with top navigation and custom native detail sheets.

## Supported environment

Minimum Android SDK 28 (Android 9), Shizuku API. Primary intended hardware: Legion Y70 new generation / XT2611-1 with Motorola Edge 70 Max firmware. Other devices and ROM configurations are not verified.

The APK requests no Internet permission, does not delete packages or change thermal/CPU/GPU kernel limits, and does not request root. Some changes can affect notifications or system behavior; the user must confirm them. System temperature reports may be unreliable with nonnative firmware.

## Build

Run Gradle `:app:assembleRelease` using the configured Android SDK and Java 17. Repository GitHub Actions additionally signs with a permanent Release certificate stored in protected Secrets and checks certificate SHA-256. **Do not place private signing keys into this source package.**

Package `dev.legion.control`, versionCode `9`, versionName `1.7.0`.

## Validation

JVM unit tests cover command allowlist, transaction restoration, device parsing, system inspector, quick profiles and action history. Android CI builds and verifies release signing. UI interaction on actual hardware must be validated before claiming full production readiness or publishing final screenshots.
