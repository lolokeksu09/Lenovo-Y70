# Motorola GameTime — Binder connection recovery

## Confirmed limitation before this patch

`MotoGameController` cached a Java `MotoExtendManagerCore` without checking if
the underlying `moto_extend_service` Binder remained alive. An OEM service
restart could leave the cache referring to a dead Binder.

## Changes

- Cache the original `IBinder` together with the official Motorola Core.
- Validate cached Binder liveness before each reuse.
- Register `IBinder.DeathRecipient`; ignore old death notifications when a
  different Binder has already been installed.
- Detect `DeadObjectException` through wrappers such as
  `InvocationTargetException`, invalidate the cached Core, and prefer a
  fresh service connection on the **next** operation.
- **No automatic replay** of Touch Boost or Direct Power Supply mutations:
  a Binder failure may occur after an operation was accepted but before its
  reply. The user sees a specific error and is asked to recheck hardware status.
- Permission errors and unsupported hidden APIs are **not** treated as a
  Binder restart.

## Evidence / limitations

- `MotoExtendManagerCore` and `moto_extend_service` were previously found on
  this device's firmware; root not required for readback through Shizuku.
- Whether the reported historical Touch Boost process restart was actually
  caused by Binder death is **unknown**. No crash log established that cause.
- Does not bypass signature|privileged permissions or modify thermal limits.
- Test the binder classification with pure JVM tests; verify the Android build
  with CI. A physical Y70 regression remains required before claiming a
  device-level fix.
