package com.moto.resolutionmaster;

import java.util.Arrays;
import java.util.List;

/** Focused regression for explicit opt-in bulk restore. */
public final class BulkEnablePolicyTest {
    private static void assertTrue(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }

    public static void main(String[] args) {
        List<String> selected = BulkEnablePolicy.select(Arrays.asList(
            new BulkEnablePolicy.Candidate("com.example.disabled", Boolean.FALSE, false),
            new BulkEnablePolicy.Candidate("com.example.disabled", Boolean.FALSE, false),
            new BulkEnablePolicy.Candidate("com.example.enabled", Boolean.TRUE, false),
            new BulkEnablePolicy.Candidate("com.example.unknown", null, false),
            new BulkEnablePolicy.Candidate("com.android.dialer", Boolean.FALSE, true),
            new BulkEnablePolicy.Candidate("com.android.systemui", Boolean.FALSE, true),
            new BulkEnablePolicy.Candidate("invalid ; rm -rf /", Boolean.FALSE, false),
            new BulkEnablePolicy.Candidate("com.example.extra", Boolean.FALSE, false)
        ));
        assertTrue(selected.size() == 2, "Only two eligible confirmed-disabled packages");
        assertTrue(selected.get(0).equals("com.example.disabled"), "Stable order");
        assertTrue(selected.get(1).equals("com.example.extra"), "Second candidate");
        assertTrue(BulkEnablePolicy.select(null).isEmpty(), "Null list does not enable anything");
        System.out.println("PASS: eligible-only selection, deduplication, protected and unknown safeguards");
    }
}
