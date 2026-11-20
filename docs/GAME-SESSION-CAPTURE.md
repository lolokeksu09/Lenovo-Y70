# 3.3.0-game — Wild Rift read-only session capture

## Problem

The device has Motorola GameMode, IMotTouch, MAXE initialization rules and
thermal-engine-v2. Previous readbacks were all taken outside the game.
An empty vendor.motosxf.mode property outside Wild Rift cannot establish what
the OEM requests while an actual game is running.

## Solution

The in-app Motorola gaming screen now has a **Записать Wild Rift · 90 сек**
action. With Shizuku authorized, one action starts a user-visible Android
foreground service and launches com.riotgames.league.wildrift. The service
takes at most 12 lightweight readbacks at an ~8-second interval, with four
extra dumpsys reads near 0/32/64/88 seconds. A notification shows progress
and offers Stop. The Activity also offers Stop and instant Copy.

Each light read observes:
- whether Wild Rift has an active process (pidof; only yes/no stored);
- vendor.motosxf.mode;
- vendor.thermal.mode;
- init.svc.thermal-engine.

At four checkpoints, the system dumpsys moto_extend_service is parsed for:
- registered GameTime touch request (not touch sampling rate);
- registered client touch request;
- GameMode state flag and Motorola Touch HAL connectivity;
- independently reported hardware DPS status.

Raw dumpsys is **not** saved; only compact summary lines are retained in
private SharedPreferences. No network calls or background recurring schedule
are added. A session is stored locally and can be copied in the app after
returning from the game. There is no root requirement if Shizuku shell has
already been authorized.

## Constraints, failure recovery

- No setprop, no thermal service restarts, no kernel/sysfs writes,
  no bypass of signature|privileged permissions.
- A property being game-perf does not establish which config thermal-engine
  has loaded. Even an accepted touch request is not measured latency.
- The service returns START_NOT_STICKY. It is user-triggered and stops after
  the bounded samples, or earlier via UI/notification Stop.
- Individual command failures are counted in the report. The last partial
  report survives process death; a stale running flag older than 3 minutes is
  marked interrupted, not successful.
- Device OEM execution and foreground-service behavior on Android 17 require
  an on-device test; GitHub Actions proves only build/test gates.
- ShellHelper's underlying shell operations may block on a failing OEM service;
  therefore 90 seconds is a nominal session target, not a hard process
  timeout. The application does not keep an indefinite monitoring schedule.

## Manual acceptance

1. Authorize Shizuku for Lenovo Y70; open Motorola gaming screen.
2. Press Start; check notification and that Wild Rift is launched.
3. Play or keep game foreground; return after ~90 seconds.
4. Status must read Completed or a truthful failed/interrupted state.
5. Copy capture; verify ~12 bounded timestamped light samples and up to four
   GameTime snapshots; check property status shown as unset when empty.
6. Repeat with Shizuku disabled: no game auto-launch and explicit denial.
7. Repeat using Stop in the notification: no further samples after cancellation.
8. Confirm OEM thermal/touch properties remain unchanged by the capture.
