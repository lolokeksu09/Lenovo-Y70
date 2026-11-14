# Legion Control

Native Android settings utility for Lenovo Legion Y70 running Motorola Edge 70 Max ROM. Android 9+, Shizuku UserService (`shell`, no root), fixed package `dev.legion.control`.

## v1.3.0 features

- Native AMOLED-first UI, fixed five-section bottom navigation, status-aware buttons.
- Two **transactional** quick profiles (120/90 Hz and 0.5× window/transition/animator), with a persisted write-ahead snapshot and verified recovery; does not touch CPU, thermal limits, GPU, power saving, or GameManager.
- Individual screen refresh setting, animation settings, Wild Rift game modes only if supported, installed font overlays, and per-app `RUN_ANY_IN_BACKGROUND` AppOps.
- Newly added **default AppOps** reset action, separate from allow/ignore.
- Real diagnostics export as `.txt`; no serial numbers, account tokens, or private credentials intended.

## Safety

Only commands allowed by `CommandPolicy` are executed by the Shizuku UserService. All system changes are explicit, have a snapshot saved before the first write, and offer a recovery action. Profile changes can still be overridden by firmware or require a new Shizuku session. A rollback can fail if Shizuku stops; an uncompleted journal remains available after restart.

## Building

Java 17, Android Gradle Plugin 8.13.2, Gradle 8.13, compileSdk 35. The GitHub Actions workflow runs command-policy tests then assembles/signs with the existing permanent certificate. **Do not commit signing keys.** Source code lives directly under `legion-control-center/` (not inside a base64 archive).

Local JVM check from repository root:

```sh
cd legion-control-center
mkdir -p /tmp/legion-test-classes
javac -encoding UTF-8 -d /tmp/legion-test-classes app/src/main/java/dev/legion/control/{CommandPolicy,QuickProfile,MutationTransaction}.java tests/{PolicyTest,QuickProfileTest,MutationTransactionTest}.java
java -cp /tmp/legion-test-classes dev.legion.control.PolicyTest
java -cp /tmp/legion-test-classes dev.legion.control.QuickProfileTest
java -cp /tmp/legion-test-classes dev.legion.control.MutationTransactionTest
```

Device test status: **not yet run on physical Lenovo Y70**. Verify Shizuku permission, screen values, recovery after interrupted profile application, and TalkBack before treating UI/operations as device-validated.
