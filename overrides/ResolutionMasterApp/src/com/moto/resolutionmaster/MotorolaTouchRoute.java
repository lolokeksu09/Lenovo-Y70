package com.moto.resolutionmaster;

/** Pure UI routing policy: OEM GameTime request != our direct API permission. */
public final class MotorolaTouchRoute {
    private MotorolaTouchRoute() {}

    public static boolean useOfficialGameTime(boolean ownBinderMissing) {
        return ownBinderMissing;
    }

    public static String actionLabel(boolean ownBinderMissing,
                                     Boolean ownTouchRequested) {
        if (ownBinderMissing) return "Открыть штатный GameTime";
        return Boolean.TRUE.equals(ownTouchRequested)
            ? "Выключить наш Touch Boost" : "Включить Touch Boost";
    }

    public static String accessSummary(boolean ownBinderMissing) {
        return ownBinderMissing
            ? "Прямой API: Binder недоступен нашему APK; штатный GameTime работает отдельно"
            : "Прямой API: доступ не установлен одной лишь диагностикой Shizuku";
    }
}
