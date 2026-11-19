package com.moto.resolutionmaster;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolve contradictory per-user Android package states without treating
 * errors, missing packages or suspended apps as disabled.
 *
 * Pure Java so the same logic can be executed by CI without Android runtime.
 */
public final class PackageStateResolver {
    private PackageStateResolver() {}

    public static final class Snapshot {
        public final boolean verified;
        public final Set<String> enabled;
        public final Set<String> disabled;

        private Snapshot(boolean verified, Set<String> enabled, Set<String> disabled) {
            this.verified = verified;
            this.enabled = enabled;
            this.disabled = disabled;
        }

        public static Snapshot fromShell(boolean enabledOk, String enabledOutput,
                                         boolean disabledOk, String disabledOutput) {
            if (!enabledOk || !disabledOk || enabledOutput == null || disabledOutput == null) {
                return new Snapshot(false, Collections.<String>emptySet(),
                                    Collections.<String>emptySet());
            }
            Set<String> e = parsePackageLines(enabledOutput);
            Set<String> d = parsePackageLines(disabledOutput);
            // An error printed to stdout is not a valid empty package list.
            if (containsError(enabledOutput) || containsError(disabledOutput)) {
                return new Snapshot(false, e, d);
            }
            return new Snapshot(true, e, d);
        }

        private static boolean containsError(String output) {
            String lower = output.toLowerCase(java.util.Locale.US);
            return lower.contains("exception") || lower.contains("permission denied") ||
                   lower.contains("error:") || lower.contains("unknown option");
        }

        private static Set<String> parsePackageLines(String output) {
            Set<String> set = new HashSet<>();
            for (String line : output.split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!trimmed.startsWith("package:")) continue;
                String pkg = trimmed.substring("package:".length()).trim();
                if (pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) set.add(pkg);
            }
            return set;
        }

        /**
         * A verified shell snapshot outranks the app's potentially stale
         * PackageManager view. Conflicts or absent entries are UNKNOWN.
         * Without a shell snapshot, trust only an internally consistent local
         * reading: never manufacture a disabled state from a failed lookup.
         */
        public Boolean resolve(String packageName, Boolean local, boolean localExplicitDisabled,
                               Snapshot shell) {
            if (packageName == null) return null;
            if (shell != null && shell.verified) {
                boolean inEnabled = shell.enabled.contains(packageName);
                boolean inDisabled = shell.disabled.contains(packageName);
                if (inEnabled == inDisabled) return null;
                if (inEnabled) return Boolean.TRUE;
                // A vendor's "disabled until used" state is not an explicit
                // user-disable: don't display it as "disabled" based only on -d.
                return localExplicitDisabled ? Boolean.FALSE : null;
            }
            if (Boolean.TRUE.equals(local)) return Boolean.TRUE;
            return localExplicitDisabled && Boolean.FALSE.equals(local) ? Boolean.FALSE : null;
        }
    }
}
