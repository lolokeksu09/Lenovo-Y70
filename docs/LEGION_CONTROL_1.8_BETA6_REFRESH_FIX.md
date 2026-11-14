# Legion Control 1.8.0-beta6 — Accessibility refresh feedback

## Report
On the target Lenovo Legion Y70 / Motorola Android 17 ROM, tapping **Проверить повторно** in **Система → Доступ приложений → [service]** appeared to do nothing in Beta 5.

## Root cause in the code
The click handler did call `refresh(false)`, but the implementation:
- synchronously called `AccessRepository.read(this)` on the UI thread, potentially blocking the UI during Android Binder/PackageManager reads;
- rebuilt the entire screen without any operation feedback;
- displayed only a timestamp at the top of the page, often outside the viewport where the button was pressed;
- produced an identical screen if no service had changed state.

This demonstrates the **lack of visible feedback and main-thread I/O** from source inspection. It is not proof that every physical touch was delivered: on-device instrumentation remains outstanding.

## Fix
- Move AccessibilityManager/PackageManager reads to a dedicated single-threaded `ExecutorService`.
- On a direct user tap, immediately mark every refresh control **Проверка…**, disable it while the read is in flight and show a short Android toast.
- Show a persistent, nearby status line under each refresh button: `Проверка №N: HH:mm:ss · Проверка завершена. Изменений нет` (or changed/error).
- After the read, re-render the same selected service without navigating away. 
- Distinguish the actual Accessibility service state changes from a successful check that happened to return the same results.
- Handle concurrent taps with "Проверка уже выполняется"; no unbounded work queue.
- Guard callbacks after Activity destruction and release the executor in `onDestroy`.
- Preserve full safety constraints: read-only public access inventory and no accessibility granting, no Shizuku/ADB bypass for restricted settings.

## Verification
- Unit test: `AccessRefreshSummaryTest` covers unchanged lists, state changes, added and removed services, initial reads and unreadable system responses.
- Existing JVM tests continue unchanged.
- Android CI: Gradle Release, Android SDK 35, pinned permanent release signature and package/version check.
- Package: `dev.legion.control`, versionCode **15**, versionName **1.8.0-beta6**.
- **Not verified on target device:** actual touch dispatch, Android 17 screen rendering, user-visible progress, and vendor Binder timing. These require testing on the physical Lenovo hardware.

## Manual smoke checks
1. Open **Система → Доступ приложений → Kimi Claw**.
2. Tap **Проверить повторно** without changing the system setting.
3. Observe the button switch to **Проверка…** immediately and then a message like **Изменений нет** directly below it.
4. Tap again: the check number and time must change.
5. Test the same on **Список активности (Clone)** and on the top-level **Проверить снова**.
6. Test with Accessibility Settings temporarily unavailable: the screen must report a read error, not a false success.
7. Confirm the detected blocked/allowed/unknown states are still distinguished.
