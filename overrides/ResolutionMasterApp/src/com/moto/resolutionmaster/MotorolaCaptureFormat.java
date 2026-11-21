package com.moto.resolutionmaster;

/**
 * Bounded, read-only system readbacks for an opted-in Wild Rift game session.
 * Only summaries are retained; raw dumpsys output is never persisted.
 */
public final class MotorolaCaptureFormat {
    private MotorolaCaptureFormat() {}

    public static final String GAME_PACKAGE = "com.riotgames.league.wildrift";

    public static final String LIGHT_COMMAND =
        "printf 'MOTOSXF=%s\\nTHERMAL=%s\\nENGINE=%s\\nGAME_PID=%s\\n' " +
        "\"$(getprop vendor.motosxf.mode)\" " +
        "\"$(getprop vendor.thermal.mode)\" " +
        "\"$(getprop init.svc.thermal-engine)\" " +
        "\"$(pidof com.riotgames.league.wildrift)\"";

    public static String light(String raw) {
        if (raw == null) return "system=нет ответа";
        String mode = field(raw, "MOTOSXF=");
        String thermal = field(raw, "THERMAL=");
        String engine = field(raw, "ENGINE=");
        String pid = field(raw, "GAME_PID=");
        if (mode == null || thermal == null || engine == null || pid == null) {
            return "system=неполный ответ";
        }
        return "game-process=" + (pid.isEmpty() ? "not-running" : "running") +
            " MAXE=" + safe(mode) +
            " thermal-property=" + safe(thermal) +
            " thermal-engine=" + safe(engine);
    }

    public static String motorola(String dump) {
        MotorolaGameStateParser.Snapshot s = MotorolaGameStateParser.parse(dump);
        if (!s.readable) return "Motorola=нет подтверждённых данных";
        return "GameTime-mode=" + state(s.gameModeActive) +
            " GameTime-touch-request=" + state(s.touchRequested) +
            " own-touch-request=" + state(s.ownTouchRequested) +
            " touch-HAL=" + (s.touchServiceConnected ? "connected" : "unconfirmed") +
            " DPS-hardware=" +
            (s.hardwareDpsStatus == null ? "unknown" : s.hardwareDpsStatus);
    }

    private static String field(String raw, String key) {
        for (String line : raw.split("\\r?\\n")) {
            if (line.startsWith(key)) return line.substring(key.length()).trim();
        }
        return null;
    }

    private static String state(Boolean raw) {
        return raw == null ? "unknown" : raw ? "on" : "off";
    }

    private static String safe(String raw) {
        if (raw.isEmpty()) return "unset";
        String clean = raw.replaceAll("[^a-zA-Z0-9_.-]", "?");
        return clean.length() > 32 ? clean.substring(0, 32) : clean;
    }
}
