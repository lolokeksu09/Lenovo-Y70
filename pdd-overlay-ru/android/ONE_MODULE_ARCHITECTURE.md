# PDD Overlay RU — single-module Compose migration

- Production Gradle module: `:app` only (no `:core`, `:feature-*` yet).
- Kotlin + Jetpack Compose: `HomeActivity`, `HomeViewModel`, `ui/home`.
- Existing `MainActivity` (Java) stays as detailed **Settings and diagnostics** and is not exported.
- `LicenseActivity`, `LicenseManager`, `LicenseTokenVerifier`, `TranslationEngine`, `PddAccessibilityService` remain unchanged.
- Same application ID `app.pinduoduo.overlayru`, same preferences, same accessibility service component and icon.
- Single primary action: detects Pinduoduo installation, Accessibility enabled vs connected, offline Chinese model ready/pending/overdue; starts translation and opens Pinduoduo only when ready. Never auto-grants permissions.
- Free translation does not depend on license or network.
- Version: `0.2.0-beta.2` / `versionCode 12`; intended as migration preview, not automatically signed.
- A later multi-module split may move `ui/home` and domain logic to `:feature:home` and translation to `:core:translation`, after runtime regressions pass.

## Verification

`bash scripts/test_local.sh`

`gradle :app:testReleaseUnitTest :app:assembleRelease`

Tests verify readiness precedence and no duplicate model requests; device validation still required for Settings return, Accessibility connection, real ML Kit/overlay behavior, licensing and update signing.

## Kotlin core migration

- TranslationPolicy, OverlayPlacement, OverlayCoordinates and OverlayUpdatePolicy are native Kotlin objects with Java-compatible @JvmStatic entry points.
- Their Java main-method regressions are replaced with Kotlin/JUnit tests.
- MainActivity, TranslationEngine, PddAccessibilityService and licensing remain unchanged until their behavior is tested independently.
- Android applicationId, versionCode 12, certificate configuration and the single Gradle module remain unchanged.

## Kotlin runtime migration (phase 3)

- TranslationEngine and PddAccessibilityService now compile from Kotlin sources.
- Both retain their Java-callable public names, methods and PREF_* constants.
- ML Kit model downloads remain explicit and single-flight, with timeout and late callbacks.
- Accessibility translation remains Pinduoduo-only; the text layer is non-touchable.
- Existing MainActivity, license classes, signer, versionCode and :app build are unchanged.

## Kotlin advanced settings migration (phase 4)

- MainActivity is Kotlin and preserves the original imperative Android layout, Accessibility permission checks, manual model consent, settings persistence, model inspection and optional PRO navigation.
- Java-free supporting helpers: AccessibilityServiceIds, DownloadAttemptGate, ModelAvailability, ModelDownloadConditionsFactory, EnglishUiGlossary, Glossary.
- Existing Java license transport, token verifier and license screen are intentionally unchanged pending separate security-focused migration.
- No applicationId, certificate configuration, versionCode or module changes.

## Complete Android application Kotlin migration (phase 5)

- LicenseTokenVerifier, LicenseManager and LicenseActivity are Kotlin.
- Java-callable verifier API and the old Java/JUnit RS256 regression tests remain intact.
- Release trust anchor, HTTPS transport, request body limits, single-flight operations,
  signed-lease cache, 7-day limit, renewal policy and SharedPreferences keys are unchanged.
- Accessibility/ML Kit text remains entirely separate from licensing transport.
- The entire app/src/main/java source folder now contains Kotlin code only;
  Java language may remain in JVM test fixtures and historical build tooling.
- ApplicationId, versionCode 12, manifest entry points, Gradle module, and signing
  configuration remain unchanged. Device deployment is not established by CI.
