package dev.legion.control;

/** Independent regression check for the two composite Shizuku profiles. */
public final class QuickProfileTest {
  private static int checks;
  private static void check(boolean v) { checks++; if (!v) throw new AssertionError("QuickProfile check #"+checks); }
  private static void reject(String id) {
    try { QuickProfile.find(id); throw new AssertionError("Accepted invalid profile: "+id); }
    catch (IllegalArgumentException expected) { checks++; }
  }
  public static void main(String[] args) {
    QuickProfile game = QuickProfile.find("game");
    QuickProfile daily = QuickProfile.find("daily");
    check(game.hz == 120 && "0.5".equals(game.animation));
    check(daily.hz == 90 && "0.5".equals(daily.animation));
    check(!game.title.equals(daily.title));
    check(CommandPolicy.supportedRate(game.hz));
    check(CommandPolicy.supportedRate(daily.hz));
    for (QuickProfile p : new QuickProfile[]{game, daily}) {
      check(CommandPolicy.permittedCommand(CommandPolicy.putSetting("system","min_refresh_rate",p.hz+".0")));
      check(CommandPolicy.permittedCommand(CommandPolicy.putSetting("system","peak_refresh_rate",p.hz+".0")));
      for(String key:new String[]{"window_animation_scale","transition_animation_scale","animator_duration_scale"})
        check(CommandPolicy.permittedCommand(CommandPolicy.putSetting("global", key, p.animation)));
    }
    reject("");reject("performance");reject("game; reboot");reject(null);
    System.out.println("QuickProfileTest PASS ("+checks+" checks)");
  }
}
