# Calm Legion Game Center — approved implementation specification (v3.7.0-game)

Status: user-approved on 2026-11-21. This is the canonical scope for the LenovoMotorolovichY70 Games tab.

## Scope
- Retain Java/XML architecture, com.moto.resolutionmaster, four bottom tabs, 64dp fixed navigation and every working OEM/Shizuku feature.
- Calm AMOLED style: base #050506; cards #14151A; separators #242730; primary Legion red #EE485A; confirmed green #2CB794; warnings #EAB969. No rainbow glow, heavy outlines or giant metrics. Hero may use a very restrained gradient; all other sections neutral. 16dp horizontal inset, ~12dp vertical spacing, minimum 48dp action height.
- Display real states only; no hard-coded game performance percentages, FPS, sensor sample rate, Bypass or Touch Boost state.

## Ordered sections
1. Hero: original gamepad vector, Wild Rift/GameTime title. Buttons to open existing MotorolaGameActivity and to start installed Wild Rift with missing-app handling.
2. Power + Touch Boost: balanced compact cards. Battery %/temperature/charging from PowerHelper; Bypass only as known hardware status. Touch card shows real Motorola direct Binder access and opens the OEM gaming module, not an invented switch.
3. Diagnostics: three actions for starting the EXISTING bounded 90-second game state capture, opening current system diagnostics, and copying the locally saved game-session report. Explicitly not video capture or FPS/sensor-rate measurement.
4. Quick actions: actual GameTime, Display, Battery Care/power and diagnostics controls. Every button has a real listener; omit unsupported actions.
5. My games: horizontally scrolling list of genuinely installed, launchable games discovered by PackageManager game classification and explicit Wild Rift support. Use actual app names/icons and launchable intents; avoid invented titles or optimization badges. If no games can be detected, hide the section.

## Safety/implementation
- No new root, Binder bypass, OEM privilege escalation, thermal writes, or background monitoring.
- Unknown and permission-denied remain distinct from verified success. A running game PID does not prove foreground.
- Never restore deleted mouse/trackpad pointer-speed UI/function.
- Keep all existing Android view IDs/handlers, existing app author credits and non-games tabs.
- Update README/manifest to 3.7.0-game versionCode 55; extend CI with static/native routing tests and signed debug APK compilation.

## Acceptance
- Five sections compact, no truncated labels at normal font scale, bottom tabs fixed, all real actions functioning.
- Missing game or denied Shizuku produces meaningful feedback and no fake success.
- UI regressions plus Android CI pass; compare APK certificate when available.
- Physical Y70 runtime/screenshot checks remain necessary after installation.

## Explicit exclusions
No invented screen video recording, 60 FPS capture, real sensor latency, game acceleration or game optimization functions where app lacks supported implementations.
