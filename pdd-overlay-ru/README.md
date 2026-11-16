# PDD Overlay RU

Russian-first offline overlay translator for the original Pinduoduo application, package `app.pinduoduo.overlayru`.

## Current development: v0.2.0-beta.11 (stabilization)
- Android Studio / Gradle sources: [android/](android/) (created by the stabilization workflow after test-gated bootstrap).
- Older release resources: [releases/](releases/).
- Product and visual design: [design/premium-amoled-3d/DESIGN.md](design/premium-amoled-3d/DESIGN.md).
- Change staging and one-time migration: [beta11/](beta11/).
- Build workflow: [pdd-stabilization-beta11.yml](../.github/workflows/pdd-stabilization-beta11.yml).

The legacy `source_bundle.base64` contains the **beta10** archive. It is not the canonical editable source after `android/` is committed. Do not run old beta10 migration scripts against the beta11 tree.

### Local verification
```sh
ln -s pdd-overlay-ru/android pinduoduo_overlay_ru
bash pinduoduo_overlay_ru/scripts/test_local.sh
gradle -p pdd-overlay-ru/android :app:testReleaseUnitTest :app:assembleRelease
```

Release builds made without the private keystore are unsigned and **not installable as updates** over signed builds. Preserve the installed app data and signing key. No testing on a physical Lenovo Legion Y70 is claimed.
