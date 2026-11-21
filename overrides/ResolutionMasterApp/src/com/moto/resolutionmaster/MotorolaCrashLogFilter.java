package com.moto.resolutionmaster;

/**
 * Select the last relevant AndroidRuntime crash from logcat -v threadtime.
 * Never append logs from another process after the matching crash.
 */
public final class MotorolaCrashLogFilter {
    private static final String TARGET = "com.moto.resolutionmaster";
    private MotorolaCrashLogFilter() {}

    public static String relevantCrashExcerpt(String raw) {
        if (raw == null || raw.isEmpty()) return "Журнал аварий пуст";
        String[] lines = raw.split("\\r?\\n");
        int match = -1;
        int pid = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains("Process: com.moto.resolutionmaster") ||
                    line.contains("Cmdline: com.moto.resolutionmaster") ||
                    line.contains(">>> com.moto.resolutionmaster <<<")) {
                int candidate = getPid(line);
                if (candidate > 0) {
                    match = i;
                    pid = candidate;
                }
            }
        }
        if (match < 0) return "В журнале нет аварии " + TARGET;
        StringBuilder out = new StringBuilder();
        for (int i = Math.max(0, match - 1);
                 i < Math.min(lines.length, match + 64) && out.length() < 4500;
                 i++) {
            String line = lines[i];
            if (getPid(line) != pid) continue;
            if (i > match && line.contains("FATAL EXCEPTION:")) break;
            if (line.length() > 240) line = line.substring(0, 240);
            out.append(line).append('\n');
        }
        return out.length() == 0 ? "В журнале нет строк аварии " + TARGET
            : out.toString();
    }

    private static int getPid(String line) {
        if (line == null) return -1;
        String[] parts = line.trim().split("\\s+", 6);
        if (parts.length < 6) return -1;
        if (!parts[0].matches("\\d{2}-\\d{2}") ||
                !parts[1].matches("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")) {
            return -1;
        }
        try { return Integer.parseInt(parts[2]); }
        catch (NumberFormatException e) { return -1; }
    }
}
