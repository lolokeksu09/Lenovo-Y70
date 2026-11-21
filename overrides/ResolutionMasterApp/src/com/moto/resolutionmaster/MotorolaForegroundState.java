package com.moto.resolutionmaster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only, conservative foreground classifier for Motorola game captures.
 * A running PID is not evidence that the game remains the resumed Activity.
 * No non-game package or Activity name is stored in the diagnostic report.
 */
public final class MotorolaForegroundState {
    private MotorolaForegroundState() {}

    public static final String READ_COMMAND =
        "dumpsys activity activities | " +
        "grep -E 'topResumedActivity|mResumedActivity|ResumedActivity|Resumed:' | " +
        "head -n 16";

    private static final Pattern COMPONENT = Pattern.compile(
        "([a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+)/[^\\s}]+");

    /** wild-rift, other, or unknown. Unknown includes split-screen ambiguity. */
    public static String parse(String raw) {
        if (raw == null || raw.trim().isEmpty() ||
                raw.contains("Permission Denial") ||
                raw.contains("Permission denial") ||
                raw.contains("SecurityException")) return "unknown";

        String preferred = null;
        boolean contradictory = false;
        for (String line : raw.split("\\r?\\n")) {
            if (!line.contains("topResumedActivity")) continue;
            String component = packageName(line);
            if (component == null) continue;
            if (preferred != null && !preferred.equals(component)) {
                contradictory = true;
            }
            preferred = component;
        }
        if (contradictory) return "unknown";
        if (preferred != null) return label(preferred);

        String fallback = null;
        for (String line : raw.split("\\r?\\n")) {
            if (!line.contains("mResumedActivity") &&
                    !line.contains("ResumedActivity") &&
                    !line.contains("Resumed:")) continue;
            String component = packageName(line);
            if (component == null) continue;
            if (fallback != null && !fallback.equals(component)) return "unknown";
            fallback = component;
        }
        return fallback == null ? "unknown" : label(fallback);
    }

    private static String packageName(String line) {
        Matcher match = COMPONENT.matcher(line);
        return match.find() ? match.group(1) : null;
    }

    private static String label(String pkg) {
        return MotorolaCaptureFormat.GAME_PACKAGE.equals(pkg) ? "wild-rift" : "other";
    }
}
