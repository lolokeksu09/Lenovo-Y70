package com.moto.resolutionmaster;

/** Exact predicate used for the "Disabled" application filter. */
public final class DisabledFilterPolicy {
    private DisabledFilterPolicy() {}

    /** Unknown status is never evidence that a package has been disabled. */
    public static boolean matches(Boolean isEnabled) {
        return Boolean.FALSE.equals(isEnabled);
    }
}
