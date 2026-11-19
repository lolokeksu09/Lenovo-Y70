package com.moto.resolutionmaster;

public final class PackageStateResolverTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String enabled = "package:com.android.dialer\npackage:com.android.settings\npackage:com.demo.active\n";
        String disabled = "package:com.demo.disabled\n";
        PackageStateResolver.Snapshot shell = PackageStateResolver.Snapshot.fromShell(
            true, enabled, true, disabled);
        check(shell.verified, "Two successful shell lists must be trusted");

        // Regression: Motorola Settings says Dialer enabled while app-side
        // PackageManager erroneously reports disabled (or until-used).
        check(Boolean.TRUE.equals(shell.resolve("com.android.dialer", Boolean.FALSE, true, shell)),
            "Verified shell-enabled Dialer must never appear disabled");

        check(Boolean.FALSE.equals(shell.resolve("com.demo.disabled", Boolean.FALSE, true, shell)),
            "Explicitly disabled package present only in -d must be disabled");

        check(shell.resolve("com.demo.active", Boolean.FALSE, false, shell) == Boolean.TRUE,
            "Shell-enabled result must outrank stale in-process false state");
        check(shell.resolve("com.demo.disabled", null, false, shell) == null,
            "A shell-disabled package without explicit local disable is ambiguous");
        check(shell.resolve("com.demo.notlisted", Boolean.FALSE, true, shell) == null,
            "Absent package must not be manufactured as disabled");

        PackageStateResolver.Snapshot collision = PackageStateResolver.Snapshot.fromShell(
            true, "package:com.android.dialer\n", true, "package:com.android.dialer\n");
        check(collision.resolve("com.android.dialer", Boolean.FALSE, true, collision) == null,
            "Conflicting system snapshots must be UNKNOWN");

        PackageStateResolver.Snapshot noShell = PackageStateResolver.Snapshot.fromShell(
            false, "permission denied", true, "package:com.demo.disabled\n");
        check(!noShell.verified, "Partial shell responses are not authoritative");
        check(noShell.resolve("com.android.dialer", Boolean.FALSE, false, noShell) == null,
            "Without verified shell, ambiguous app-side false state stays unknown");
        check(Boolean.FALSE.equals(noShell.resolve("com.demo.disabled", Boolean.FALSE, true, noShell)),
            "Known explicitly disabled state can be shown without Shizuku");
        check(Boolean.TRUE.equals(noShell.resolve("com.demo.active", Boolean.TRUE, false, noShell)),
            "Enabled state remains enabled without Shizuku");

        System.out.println("PASS: dialer/system enabled override, explicit disable, conflict, no-shell");
    }
}
