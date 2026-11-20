package com.moto.resolutionmaster;

import android.content.Context;
import android.content.SharedPreferences;

/** Private, bounded local report; no raw dumpsys, network, or external storage. */
public final class MotorolaCaptureStore {
    private static final String PREFS = "motorola_capture";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_STARTED = "started";
    private static final String KEY_STATUS = "status";
    private static final String KEY_REPORT = "report";
    private static final long EXPIRY_MS = 180000L;

    private MotorolaCaptureStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(
            PREFS, Context.MODE_PRIVATE);
    }

    public static boolean begin(Context context) {
        return prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_STARTED, System.currentTimeMillis())
            .putString(KEY_STATUS, "Запись идёт")
            .putString(KEY_REPORT, "")
            .commit();
    }

    public static void progress(Context context, String report, int samples) {
        prefs(context).edit()
            .putString(KEY_REPORT, report)
            .putString(KEY_STATUS, "Запись идёт · " + samples + "/" +
                MotorolaCaptureReport.MAX_SAMPLES)
            .apply();
    }

    public static void finish(Context context, String status, String report) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .putString(KEY_STATUS, status)
            .putString(KEY_REPORT, report)
            .commit();
    }

    public static boolean isActive(Context context) {
        expireStale(context);
        return prefs(context).getBoolean(KEY_ACTIVE, false);
    }

    public static String status(Context context) {
        expireStale(context);
        return prefs(context).getString(KEY_STATUS, "Запись ещё не запускалась");
    }

    public static String report(Context context) {
        return prefs(context).getString(KEY_REPORT, "Нет записанного сеанса");
    }

    private static void expireStale(Context context) {
        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_ACTIVE, false)) return;
        long started = p.getLong(KEY_STARTED, 0L);
        long elapsed = System.currentTimeMillis() - started;
        if (started > 0 && (elapsed > EXPIRY_MS || elapsed < -60000L)) {
            p.edit().putBoolean(KEY_ACTIVE, false)
                .putString(KEY_STATUS,
                    "Сеанс прервался: приложение или служба были остановлены")
                .commit();
        }
    }
}
