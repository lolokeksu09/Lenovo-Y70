package com.moto.resolutionmaster;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Durable, non-sensitive breadcrumb for an interrupted Motorola OEM call.
 *
 * An app process can terminate before a Java catch/finally (including native
 * signals or system process kills). Persisting a pending operation BEFORE
 * crossing the OEM boundary lets the next app launch report that no verified
 * outcome was received. This is not a substitute for logcat crash details.
 */
public final class MotorolaControlTrace {
    private static final String PREFS = "motorola_control_trace";
    private static final String KEY_PENDING = "pending_operation";
    private static final String KEY_LAST = "last_completed_operation";

    private MotorolaControlTrace() {}

    public static boolean begin(Context context, String operation) {
        if (context == null || operation == null) return false;
        return prefs(context).edit()
            .putString(KEY_PENDING, operation).commit();
    }

    public static void finish(Context context, String outcome) {
        if (context == null) return;
        prefs(context).edit()
            .remove(KEY_PENDING)
            .putString(KEY_LAST, outcome == null ? "Нет результата" : outcome)
            .commit();
    }

    public static String pending(Context context) {
        if (context == null) return null;
        return prefs(context).getString(KEY_PENDING, null);
    }

    public static String lastCompleted(Context context) {
        if (context == null) return null;
        return prefs(context).getString(KEY_LAST, null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
            PREFS, Context.MODE_PRIVATE);
    }
}
