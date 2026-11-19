package com.moto.resolutionmaster;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selection only. This class never invokes Android or changes packages. */
public final class BulkEnablePolicy {
    private BulkEnablePolicy() {}

    public static final class Candidate {
        public final String packageName;
        public final Boolean enabled;
        public final boolean protectedPackage;

        public Candidate(String packageName, Boolean enabled, boolean protectedPackage) {
            this.packageName = packageName;
            this.enabled = enabled;
            this.protectedPackage = protectedPackage;
        }
    }

    /** Select only confirmed-disabled, unprotected packages. Ignore duplicates. */
    public static List<String> select(List<Candidate> candidates) {
        Set<String> selected = new LinkedHashSet<>();
        if (candidates == null) return new ArrayList<>(selected);
        for (Candidate c : candidates) {
            if (c == null || c.protectedPackage ||
                    c.packageName == null ||
                    !c.packageName.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") ||
                    !Boolean.FALSE.equals(c.enabled)) continue;
            selected.add(c.packageName);
        }
        return new ArrayList<>(selected);
    }
}
