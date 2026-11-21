package com.moto.resolutionmaster;

public final class MotorolaTouchRouteTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(MotorolaTouchRoute.useOfficialGameTime(true),
              "missing own Binder must route to official GameTime");
        check(!MotorolaTouchRoute.useOfficialGameTime(false),
              "unknown Binder state is not automatic denial");
        check(MotorolaTouchRoute.actionLabel(true, null).contains("GameTime"),
              "OEM route action must be clearly named");
        check(MotorolaTouchRoute.actionLabel(true, Boolean.TRUE).contains("GameTime"),
              "OEM request on does not restore our own Binder");
        check(MotorolaTouchRoute.actionLabel(false, Boolean.TRUE)
              .contains("Выключить"), "own successful request allows off control");
        check(MotorolaTouchRoute.actionLabel(false, null)
              .contains("Включить"), "unconfirmed own state cannot be presented as on");
        check(MotorolaTouchRoute.accessSummary(true).contains("нашему APK"),
              "denial must be scoped to APK, not the entire OEM service");
        check(MotorolaTouchRoute.accessSummary(false).contains("не установлен"),
              "Shizuku readback alone cannot prove direct access");
        System.out.println("PASS: own Binder availability distinct from OEM GameTime and Shizuku");
    }
}
