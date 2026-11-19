package com.moto.resolutionmaster;

public final class RestoreRouteTest {
    private static void check(boolean expected, String reason) {
        if (!expected) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(RestoreRoute.choose(Boolean.FALSE, 2) == RestoreRoute.Mode.SETTINGS,
            "Expressweather and brapps enabled=2 must use Android Settings");
        check(RestoreRoute.choose(Boolean.FALSE, 4) == RestoreRoute.Mode.SETTINGS,
            "Disabled-until-used must not be forced by Shizuku");
        check(RestoreRoute.choose(Boolean.FALSE, 3) == RestoreRoute.Mode.SHELL,
            "User-disabled apps may use pm enable --user 0");
        check(RestoreRoute.choose(Boolean.FALSE, 0) == RestoreRoute.Mode.UNKNOWN,
            "Contradictory DEFAULT vs shell disabled must not be mutated");
        check(RestoreRoute.choose(Boolean.TRUE, 2) == RestoreRoute.Mode.UNKNOWN,
            "Already enabled package cannot be restored again");
        check(RestoreRoute.choose(null, 2) == RestoreRoute.Mode.UNKNOWN,
            "Unknown state never triggers a write");
        check(RestoreRoute.choose(Boolean.FALSE, -1) == RestoreRoute.Mode.UNKNOWN,
            "Failed PackageManager read must not trigger a write");
        System.out.println("PASS: Android per-user enable route and no-root Settings fallback");
    }
}
