# Legion Control v1.3.0 — changes and design notes

## User-facing changes

1. AMOLED native view hierarchy with fixed five-tab bottom navigation and 48+dp interactive targets.
2. Two quick profiles modify exactly 5 settings atomically: min/peak refresh rate and window/transition/animator duration scales. The original values are staged in SharedPreferences *before* any shell write, and verified on rollback.
3. Explicit AppOps `default` reset per supported app; all app changes remain reversible.
4. Busy state prevents repeated actions while a Shizuku command is running; disabled controls remain disabled.
5. The activity retains its active tab on configuration recreation; no static fake performance/thermal metrics added.

## Visual direction

- Use: one-handed advanced settings, dark environment, quick readability.
- Base `#060608`; surface `#151518`; raised surface `#202025`; outline `#303038`.
- Foreground `#F4F3F7`; secondary `#BAB8C4`; action `#D1C3FF`; success `#94EBC6`; warning `#FFCC91`.
- Type: Android sans-serif (native text scaling), section title 23sp, body 15sp, actions 14sp. No new font dependency.
- Navigation: fixed equal-width 5 destinations, native system Back preserved.

## Constraints

- Fixed `applicationId=dev.legion.control`, versionCode=4, versionName=1.3.0.
- Permanent Release signing certificate SHA-256: `c912747bc79e33adfc327817e36c667719937de59dcad1c22bf154e44bb56f5a`.
- No package disable/uninstall, bootloader changes, thermal overclock, forced FPS, artificial temperature readings, or overlay tampering outside installed fonts.
- Scope of local tests: pure Java command/transaction/profile invariants; Android build and signature must be verified in CI, hardware behavior requires device testing.
