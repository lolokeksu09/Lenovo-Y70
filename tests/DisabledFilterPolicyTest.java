package com.moto.resolutionmaster;

/** Run as pure Java on GitHub Actions: checks the exact Android adapter predicate. */
public final class DisabledFilterPolicyTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(!DisabledFilterPolicy.matches(Boolean.TRUE),
            "Enabled applications must not appear as disabled");
        check(DisabledFilterPolicy.matches(Boolean.FALSE),
            "Verified-disabled applications must be included");
        check(!DisabledFilterPolicy.matches(null),
            "Unknown state must not be mislabeled disabled");

        Boolean enabled = Boolean.FALSE;
        check(DisabledFilterPolicy.matches(enabled), "Disabled before restore");
        enabled = Boolean.TRUE;
        check(!DisabledFilterPolicy.matches(enabled), "Reenabled package must leave disabled filter");
        System.out.println("PASS: filter enabled/disabled/unknown/updated-state");
    }
}
