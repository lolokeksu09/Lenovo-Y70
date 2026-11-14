package dev.legion.control;

/** Vetted paired refresh-rate and animation presets; no power/thermal modification. */
public final class QuickProfile {
    public final String id;
    public final String title;
    public final int hz;
    public final String animation;

    private QuickProfile(String id, String title, int hz, String animation) {
        this.id=id; this.title=title; this.hz=hz; this.animation=animation;
    }
    public static QuickProfile find(String id) {
        if ("game".equals(id)) return new QuickProfile("game", "Для игр", 120, "0.5");
        if ("daily".equals(id)) return new QuickProfile("daily", "На каждый день", 90, "0.5");
        throw new IllegalArgumentException("Неизвестный профиль");
    }
}
