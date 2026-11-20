# MAXE / Thermal Engine — read-only probe

Hardware evidence from Lenovo Legion Y70 (Motorola `avr_g`, Android 17):
- `/vendor/etc/init/init.maxe.rc`: `vendor.motosxf.mode` is copied to `vendor.thermal.mode`.
- `/vendor/etc/init/init_thermal-engine-v2.rc`: property trigger stops and starts `thermal-engine`.
- `thermal-engine-v2`, `perfservice`, Motorola Touch HAL and Qualcomm perf2 were observed running.
- `/vendor/etc/thermal-engine-avr-game-perf.conf` exists and contains thermal action tables.
- Both properties were empty in the user's readback; active config **not established**.

## Scope of the feature

The existing **Motorola gaming functions** screen reads three properties and
the **existence** of the `game-perf` configuration using the already-authorized
Shizuku/Root shell path. The readback is bundled into the existing copyable
diagnostic report. Access errors and incomplete responses are displayed
independently of Touch Boost / Bypass state.

This feature NEVER sets a vendor property, restarts thermal-engine, changes
thermal limits, requests a protected permission or claims which configuration
is currently loaded. A present config file does **not** prove that it is
selected, and `vendor.thermal.mode=game-perf` alone does not prove it is loaded.

## Validation

- Pure JVM test: `MotorolaThermalStateTest`.
- Static integration check: `tests/check_maxe_thermal.py`.
- Android compilation: existing GitHub Actions `build-audit.yml` workflow.
- **Not verified** on a physical Y70 until a user installs the built APK.
