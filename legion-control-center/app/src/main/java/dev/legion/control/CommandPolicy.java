package dev.legion.control;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure command policy: all user-selected arguments are allowlisted. */
public final class CommandPolicy {
    private CommandPolicy() {}
    /** Read-only public overview. No UID/group or raw shell identity in the normal interface. */
    public static final String DEVICE_SUMMARY_COMMAND =
        "printf 'Model: '; getprop ro.product.model; printf 'SKU: '; getprop ro.boot.hardware.sku; printf 'SoC: '; getprop ro.soc.model; printf 'Android: '; getprop ro.build.version.release; printf 'Power saving: '; settings get global low_power; printf 'Peak refresh: '; settings get system peak_refresh_rate; printf 'Minimum refresh: '; settings get system min_refresh_rate";
    public static final String DISPLAY_144_CHECK = "dumpsys display | grep -m 1 'fps=144'";
    public static final String WILD_RIFT = "com.riotgames.league.wildrift";
    public static final List<String> BACKGROUND_PACKAGES = Arrays.asList(
            "com.riotgames.league.wildrift", "org.telegram.messenger", "dev.monolith.launcher", "com.termux");
    public static final List<String> FONTS = Arrays.asList("com.android.theme.font.SpaceGrotesk", "com.android.theme.font.WorkSans", "com.android.theme.font.Exo2Regular", "com.android.theme.font.RobotoSlabRegular", "com.android.theme.font.RookeryRegular");
    /** Service boundary: reject commands outside our exact UI operations, even from another binder client. */
    public static boolean permittedCommand(String c) {
        if (c == null || c.isEmpty() || c.length() > 500 || c.indexOf('\n') >= 0 || c.indexOf('\r') >= 0) return false;
        if (READ_COMMANDS.contains(c) || DISPLAY_144_CHECK.equals(c)) return true;
        for (String pkg : BACKGROUND_PACKAGES) {
            if (c.equals(appOpsGet(pkg))) return true;
            for (String mode : Arrays.asList("allow", "ignore", "default")) if (c.equals(appOpsSet(pkg, mode))) return true;
        }
        for (String font : FONTS) {
            if (c.equals(overlayFont(font)) || c.equals("cmd overlay disable --user 0 " + font)) return true;
        }
        for (String mode : Arrays.asList("standard", "custom", "performance", "battery")) {
            if (c.equals(gameMode(mode))) return true;
        }
        for (String ns : Arrays.asList("system", "global")) {
            for (String key : Arrays.asList("min_refresh_rate", "peak_refresh_rate", "window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
                if (!validSetting(ns, key)) continue;
                if (c.equals(getSetting(ns,key)) || c.equals(deleteSetting(ns,key))) return true;
                for (String v : Arrays.asList("60.0", "90.0", "120.0", "144.0", "0.0", "0.5", "1.0")) {
                    try { if (c.equals(putSetting(ns,key,v))) return true; } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        return false;
    }
    private static final List<String> READ_COMMANDS = Arrays.asList(
        "id; getprop ro.product.model; getprop ro.boot.hardware.sku; getprop ro.soc.model; getprop ro.build.version.release",
        DEVICE_SUMMARY_COMMAND,
        "settings get global low_power; settings get system min_refresh_rate; settings get system peak_refresh_rate; wm size; wm density",
        "cmd game list-modes com.riotgames.league.wildrift; cmd game list-configs com.riotgames.league.wildrift",
        "cmd game list-modes com.riotgames.league.wildrift",
        "cmd overlay list --user 0",
        "pm list packages -d",
        "cmd overlay list --user 0 | grep -E 'com.android.theme.font'",
        "cmd overlay list --user 0 | grep -E 'com.android.theme.font' ",
        "for p in com.motorola.gamemode com.motorola.freeform com.motorola.mobiledesktop com.motorola.batterycare com.motorola.personalize; do echo ===$p; pm path $p | head -1; done",
        "cmd appops get com.termux RUN_ANY_IN_BACKGROUND; cmd appops get dev.monolith.launcher RUN_ANY_IN_BACKGROUND; cmd appops get org.telegram.messenger RUN_ANY_IN_BACKGROUND",
        "cmd appops get com.termux RUN_ANY_IN_BACKGROUND; cmd appops get org.telegram.messenger RUN_ANY_IN_BACKGROUND"
    );
    public static String restoreValue(String key, String raw) {
        if (raw == null || raw.equals("null")) return "null";
        try {
            double n = Double.parseDouble(raw);
            if (!Double.isFinite(n)) throw new IllegalArgumentException("Nonfinite value");
            String normalized = String.format(Locale.ROOT,"%.1f",n);
            if (!safeSettingValue(key,normalized)) throw new IllegalArgumentException("Unsupported saved value");
            return normalized;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Unsupported saved value",e); }
    }
    public static boolean supportedRate(int hz) { return hz == 60 || hz == 90 || hz == 120 || hz == 144; }
    public static boolean display144Supported(String modes) {
        return modes != null && Pattern.compile("fps=144(?:\\.0+)?(?:\\d+)?(?:[, }\\]]|$)").matcher(modes).find();
    }
    public static String getSetting(String namespace, String key) {
        if (!validSetting(namespace, key)) throw new IllegalArgumentException("Setting not allowed");
        return "settings get " + namespace + " " + key;
    }
    public static String putSetting(String namespace, String key, String value) {
        if (!validSetting(namespace,key) || !safeSettingValue(key,value)) throw new IllegalArgumentException("Value not allowed");
        return "settings put " + namespace + " " + key + " " + value;
    }
    public static String deleteSetting(String namespace, String key) {
        if (!validSetting(namespace,key)) throw new IllegalArgumentException("Setting not allowed");
        return "settings delete " + namespace + " " + key;
    }
    private static boolean validSetting(String ns,String key) {
        return (ns.equals("system") && (key.equals("min_refresh_rate") || key.equals("peak_refresh_rate"))) ||
               (ns.equals("global") && (key.equals("window_animation_scale") || key.equals("transition_animation_scale") || key.equals("animator_duration_scale")));
    }
    private static boolean safeSettingValue(String key,String val) {
        if (val == null) return false;
        if (key.endsWith("refresh_rate")) return val.equals("60.0") || val.equals("90.0") || val.equals("120.0") || val.equals("144.0") || val.equals("null");
        return val.equals("0.0") || val.equals("0.5") || val.equals("1.0") || val.equals("null");
    }
    public static String appOpsGet(String pkg) {
        if (!BACKGROUND_PACKAGES.contains(pkg)) throw new IllegalArgumentException("Package not allowed");
        return "cmd appops get " + pkg + " RUN_ANY_IN_BACKGROUND";
    }
    public static String appOpsSet(String pkg,String mode) {
        if (!BACKGROUND_PACKAGES.contains(pkg) || !(mode.equals("allow") || mode.equals("ignore") || mode.equals("default"))) throw new IllegalArgumentException("AppOps not allowed");
        return "cmd appops set " + pkg + " RUN_ANY_IN_BACKGROUND " + mode;
    }
    public static String overlayFont(String pkg) {
        if (!FONTS.contains(pkg)) throw new IllegalArgumentException("Font not allowed");
        return "cmd overlay enable-exclusive --user 0 --category " + pkg;
    }
    public static String gameMode(String mode) {
        if (!(mode.equals("standard") || mode.equals("performance") || mode.equals("battery") || mode.equals("custom"))) throw new IllegalArgumentException("Mode not allowed");
        return "cmd game mode " + mode + " " + WILD_RIFT;
    }
    public static boolean gameModeAvailable(String listing,String mode) {
        Matcher m = Pattern.compile("available game modes:\\s*\\[([^]]*)]",Pattern.CASE_INSENSITIVE).matcher(listing);
        if (!m.find()) return false;
        for(String s: m.group(1).split(",")) if (s.trim().equalsIgnoreCase(mode)) return true;
        return false;
    }
    public static String currentGameMode(String listing) {
        Matcher m = Pattern.compile("current mode:\\s*([a-z]+)",Pattern.CASE_INSENSITIVE).matcher(listing);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "unknown";
    }
    public static String appOpsMode(String listing) {
        Matcher m = Pattern.compile("RUN_ANY_IN_BACKGROUND:\\s*(allow|ignore|deny|default)",Pattern.CASE_INSENSITIVE).matcher(listing);
        if(m.find()) return m.group(1).toLowerCase(Locale.ROOT);
        if(listing.contains("No operations") || listing.contains("Default mode: allow")) return "default";
        return "unknown";
    }
    public static String enabledFont(String overlayList) {
        for(String font:FONTS) if (overlayList.contains("[x] " + font)) return font;
        return "";
    }
}
