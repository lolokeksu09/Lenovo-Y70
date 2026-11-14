# Legion Control 1.2.0 — safety and recovery

Based on verified stable `legion-control-v1.1.0`, package ID `dev.legion.control`.

- System setting mutations use a write-ahead journal in private SharedPreferences.
- Snapshots are committed before the first Shizuku shell mutation.
- Successful completion atomically replaces the last undo snapshot and clears the pending marker.
- If verification fails, rollback is attempted; a failed rollback leaves the pending marker for recovery.
- A recovery panel remains accessible after app process death/restart.
- New mutations and prior undo are blocked while a pending recovery exists.
- Game mode, overlays, AppOps, refresh rate, and animations all use this transaction path.
- Read-only inspection of disabled packages via `pm list packages -d`.
- 39 command-policy checks plus 15 mutation-state assertions run as plain JVM tests.
- Stable APK uses versionCode 3 and versionName 1.2.0; the existing pinned signing certificate must match.

## Known limits

- No phone-side test, no proof of hardware/RRO support beyond reported shell commands.
- Restoring a font overlay may not reproduce a user-specific theme's complete state.
- Other tools/system services may change the same settings between snapshot and rollback.
- No automatic rollback after kernel reboot; users must reopen the app and select recovery.
- Stable release signing secrets remain on GitHub and are never included in source archives.