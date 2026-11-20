package com.moto.resolutionmaster;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only parser for "dumpsys moto_extend_service".
 *
 * A request inside mDpsRequests is not the hardware state. Likewise a
 * GameTime bundle is evidence of a requested touch mode, NOT a measured
 * touchscreen sampling rate or driver acknowledgement.
 *
 * Pure Java so parsing and failure handling can be regression tested on JVM.
 */
public final class MotorolaGameStateParser {
    private MotorolaGameStateParser() {}

    public static final class Snapshot {
        public final boolean readable;
        public final String error;
        public final Boolean dpsSupported;
        public final Integer hardwareDpsStatus;
        public final String dpsRequests;
        public final Boolean gameModeActive;
        public final Boolean touchRequested;
        public final String gamePackage;
        public final Boolean ownTouchRequested;
        public final String ownGamePackage;
        public final boolean touchServiceConnected;
        public final Boolean reportRateSupported;
        public final Boolean sampleRateSupported;
        public final Boolean tapSensitivitySupported;
        public final Boolean swipeResponsivenessSupported;

        private Snapshot(boolean readable, String error, Boolean dpsSupported,
                         Integer hardwareDpsStatus, String dpsRequests,
                         Boolean gameModeActive, Boolean touchRequested,
                         String gamePackage, Boolean ownTouchRequested,
                         String ownGamePackage, boolean touchServiceConnected,
                         Boolean reportRateSupported, Boolean sampleRateSupported,
                         Boolean tapSensitivitySupported, Boolean swipeResponsivenessSupported) {
            this.readable = readable;
            this.error = error;
            this.dpsSupported = dpsSupported;
            this.hardwareDpsStatus = hardwareDpsStatus;
            this.dpsRequests = dpsRequests;
            this.gameModeActive = gameModeActive;
            this.touchRequested = touchRequested;
            this.gamePackage = gamePackage;
            this.ownTouchRequested = ownTouchRequested;
            this.ownGamePackage = ownGamePackage;
            this.touchServiceConnected = touchServiceConnected;
            this.reportRateSupported = reportRateSupported;
            this.sampleRateSupported = sampleRateSupported;
            this.tapSensitivitySupported = tapSensitivitySupported;
            this.swipeResponsivenessSupported = swipeResponsivenessSupported;
        }
    }

    public static Snapshot parse(String dump) {
        if (dump == null || dump.length() == 0) return unavailable("Ответ сервиса пуст");
        if (dump.contains("Permission Denial") || dump.contains("Permission denial") ||
                dump.contains("SecurityException")) {
            return unavailable("Системный сервис отказал в чтении (права)");
        }
        if (!dump.contains("MotoExtendService") ||
                !dump.contains("MotoPowerHealthManagerService State:") ||
                !dump.contains("GameModeStateController")) {
            return unavailable("Ответ moto_extend_service неполный или несовместимый");
        }
        String hardwareRaw = value(dump, "mCurrentHardwareDpsStatus");
        String supportedRaw = value(dump, "isDPSSupported");
        // Do not convert a truncated dump into a plausible disabled state.
        if (hardwareRaw == null || supportedRaw == null) {
            return unavailable("Нет подтверждённого статуса Direct Power Supply");
        }

        Boolean touch = null;
        String game = null;
        Boolean ownTouch = null;
        String ownGame = null;
        for (String line : dump.split("\\r?\\n")) {
            if (!line.contains("token=") || !line.contains("bundle=Bundle[")) {
                continue;
            }
            String actor = inlineValue(line, "game_mode_op_package_name");
            String raw = inlineValue(line, "touch_enhance_enable");
            if (!"0".equals(raw) && !"1".equals(raw)) continue;
            if ("com.motorola.gamemode".equals(actor)) {
                touch = "1".equals(raw);
                game = inlineValue(line, "game_package_name");
            } else if ("com.moto.resolutionmaster".equals(actor)) {
                ownTouch = "1".equals(raw);
                ownGame = inlineValue(line, "game_package_name");
            }
        }

        return new Snapshot(
            true, null, bool(supportedRaw), integer(hardwareRaw),
            value(dump, "mDpsRequests"), bool(value(dump, "isGameModeActive")),
            touch, game, ownTouch, ownGame, value(dump, "mAidlService") != null &&
                value(dump, "mAidlService").contains("IMotTouch"),
            bool(value(dump, "mReportRateSupported_aidl")),
            bool(value(dump, "mSampleRateSupported_aidl")),
            bool(value(dump, "mTapSensitivitySupported_aidl")),
            bool(value(dump, "mSwipeResponsivenessSupported_aidl")));
    }

    public static Snapshot unavailable(String error) {
        return new Snapshot(false, error, null, null, null, null, null,
            null, null, null, false, null, null, null, null);
    }

    private static String value(String dump, String key) {
        Pattern p = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "=([^\\r\\n]*)");
        Matcher m = p.matcher(dump);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String inlineValue(String line, String key) {
        Matcher m = Pattern.compile("(?:^|[,\\s{])" + Pattern.quote(key) +
            "=([^,\\s}\\]]+)").matcher(line);
        return m.find() ? m.group(1) : null;
    }

    private static Boolean bool(String raw) {
        if ("true".equals(raw)) return Boolean.TRUE;
        if ("false".equals(raw)) return Boolean.FALSE;
        return null;
    }

    private static Integer integer(String raw) {
        if (raw == null) return null;
        try { return Integer.valueOf(raw); }
        catch (NumberFormatException e) { return null; }
    }
}
