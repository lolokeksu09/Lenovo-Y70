# Legion Control 1.8.0 Beta 5 — Button response audit

Status: source fixes committed, Android runtime verification pending.

## Reproduced source defects

1. **Shizuku connection button:** previously called `requestAccess(); updateConnection();`. If the service was already connected, the UI stayed identical and provided no confirmation. Changed to `checkShizukuConnection()` with an explicit success dialog or connecting feedback.
2. **Shizuku command first tap:** `executeAsync()` silently returned after requesting permission or binding when the remote service wasn't ready. It now informs the user that the action must be repeated after connection; it does **not** queue a privileged write to happen unexpectedly in the future.
3. **Navigation locked during an unrelated operation:** tabs silently dropped clicks when `busy`; the System tab's Access Center button also used the command gate. Navigation is now independent of Shizuku command execution.
4. **Technical details:** the disclosure toggled a hidden text view at the bottom of the scrollable sheet but didn't scroll to it. The button now changes label and scrolls to the expanded content.
5. **Access Center refresh:** when the system state was unchanged, `refresh()` showed identical content, appearing unresponsive. Displays the latest read time (HH:mm:ss).
6. **Last-change undo:** when no snapshot existed, the UI displayed a disabled button. Now a readable explanation replaces the inert control.
7. **In-flight operations:** the Shizuku connection status displays a running state while a shell command is executing.

## Safety and known limitations

- UI navigation can continue while a Shizuku operation runs; new writes remain gated, preserving the existing single-flight guard.
- The app never silently retries a previously confirmed privileged write after an asynchronous permission grant. The user explicitly repeats it.
- No hidden Accessibility permission toggles, no Expanded Confirmation/Restricted Settings bypass.
- Restricted/unknown accessibility service states from Beta 4 remain distinct.
- Some service buttons could still fail for ROM-specific permissions. A screenshot, action label, and logcat from the exact device are required to prove or fix those failures.
- JVM tests and Gradle compilation cannot prove touch event dispatch on physical Motorola Android 17.

## Release

- Package: `dev.legion.control`.
- `versionName=1.8.0-beta5`; `versionCode=14`.
- Permanent release certificate pinned by existing CI.
- Verify the build, certificate, APK version and in-app navigation before calling 4PDA release ready.

## Manual device smoke test

1. Open `System → Access Center` while Shizuku is disconnected and connected.
2. Tap `Check Shizuku connection` twice: success/connecting feedback should be visible.
3. While reading device data, move between tabs: navigation should respond; writes remain disabled.
4. Open a structured readout; reveal `Technical details`. Expanded output should scroll into view.
5. In Access Center press `Check again`. Timestamp should update even if the service status stays unchanged.
6. In History without any applied changes, check the Undo button is absent and a reason is shown.
7. Confirm last-change undo, profiles and GameManager still behave correctly.
