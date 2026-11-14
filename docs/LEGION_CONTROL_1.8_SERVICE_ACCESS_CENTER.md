# Legion Control 1.8.0 — Service Access Center

Status: **Beta 1 built (2026-11-14)**. Phase 1: Accessibility inventory and user-guided recovery; not yet tested on the target handset. Target: Lenovo Legion Y70 (XT2611-1) with Motorola Edge 70 Max ROM, Android 17, no root, Shizuku (ADB Shell). Scope extends to other Android devices on a best-effort basis with explicit unknown states.

## Problem and constraints

On Motorola ROM, some Accessibility services are shown as *controlled by restricted settings*. A greyed out service is **not** proof that this is the cause: it may be disabled for other reasons. The 2026-11-14 screenshot shows "PDD · Перевод экрана" **enabled** and SCENE / Shizuku accessibility entries controlled by restricted settings. Shizuku's own accessibility entry is **not required** for the existing ADB-backed Shizuku UserService connection. Never imply otherwise.

The app must not automatically enable Accessibility or silently bypass Restricted Settings, change app-ops that gate sensitive permissions, or suppress Android security prompts. Only user-initiated official Settings navigation is allowed.

### Google-supported manual pathway

1. Inspect which installed Accessibility services exist and which are enabled.
2. From a disabled service's details, open **App info** for that exact package.
3. Only if the user trusts the app and Android shows the menu item, use **More (⋮) → Allow restricted settings**. The option may be absent for reasons unrelated to the application's state.
4. Open **Accessibility Settings**, then choose the service and enable it manually.
5. Return to Legion Control; re-read the actual enabled-service list. Never assert "restriction removed" because an intent launched successfully.

Official Android references:
- https://support.google.com/android/answer/12623953
- https://developer.android.com/reference/android/view/accessibility/AccessibilityManager
- https://developer.android.com/reference/android/provider/Settings

## User experience

Entry point: **System** tab → **Доступ приложений**. Keep five tabs, original AMOLED/brand palette and top navigation. Entry opens a dedicated non-exported native Activity.

Screen:
- Header "Доступ приложений", back arrow, concise scope warning.
- User-triggered "Проверить доступы" refresh.
- Only real services from `AccessibilityManager.getInstalledAccessibilityServiceList()` (installed) and `getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` (active).
- One row per installed service: service label, application package, "Включена" / "Выключена" / "Не удалось проверить".
- Empty state for no returned services; error state for inaccessible manager or API exceptions.
- Tap row → details with application label, service, enabled status, and note "Ограничение Android автоматически не определяется".
- If enabled: show status and **no remediation prompt**. If disabled: display manually guided steps including trust/security warning.
- Buttons: **Информация о приложении**, **Специальные возможности**, **Проверить снова**. No fake 'Fix' button; no hidden Shizuku commands.
- Back goes to the list. `onResume` re-reads when returning from Android Settings. Preserve selected service when possible.

The app uses `ACTION_APPLICATION_DETAILS_SETTINGS` with a `package:` URI and `ACTION_ACCESSIBILITY_SETTINGS` for navigation. Handle missing or blocked settings activities gracefully. UI uses >=48dp tap targets, system bars and scroll, Russian strings, no raw shell output or guessed statuses.

## Implementation architecture

- `AccessStatus.java`: pure-Java normalized, testable service identity and enabled-state policy; no guess about restricted status.
- `AccessRepository.java`: Android API adapter for installed/enabled Accessibility service lists; graceful errors and app/service names.
- `AccessCenterActivity.java`: standalone, private native screen; list/detail UI and user-driven Settings wizard, no network, no root, no Shizuku requirement to read standard AccessibilityManager APIs.
- `MainActivity.java`: one entry card in existing "Система" tab.
- `AndroidManifest.xml`: register non-exported Activity. No Accessibility service declaration, no privileged permissions.
- `AccessStatusTest.java`: enabled/disabled/unknown logic, duplicate IDs, and app-id validation.

## Phase plan / acceptance gates

### Phase 1: Functional beta
- [x] Commit documentation and design constraints.
- [x] Implement inventory and status normalization with negative tests.
- [x] Implement native list/details/wizard + safe Settings navigation and empty/error states.
- [x] Link from "Система"; preserve back navigation and all existing tabs.
- [x] Run pure-Java focused tests plus existing tests.
- [x] CI build signed APK, check stable certificate and `dev.legion.control` / versionCode 10.

### Phase 2: Capability diagnostics
- [ ] Check Overlay / Notification Listener / Background constraints only through supported APIs; report "unknown" when status cannot be read.
- [ ] Add accessible app icons and search/filter if confirmed useful.
- [ ] Determine whether more specific Settings navigation is supported on this Motorola ROM; fall back safely.
- [ ] Capture device screenshots and test manual Restricted Settings flow on XT2611-1. **Never fake a restricted-status detector.**

### Phase 3: 4PDA release hardening
- [ ] Validate Android 17/ROM behavior on physical phone, dark AMOLED UI, font scale and 48dp touch targets.
- [ ] Check Shizuku connection remains unchanged, undo/history works.
- [ ] Package accessible release notes, screenshots, SHA-256, compatibility/limitations, APK.
- [ ] Release stable 1.8.0 only after physical tests.

## Beta 2: missing `Allow restricted settings` entry on Motorola ROM

**User-observed symptom:** On Lenovo Legion Y70 with Motorola Edge 70 Max Android 17 ROM, the `Allow restricted settings` option is absent from an application's App Info page, despite the Accessibility service appearing restricted.

**Underlying Android mechanism:** AOSP EnhancedConfirmationManager uses `isClearRestrictionAllowed(packageName)` to determine whether App Info should offer this menu item. In this flow, attempting to open the blocked Accessibility service first shows a system "Restricted setting" warning; the system warning makes the app eligible for user confirmation. Different ROM builds may fail to expose the menu even after the warning. This is a possible ROM integration issue, not a proven diagnosis.

**New user flow in Beta 2:**
1. Open Android Accessibility and *tap the disabled service* to trigger its Restricted Settings warning.
2. Return to the system **Settings → Apps → All apps → [affected app]** list (not a deep link to App Info from Legion Control).
3. Look for the three-dot menu `Allow restricted settings`.
4. If missing, use the **`Пункт отсутствует — что делать?`** disclosure; the app explains the expected ECM trigger, permits opening the system Apps list, and offers rechecking the service state.
5. If absent after the documented sequence, report a likely Settings/ECM integration limitation and collect diagnostic evidence; never pretend a direct Settings intent or an AppOps read fixes it.

Android 17 compatibility definition requires user confirmation for restricted settings to originate in the target app's AppInfo with EnhancedConfirmationManager; do not present a silent Shizuku/ADB AppOps write or Android Secure Settings modification as the official or safe equivalent. The fallback is **instructional and diagnostic**, not a privilege bypass.

Refs: https://android.googlesource.com/platform/packages/modules/Permission/+/b27b1901d/framework-s/java/android/app/ecm/EnhancedConfirmationManager.java ; https://source.android.com/docs/compatibility/17/android-17-cdd ; https://android.googlesource.com/platform/packages/apps/Settings/+/master/src/com/android/settings/applications/appinfo/AppInfoDashboardFragment.java .

**Beta2 build:** `dev.legion.control`, versionCode `11`, versionName `1.8.0-beta2`, same pinned release certificate; physical-device verification still required.

## Beta 3: direct navigation to selected app (2026-11-14)

**Reported UX defect:** A user viewing a disabled Accessibility service (e.g. Kimi Claw) chose the missing-menu help and landed in **All apps**, then had to search for the same application manually.

**Fixed behavior:**
- The service details wizard now offers **"Настройки [application label]"**.
- Selecting **"Пункт отсутствует — открыть [application label]"** immediately launches the selected package's App Info through the public `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` intent and `Uri.fromParts("package", packageName, null)`. The app package is taken from the selected service, not from a guess or string built from its label.
- If the system does not support that intent, the safe fallback opens the generic Apps list. This fallback may still require manual selection — it is an OS limitation, not a silently completed action.
- After returning, the detail page remains on the same service and re-reads its enabled state. If the menu is still missing, additional diagnostic guidance is available, including the alternate manual Apps-list route.
- Android may hide the "Allow restricted settings" item on directly deep-linked App Info screens on some ROMs. This action improves navigation but does **not** guarantee that the option appears; the manual app-list route remains an explicitly labeled fallback.
- The app never grants Accessibility permissions by itself.

Official Android API: https://developer.android.com/reference/android/provider/Settings#ACTION_APPLICATION_DETAILS_SETTINGS

**Version:** `1.8.0-beta3`, versionCode `12`, release signing fingerprint unchanged. Hardware verification remains pending.

### Beta 3 build verification

- GitHub Actions run: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43582604588 — **PASS** for JVM policy tests, Android build, APK signing and certificate pin.
- Package: `dev.legion.control`, versionCode `12`, versionName `1.8.0-beta3`.
- Artifact: `Legion-Control-v1.8.0-beta3-release.apk`; 221,831 bytes.
- SHA-256: `e9583ecba47e58d44cacd62d6d5d620fbdca87c3692162a868c9540a49559e7a`.
- Zip integrity: PASS; native `.so` binaries: 0.
- **NOT RUN:** direct Settings UI navigation on the physical Motorola ROM; actual visibility of the Restricted Settings menu.

## Security / release invariants

No `WRITE_SECURE_SETTINGS`, no accessibility toggling, no changes to `ACCESS_RESTRICTED_SETTINGS`, no hidden app-op writes, no shell injection for arbitrary packages. Opening Settings does not prove that permissions were granted. Existing Shizuku allowlist remains unchanged. The Activity is `exported=false`. No internet permission or analytics. No private signing material in source.

New first-phase APK uses the existing release keystore and package `dev.legion.control`, versionCode 10. First build should be labeled `1.8.0-beta1` until real-device testing.

## First implementation evidence

- Branch: `feat/legion-control-v1.8.0`; first beta's build commit: `b578cd4e7e72fa8e965bfaf70617b2de5c350322`.
- GitHub Actions: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43575293821 — PASS (JVM tests, Android Gradle, pinned release certificate, APK export).
- Artifact name: `Legion-Control-v1.8.0-beta1-Release-Signed`, API 28+, `dev.legion.control`, versionCode 10, versionName `1.8.0-beta1`.
- Exact unpacked APK SHA-256: `9754d5b724bbab6d8c7e446d60eee1a3442a6b600f6ca643abe10d6e5ad930b7`.
- **NOT RUN:** handset installation, accessibility list comparability with the Motorola ROM screenshot, user-initiated settings wizard, real screenshots and accessibility screen-reader evaluation.
- Do not publish as a fully validated 4PDA release until these checks pass. Technical source/JVM checks do not establish runtime behavior.
