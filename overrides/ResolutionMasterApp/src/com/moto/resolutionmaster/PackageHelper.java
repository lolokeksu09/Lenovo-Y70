package com.moto.resolutionmaster;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PackageHelper {

    private static final String TAG = "PackageHelper";
    private static final String PREF_APPOPS = "appops_freeze";

    // Packages protected by Motorola firmware (in /system_ext/etc/nondisable/ or marked protected)
    public static final Set<String> NONDISABLE_PACKAGES = new HashSet<>(Arrays.asList(
        "com.motorola.paks",
        "com.motorola.paks.notification",
        "com.motorola.ccc.devicemanagement"
    ));

    public interface BatchCallback {
        void onProgress(int current, int total, String packageName, boolean success);
        void onComplete(int successCount, int total);
    }

    public static boolean isProtectedOrNonDisable(String packageName) {
        return packageName != null && NONDISABLE_PACKAGES.contains(packageName);
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final String PREF_JOURNAL = "debloat_v31_journal";
    private static final String KEY_MODIFIED_PACKAGES = "packages_disabled_by_app";
    private static final java.util.concurrent.ConcurrentHashMap<String, String> LAST_ERRORS =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static void error(String packageName, String message) {
        LAST_ERRORS.put(packageName, message == null ? "Ошибка операции" : message);
        Log.w(TAG, packageName + ": " + LAST_ERRORS.get(packageName));
    }

    public static String getLastOperationError(String packageName) {
        String value = LAST_ERRORS.get(packageName);
        return value == null ? "Операция не подтверждена. Проверьте Shizuku/Root." : value;
    }

    public static synchronized Set<String> getModifiedPackages(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_JOURNAL, Context.MODE_PRIVATE);
        return new HashSet<>(sp.getStringSet(KEY_MODIFIED_PACKAGES, Collections.<String>emptySet()));
    }

    public static synchronized boolean isModifiedByApp(Context context, String packageName) {
        return getModifiedPackages(context).contains(packageName);
    }

    /**
     * Journal MUST be durable before a disabling command. If the process dies
     * after a successful pm command, the rollback still knows what to restore.
     */
    private static synchronized boolean recordBeforeDisable(Context context, String packageName) {
        SharedPreferences sp = context.getSharedPreferences(PREF_JOURNAL, Context.MODE_PRIVATE);
        Set<String> modified = getModifiedPackages(context);
        modified.add(packageName);
        return sp.edit().putStringSet(KEY_MODIFIED_PACKAGES, modified).commit();
    }

    private static synchronized void clearAfterRestore(Context context, String packageName) {
        SharedPreferences sp = context.getSharedPreferences(PREF_JOURNAL, Context.MODE_PRIVATE);
        Set<String> modified = getModifiedPackages(context);
        if (modified.remove(packageName)) {
            sp.edit().putStringSet(KEY_MODIFIED_PACKAGES, modified).commit();
        }
    }

    /**
     * null = cannot verify through PackageManager. Never interpret null as disabled.
     * A SharedPreferences flag does NOT prove the live package or AppOps state.
     */
    /**
     * Actual enabled/disabled PackageManager state for UI filters.
     * Suspension is a separate Android state and must not appear as "disabled".
     */
    private static final class LocalState {
        final Boolean enabled;
        final boolean explicitlyDisabled;
        LocalState(Boolean enabled, boolean explicitlyDisabled) {
            this.enabled = enabled;
            this.explicitlyDisabled = explicitlyDisabled;
        }
    }

    private static LocalState readLocalState(Context context, String packageName) {
        if (packageName == null || packageName.isEmpty()) return new LocalState(null, false);
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            int state;
            try {
                state = pm.getApplicationEnabledSetting(packageName);
            } catch (Throwable error) {
                Log.w(TAG, "Enabled setting cannot be read for " + packageName, error);
                return new LocalState(ai.enabled ? Boolean.TRUE : null, false);
            }

            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                return new LocalState(Boolean.TRUE, false);
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                // A contradictory firmware report is UNKNOWN until verified by shell.
                return new LocalState(ai.enabled ? null : Boolean.FALSE, true);
            }
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                // Android distinguishes "disabled until used" from user-disabled.
                return new LocalState(null, false);
            }
            return new LocalState(ai.enabled ? Boolean.TRUE : null, false);
        } catch (Throwable error) {
            Log.w(TAG, "Package state not readable for " + packageName, error);
            return new LocalState(null, false);
        }
    }

    /** In-process state only. A null status must never be treated as disabled. */
    public static Boolean getPackageEnabledState(Context context, String packageName) {
        return readLocalState(context, packageName).enabled;
    }

    /**
     * Query the same user (0) targeted by Debloater's pm operations.
     * Both successful -e and -d lists are required to classify a shell result.
     * Called in the background once per full list, not once per package.
     */
    private static PackageStateResolver.Snapshot readShellPackageSnapshot(Context context) {
        if (!ShellHelper.isShizukuPermissionGranted() && !ShellHelper.isRootAvailable()) {
            return PackageStateResolver.Snapshot.fromShell(false, "", false, "");
        }
        ShellHelper.CommandResult enabled = ShellHelper.runCommandWithResult(
            context, "pm list packages -e --user 0");
        ShellHelper.CommandResult disabled = ShellHelper.runCommandWithResult(
            context, "pm list packages -d --user 0");
        return PackageStateResolver.Snapshot.fromShell(
            enabled.success, enabled.detail, disabled.success, disabled.detail);
    }

    /** Use for a single confirmation after a user-initiated operation. */
    public static Boolean getVerifiedPackageEnabledState(Context context, String packageName) {
        LocalState local = readLocalState(context, packageName);
        PackageStateResolver.Snapshot snapshot = readShellPackageSnapshot(context);
        return snapshot.resolve(packageName, local.enabled, local.explicitlyDisabled, snapshot);
    }

    /**
     * Effective state for existing package operations. A suspended package is
     * operationally unavailable but remains *enabled* in the UI category.
     */
    private static Boolean readEnabledState(Context context, String packageName) {
        Boolean declaredEnabled = getPackageEnabledState(context, packageName);
        if (!Boolean.TRUE.equals(declaredEnabled)) return declaredEnabled;
        try {
            if (context.getPackageManager().isPackageSuspended(packageName)) {
                return Boolean.FALSE;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Suspension status unavailable for " + packageName, t);
        }
        return declaredEnabled;
    }

    public static boolean isPackageEnabled(Context context, String packageName) {
        return Boolean.TRUE.equals(readEnabledState(context, packageName));
    }

    /** Only genuinely disabled (not suspended) packages qualify. */
    public static boolean isPackageDisabledVerified(Context context, String packageName) {
        return Boolean.FALSE.equals(getPackageEnabledState(context, packageName));
    }

    /**
     * Protect dialers, especially the current default phone handler.
     * The system Settings may forbid disabling it even if vendor package
     * APIs report confusing enabled states.
     */
    public static boolean isProtectedDialer(Context context, String packageName) {
        if ("com.android.dialer".equals(packageName)) return true;
        if (packageName == null) return false;
        try {
            android.telecom.TelecomManager tm = (android.telecom.TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
            return tm != null && packageName.equals(tm.getDefaultDialerPackage());
        } catch (Throwable e) {
            Log.w(TAG, "Cannot determine default dialer", e);
            return false;
        }
    }

    public static boolean isProtectedInDebloater(Context context, String packageName) {
        return isProtectedOrNonDisable(packageName) || isCriticalPackage(context, packageName);
    }

    private static boolean isCriticalPackage(Context context, String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.]+")) return true;
        if (isProtectedDialer(context, packageName)) return true;
        if (packageName.equals(context.getPackageName()) ||
                packageName.equals(ShellHelper.SHIZUKU_PACKAGE) ||
                "android".equals(packageName) ||
                "com.android.systemui".equals(packageName) ||
                "com.android.settings".equals(packageName) ||
                "com.android.phone".equals(packageName) ||
                "com.android.providers.settings".equals(packageName) ||
                "com.android.permissioncontroller".equals(packageName) ||
                "com.google.android.permissioncontroller".equals(packageName) ||
                "com.android.launcher3".equals(packageName) ||
                "com.motorola.launcher3".equals(packageName) ||
                "com.google.android.gms".equals(packageName) ||
                "com.android.networkstack".equals(packageName) ||
                "com.google.android.networkstack".equals(packageName)) return true;
        try {
            android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN);
            home.addCategory(android.content.Intent.CATEGORY_HOME);
            android.content.pm.ResolveInfo ri = context.getPackageManager().resolveActivity(
                home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return true; // Fail closed.
            return packageName.equals(ri.activityInfo.packageName);
        } catch (Throwable t) {
            // Refuse debloat when the active launcher cannot be verified.
            return true;
        }
    }

    /**
     * Only issue verifiable package operations. Protected Motorola AppOps must
     * not be marked as frozen without reading the actual system state.
     */
    public static boolean disablePackage(Context context, String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.]+")) {
            error(String.valueOf(packageName), "Некорректное имя пакета");
            return false;
        }
        LAST_ERRORS.remove(packageName);
        if (isCriticalPackage(context, packageName)) {
            error(packageName, "Критический пакет защищён от отключения");
            return false;
        }
        if (isProtectedOrNonDisable(packageName)) {
            error(packageName, "Пакет защищён прошивкой; изоляция AppOps без проверки отключена");
            return false;
        }
        Boolean before = readEnabledState(context, packageName);
        if (before == null) {
            error(packageName, "Не удалось прочитать текущее состояние пакета");
            return false;
        }
        if (!before) return true; // Do not claim this pre-existing disabled package.

        if (!ShellHelper.isRootAvailable() && !ShellHelper.isShizukuPermissionGranted()) {
            error(packageName, "Нет разрешения Shizuku/Root");
            return false;
        }
        if (!recordBeforeDisable(context, packageName)) {
            error(packageName, "Не удалось сохранить журнал отката; действие отменено");
            return false;
        }

        ShellHelper.CommandResult disable = ShellHelper.runCommandWithResult(
            context, "pm disable-user --user 0 " + packageName);
        Boolean after = readEnabledState(context, packageName);
        if (Boolean.FALSE.equals(after)) return true;

        ShellHelper.CommandResult suspend = ShellHelper.runCommandWithResult(
            context, "pm suspend --user 0 " + packageName);
        after = readEnabledState(context, packageName);
        if (Boolean.FALSE.equals(after)) return true;

        // If it stayed enabled, remove the pending record. Unknown state keeps
        // the record for recovery after a crash or permission loss.
        if (Boolean.TRUE.equals(after)) clearAfterRestore(context, packageName);
        error(packageName, "Отключение не подтверждено. pm: " + disable.detail +
            " (" + disable.exitCode + "), suspend: " + suspend.detail + " (" + suspend.exitCode + ")");
        return false;
    }

    public static boolean enablePackage(Context context, String packageName) {
        LAST_ERRORS.remove(packageName);
        if (packageName == null || !packageName.matches("[A-Za-z0-9_.]+")) {
            error(String.valueOf(packageName), "Некорректное имя пакета");
            return false;
        }
        if (isProtectedOrNonDisable(packageName)) {
            error(packageName, "Для этого защищённого пакета автоматическое изменение AppOps недоступно");
            return false;
        }
        Boolean before = readEnabledState(context, packageName);
        if (before == null) {
            error(packageName, "Не удалось прочитать текущее состояние пакета");
            return false;
        }
        if (before) {
            clearAfterRestore(context, packageName);
            return true;
        }
        ShellHelper.CommandResult enabled = ShellHelper.runCommandWithResult(
            context, "pm enable --user 0 " + packageName);
        ShellHelper.CommandResult unsuspended = ShellHelper.runCommandWithResult(
            context, "pm unsuspend --user 0 " + packageName);
        Boolean after = readEnabledState(context, packageName);
        if (Boolean.TRUE.equals(after)) {
            clearAfterRestore(context, packageName);
            return true;
        }
        error(packageName, "Восстановление не подтверждено. pm: " + enabled.detail +
            " (" + enabled.exitCode + "), unsuspend: " + unsuspended.detail +
            " (" + unsuspended.exitCode + ")");
        return false;
    }

    public static List<AppInfo> getInstalledApps(Context context) {
        List<AppInfo> result = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        final PackageStateResolver.Snapshot snapshot = readShellPackageSnapshot(context);

        try {
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
            for (PackageInfo pi : packages) {
                if (pi.applicationInfo == null) continue;

                String pkg = pi.packageName;
                // Exclude self from disabling
                if ("com.moto.resolutionmaster".equals(pkg) || "moe.shizuku.privileged.api".equals(pkg)) {
                    continue;
                }

                String label;
                try {
                    CharSequence cs = pi.applicationInfo.loadLabel(pm);
                    label = (cs != null) ? cs.toString() : pkg;
                } catch (Throwable t) {
                    label = pkg;
                }

                Drawable icon;
                try {
                    icon = pi.applicationInfo.loadIcon(pm);
                } catch (Throwable t) {
                    icon = pm.getDefaultActivityIcon();
                }

                boolean isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                LocalState local = readLocalState(context, pkg);
                Boolean enabledState = snapshot.resolve(
                    pkg, local.enabled, local.explicitlyDisabled, snapshot);

                PackageDescriptions.PackageMeta meta = PackageDescriptions.getMeta(pkg);

                result.add(new AppInfo(pkg, label, icon, isSystem, enabledState,
                        meta.description, meta.safetyLevel, meta.getSafetyLabel(), meta.getSafetyColor()));
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load installed apps", t);
        }

        Collections.sort(result, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo o1, AppInfo o2) {
                return o1.label.compareToIgnoreCase(o2.label);
            }
        });

        return result;
    }

    public static void runBatchDisable(final Context context, final List<String> packageList, final BatchCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int total = packageList.size();
                int success = 0;
                for (int i = 0; i < total; i++) {
                    String pkg = packageList.get(i);
                    boolean ok = disablePackage(context, pkg);
                    if (ok) success++;
                    if (callback != null) {
                        callback.onProgress(i + 1, total, pkg, ok);
                    }
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {}
                }
                if (callback != null) {
                    callback.onComplete(success, total);
                }
            }
        }).start();
    }

    public static void runBatchEnable(final Context context, final List<String> packageList, final BatchCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                int total = packageList.size();
                int success = 0;
                for (int i = 0; i < total; i++) {
                    String pkg = packageList.get(i);
                    boolean ok = enablePackage(context, pkg);
                    if (ok) success++;
                    if (callback != null) {
                        callback.onProgress(i + 1, total, pkg, ok);
                    }
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException ignored) {}
                }
                if (callback != null) {
                    callback.onComplete(success, total);
                }
            }
        }).start();
    }
}
