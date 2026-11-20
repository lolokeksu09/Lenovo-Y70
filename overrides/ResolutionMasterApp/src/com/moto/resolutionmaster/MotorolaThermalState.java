package com.moto.resolutionmaster;

/**
 * Read-only snapshot of Motorola MAXE and thermal-engine properties.
 * Property values are not proof that thermal-engine loaded a specific profile.
 */
public final class MotorolaThermalState {
    private MotorolaThermalState() {}

    public static final String READ_COMMAND =
        "printf 'MOTOSXF=%s\\nTHERMAL=%s\\nENGINE=%s\\n' " +
        "\"$(getprop vendor.motosxf.mode)\" " +
        "\"$(getprop vendor.thermal.mode)\" " +
        "\"$(getprop init.svc.thermal-engine)\"; " +
        "if [ -f /vendor/etc/thermal-engine-avr-game-perf.conf ]; then " +
        "printf 'GAME_PERF_CONFIG=present\\n'; else " +
        "printf 'GAME_PERF_CONFIG=absent\\n'; fi";

    public static String unavailable(String reason) {
        return "MAXE / Thermal Engine: нет данных (" + reason + ")";
    }

    public static String parse(String raw) {
        if (raw == null || raw.isEmpty()) return unavailable("пустой ответ");
        String motosxf = null;
        String thermal = null;
        String engine = null;
        String config = null;
        for (String line : raw.split("\\r?\\n")) {
            if (line.startsWith("MOTOSXF=")) motosxf = line.substring(8).trim();
            else if (line.startsWith("THERMAL=")) thermal = line.substring(8).trim();
            else if (line.startsWith("ENGINE=")) engine = line.substring(7).trim();
            else if (line.startsWith("GAME_PERF_CONFIG=")) {
                config = line.substring(17).trim();
            }
        }
        if (motosxf == null || thermal == null || engine == null ||
                config == null || (!"present".equals(config) &&
                !"absent".equals(config))) {
            return unavailable("неполный ответ от Shell");
        }
        return "MAXE: " + value(motosxf) +
            "\nThermal property: " + value(thermal) +
            "\nthermal-engine: " + value(engine) +
            "\ngame-perf.conf: " + ("present".equals(config) ? "найден" : "не найден") +
            "\nАктивный thermal-конфиг не установлен этой проверкой.";
    }

    private static String value(String raw) {
        return raw.isEmpty() ? "не задано" : raw;
    }
}
