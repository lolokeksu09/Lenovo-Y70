package com.moto.resolutionmaster;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * A previous getService("moto_extend_service") returned no Binder to this APK.
 * Record the exact firmware build, not a claim that the OEM service is absent.
 * Explicit user reset and a firmware change both allow a new direct attempt.
 */
public final class MotorolaDirectAccessGate {
    private static final String PREFS = "motorola_direct_access";
    private static final String KEY_DENIED_BUILD = "unavailable_binder_build";
    private static final String KEY_LEGACY_IMPORTED = "legacy_binder_result_imported";

    private MotorolaDirectAccessGate() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
            PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isBlocked(Context context) {
        if (context == null) return false;
        String recorded = prefs(context).getString(KEY_DENIED_BUILD, null);
        return recorded != null && recorded.equals(Build.FINGERPRINT);
    }

    public static void recordMissingBinder(Context context) {
        if (context != null) {
            prefs(context).edit().putString(KEY_DENIED_BUILD, Build.FINGERPRINT).commit();
        }
    }

    /** Import the already-observed v3.3.1 Binder failure exactly once.
     *  A manual reset must not be undone by the old persisted action trace.
     */
    public static void migrateLegacyFailure(Context context, String lastAction) {
        if (context == null) return;
        SharedPreferences storage = prefs(context);
        if (storage.getBoolean(KEY_LEGACY_IMPORTED, false)) return;
        SharedPreferences.Editor edit = storage.edit().putBoolean(KEY_LEGACY_IMPORTED, true);
        if (lastAction != null && lastAction.contains(
                "Сервис moto_extend_service не предоставил Binder нашему APK")) {
            edit.putString(KEY_DENIED_BUILD, Build.FINGERPRINT);
        }
        edit.commit();
    }

    /** The user may deliberately retry after a firmware update or a service change. */
    public static void reset(Context context) {
        if (context != null) prefs(context).edit().remove(KEY_DENIED_BUILD).commit();
    }
}
