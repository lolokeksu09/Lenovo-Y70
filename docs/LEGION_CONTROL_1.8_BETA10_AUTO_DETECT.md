# Legion Control 1.8.0 Beta 10 — Automatic Accessibility Eligibility

## Root cause

Beta 8/9 correctly enumerate installed/enabled Accessibility services through public `AccessibilityManager`, but cannot reliably call non-SDK `isAccessibilityTargetAllowed(package, uid, userId)` from the normal application's process. On the Lenovo Y70 XT2611-1 with Motorola Android 17 ROM, Kimi Claw (greyed out by Restricted Settings) and SCENE (ordinary off, enabled switch) both remained `DISABLED_UNVERIFIED`.

## AOSP investigation

- `AccessibilityManagerService.isAccessibilityTargetAllowed(String packageName, int uid, int userId)` checks permitted Accessibility services from DevicePolicyManager; if allowed, it also checks Enhanced Confirmation Mode (or legacy app-op restrictions). The system server Binder method is annotated `@RequiresNoPermission` in AOSP master.
  https://android.googlesource.com/platform/frameworks/base/+/master/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java
- Android 15+ ECM uses `ACCESS_RESTRICTED_SETTINGS` as an explicitly set state, but `MODE_DEFAULT` means the service runs a *lazy heuristic*. A mere `cmd appops get PACKAGE ACCESS_RESTRICTED_SETTINGS` therefore does **not** establish that the app is allowed in Android 17.
  https://android.googlesource.com/platform/packages/modules/Permission/+/b27b1901d/framework-s/java/android/app/ecm/EnhancedConfirmationManager.java
- Shizuku UserService executes Java code as the authorized shell UID and does not enforce the usual non-SDK reflection restrictions; however, the device may still reject a Binder call or implement a different service interface.
  https://github.com/RikkaApps/Shizuku-API

## Beta 10 implementation

1. Continue to read installed/active Accessibility service lists via Android public APIs. Record their actual `ApplicationInfo.uid`.
2. When an installed service is disabled and its restriction status remains unknown, bind an **independent** non-daemon Shizuku UserService (tag `legion_access_probe`); this prevents unbinding the main app's command service.
3. UserService obtains Android's `accessibility` Binder using `SystemServiceHelper`, invokes `IAccessibilityManager.isAccessibilityTargetAllowed(package, uid, userId)` reflectively, and returns a minimal response containing only `ALLOW/ BLOCKED` and the queried package name.
4. The app accepts only responses for packages it just queried, ignores conflicting/malformed records, and prioritizes the system verdict above any earlier user-supplied state.
5. State meaning:
   - **ENABLED**: the Accessibility service is active.
   - **DISABLED**: the system says the app's Accessibility target can be enabled, but it is off.
   - **RESTRICTED**: the system denies enabling (ECM or administrator policy; not necessarily ECM specifically).
   - **DISABLED_UNVERIFIED**: Binder call absent, denied or incompatible; manual check remains available.
6. Auto-checked count and provenance are shown in the summary, service details, and user-initiated diagnostic TXT.
7. No Accessibility enablement, settings/AppOps changes, privilege grants, automated restricted dialog invocation, or other access bypass. Queries execute in the existing worker with a 12-second overall timeout.

## Controls

- `ICommandService` adds only `queryAccessibilityTargets(in String[] packages, in int[] uids, int userId)`.
- UserService rejects invalid package names, invalid or inconsistent UIDs, more than 64 targets, wrong user IDs. Returns an explicit unavailable response on errors.
- `AccessAutoProbe` is pure Java and contains a strict parser. Tests include a fake Kimi-blocked/SCENE-available response, malformed and contradictory results, unknown UID and unavailable Binder.
- No sensitive permissions added to AndroidManifest. The normal app still requires an explicit existing Shizuku grant.

## Status / acceptance

- Source implementation completed; existing code and permanent signing kept.
- `dev.legion.control`, `versionCode 19`, `versionName 1.8.0-beta10`, same Release certificate.
- **Not yet verified:** whether the Motorola ROM exposes `IAccessibilityManager.isAccessibilityTargetAllowed` to a shell process, and whether the actual Kimi and SCENE results correspond to the Android Settings screen. On failure the status must remain unknown.
- Physical device validation: install Beta 10 over Beta 9, launch Shizuku, open "Система → Доступ приложений", refresh, inspect Kimi and SCENE. Collect the built-in diagnostic TXT to distinguish `checked N` from `API_NOT_AVAILABLE`.

**Do not label fully automatic eligibility as device-verified or announce a final 4PDA release until both Kimi and SCENE agree with Settings on the physical device.**

## Verified build evidence — 2026-11-14

- GitHub Actions: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43593196500 — **success**.
- Build source revision: `200048cf1333d78f6189822af291e4698ec06fe3`; any subsequent documentation-only commit does not modify APK source.
- `AccessAutoProbeTest`: 17 checks PASS; `AccessEvidenceTest`: 20 checks PASS; remaining repository JVM tests PASS.
- Android SDK 35 / Gradle Release: **BUILD SUCCESSFUL**.
- Stable pinned Release certificate and `apksigner verify`: **PASS**.
- `aapt` APK identity: `dev.legion.control`, code `19`, version `1.8.0-beta10`.
- Exact APK: `Legion-Control-v1.8.0-beta10.apk`, **254599 bytes**; SHA-256: `743a79ba2f9f10bca14b698e7c0ffa1147b865e7ef404c1c9d790a67b3c27649`.
- ZIP/APK integrity: PASS; native libraries: 0.
- **NOT RUN:** real-device Binder and UI validation on Lenovo XT2611-1 / Motorola ROM. If the shell-accessible AIDL method differs, status remains `DISABLED_UNVERIFIED`; never claim verified automatic detection solely from CI.
