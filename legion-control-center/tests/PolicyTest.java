package dev.legion.control;
public final class PolicyTest {
  private static int tests = 0;
  private static void ok(boolean v) { tests++; if(!v) throw new AssertionError("Policy invariant #"+tests+" failed"); }
  private static void rejects(Runnable r) { try { r.run(); throw new AssertionError("Expected rejection"); } catch(IllegalArgumentException expected) {} }
  public static void main(String[] args) {
    ok(CommandPolicy.supportedRate(120)); ok(CommandPolicy.supportedRate(144));
    rejects(() -> CommandPolicy.putSetting("global", "low_power", "0"));
    ok(CommandPolicy.permittedCommand(CommandPolicy.putSetting("system", "peak_refresh_rate", "144.0")));
    ok(CommandPolicy.display144Supported("supportedModes fps=144.00002, fps=120.0"));
    ok(!CommandPolicy.display144Supported("supportedModes fps=120.0"));
    ok(!CommandPolicy.display144Supported("fps=1440.0"));
    ok(CommandPolicy.display144Supported("fps=144.00002]"));
    ok(!CommandPolicy.display144Supported("fps=144.2]"));
    ok(CommandPolicy.permittedCommand(CommandPolicy.DISPLAY_144_CHECK));
    ok(CommandPolicy.permittedCommand(CommandPolicy.deleteSetting("system", "peak_refresh_rate")));
    rejects(() -> CommandPolicy.appOpsSet("com.android.systemui", "ignore"));
    rejects(() -> CommandPolicy.appOpsSet("com.termux", "arbitrary"));
    rejects(() -> CommandPolicy.overlayFont("evil; reboot"));
    ok(!CommandPolicy.gameModeAvailable("current mode: standard, available game modes: [standard,custom]", "performance"));
    ok(CommandPolicy.gameModeAvailable("current mode: standard, available game modes: [standard,custom]", "standard"));
    ok("default".equals(CommandPolicy.appOpsMode("No operations.\nDefault mode: allow")));
    ok("allow".equals(CommandPolicy.appOpsMode("RUN_ANY_IN_BACKGROUND: allow")));
    ok("com.android.theme.font.WorkSans".equals(CommandPolicy.enabledFont("[x] com.android.theme.font.WorkSans")));
    // Shell user service must reject even commands from a compromised/foreign binder client.
    for (String injection : new String[] {"id; reboot", "cmd game mode standard com.riotgames.league.wildrift; reboot", "settings put global low_power 0", "rm -rf /sdcard", "", "id\nreboot", "cmd appops set com.android.systemui RUN_ANY_IN_BACKGROUND ignore"}) {
       ok(!CommandPolicy.permittedCommand(injection));
    }
    ok(CommandPolicy.permittedCommand("cmd game list-modes com.riotgames.league.wildrift"));
    ok(CommandPolicy.permittedCommand(CommandPolicy.putSetting("system", "peak_refresh_rate", "120.0")));
    ok(CommandPolicy.permittedCommand(CommandPolicy.appOpsSet("org.telegram.messenger", "default")));
    ok(CommandPolicy.permittedCommand("cmd overlay list --user 0"));
    ok(!CommandPolicy.permittedCommand("cmd overlay enable-exclusive --user 0 --category wrong.overlay"));
    ok(!CommandPolicy.permittedCommand("dumpsys display | grep -m 1 'fps=144'; reboot"));
    // Old Android settings may be serialized as 0, 1 or 120: rollback normalizes safely.
    ok("0.0".equals(CommandPolicy.restoreValue("animator_duration_scale", "0")));
    ok("1.0".equals(CommandPolicy.restoreValue("window_animation_scale", "1")));
    ok("120.0".equals(CommandPolicy.restoreValue("peak_refresh_rate", "120")));
    ok("null".equals(CommandPolicy.restoreValue("min_refresh_rate", "null")));
    ok("144.0".equals(CommandPolicy.restoreValue("peak_refresh_rate", "144")));
    rejects(() -> CommandPolicy.restoreValue("peak_refresh_rate", "NaN"));
    for(String read : new String[]{
       "cmd game list-modes com.riotgames.league.wildrift; cmd game list-configs com.riotgames.league.wildrift",
       "settings get global low_power; settings get system min_refresh_rate; settings get system peak_refresh_rate; wm size; wm density",
       "cmd overlay list --user 0 | grep -E 'com.android.theme.font' ",
       "for p in com.motorola.gamemode com.motorola.freeform com.motorola.mobiledesktop com.motorola.batterycare com.motorola.personalize; do echo ===$p; pm path $p | head -1; done",
       "cmd appops get com.termux RUN_ANY_IN_BACKGROUND; cmd appops get org.telegram.messenger RUN_ANY_IN_BACKGROUND",
       "pm list packages -d"
    }) ok(CommandPolicy.permittedCommand(read));
    for(String pkg:CommandPolicy.FONTS) {
       ok(CommandPolicy.permittedCommand(CommandPolicy.overlayFont(pkg)));
       ok(CommandPolicy.permittedCommand("cmd overlay disable --user 0 " + pkg));
    }
    System.out.println("PolicyTest PASS ("+tests+" checks)");
  }
}
