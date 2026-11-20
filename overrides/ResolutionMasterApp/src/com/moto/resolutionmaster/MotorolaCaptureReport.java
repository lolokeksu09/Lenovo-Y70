package com.moto.resolutionmaster;

/** Bounded memory footprint and audit-friendly output for a 90-second capture. */
public final class MotorolaCaptureReport {
    public static final int MAX_SAMPLES = 12;
    public static final long SAMPLE_INTERVAL_MS = 8000L;

    private final StringBuilder entries = new StringBuilder();
    private int count = 0;
    private int failures = 0;

    public void append(int seconds, String light, String motorola, boolean failed) {
        if (count >= MAX_SAMPLES) return;
        count++;
        if (failed) failures++;
        entries.append("t+").append(Math.max(0, seconds)).append("s ")
            .append(bounded(light, 160));
        if (motorola != null) {
            entries.append(" | ").append(bounded(motorola, 180));
        }
        entries.append('\n');
    }

    public int samples() { return count; }
    public int failures() { return failures; }

    public String render(String outcome) {
        String status = outcome == null ? "неизвестно" : bounded(outcome, 90);
        return "Lenovo Y70 · Wild Rift game session\n" +
            "Сеанс: " + status + "\n" +
            "Сэмплов: " + count + ", ошибок чтения: " + failures + "\n" +
            "Интервал: 8 секунд; до 12 сэмплов.\n" +
            "Фактические частоты GPU, сенсора и активный thermal-конфиг не измерялись.\n" +
            entries.toString();
    }

    private static String bounded(String value, int maxLength) {
        if (value == null) return "unknown";
        String compact = value.replace('\r', ' ').replace('\n', ' ').trim();
        return compact.length() > maxLength
            ? compact.substring(0, maxLength) : compact;
    }
}
