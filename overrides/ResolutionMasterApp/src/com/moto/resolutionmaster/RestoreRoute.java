package com.moto.resolutionmaster;

/** Route Android user-0 package state to the least-privileged safe recovery UI. */
public final class RestoreRoute {
    private RestoreRoute() {}

    public enum Mode { SHELL, SETTINGS, UNKNOWN }

    /**
     * DISABLED (2) and DISABLED_UNTIL_USED (4) cannot be changed by shell
     * on many Android builds. DISABLED_USER (3) is eligible for pm enable.
     */
    public static Mode choose(Boolean verifiedEnabled, int enabledSetting) {
        if (!Boolean.FALSE.equals(verifiedEnabled)) return Mode.UNKNOWN;
        if (enabledSetting == 2 || enabledSetting == 4) return Mode.SETTINGS;
        if (enabledSetting == 3) return Mode.SHELL;
        return Mode.UNKNOWN;
    }
}
