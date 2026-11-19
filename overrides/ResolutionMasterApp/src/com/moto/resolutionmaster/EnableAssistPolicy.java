package com.moto.resolutionmaster;

/** Strict one-shot policy for consented Android Settings UI assistance. */
public final class EnableAssistPolicy {
    private EnableAssistPolicy() {}

    public static boolean canClick(String screenPackage, boolean exactAppLabelVisible,
                                   String actionLabel, boolean actionEnabled, long ageMs) {
        if (!"com.android.settings".equals(screenPackage)) return false;
        if (!exactAppLabelVisible || !actionEnabled) return false;
        if (ageMs < 0 || ageMs > 45000) return false;
        if (actionLabel == null) return false;
        String label = actionLabel.trim();
        return "Включить".equals(label) || "Enable".equalsIgnoreCase(label);
    }
}
