package com.moto.resolutionmaster;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

public class PowerHelper {

    private static final String TAG = "PowerHelper";
    private static final String PREF_NAME = "power_helper_prefs";
    private static final String KEY_BYPASS_ACTIVE = "key_bypass_charging_active";

    public static class BatteryInfo {
        public int level = 0;
        public float temperature = 0f;
        public boolean isCharging = false;
        public boolean isPlugged = false;
        public boolean isBypassActive = false;
        public String statusText = "";
    }

    /**
     * Hardware bypass was never verified for this kernel.
     * Android battery simulation and generic charging_enabled writes
     * do not prove a direct PMIC power path, so this control is disabled.
     */
    public static boolean isBypassChargingActive(Context context) {
        return false;
    }

    public static boolean setBypassCharging(Context context, boolean enable) {
        return false;
    }

    public static BatteryInfo getBatteryInfo(Context context) {
        BatteryInfo info = new BatteryInfo();
        info.isBypassActive = isBypassChargingActive(context);

        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    info.level = Math.round((level / (float) scale) * 100);
                }

                int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                info.temperature = temp / 10.0f;

                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                info.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                  status == BatteryManager.BATTERY_STATUS_FULL;

                int plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
                info.isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                                 plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                                 plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;
            }
        } catch (Throwable t) {
            Log.e(TAG, "getBatteryInfo error", t);
        }

        if (info.isBypassActive) {
            info.statusText = "Сквозное питание (Bypass) • Сеть ⚡";
        } else if (info.isCharging) {
            info.statusText = "Зарядка от сети ⚡";
        } else if (info.isPlugged) {
            info.statusText = "Подключено • Батарея полная ✓";
        } else {
            info.statusText = "Питание от аккумулятора";
        }

        return info;
    }

    public static boolean isPowerSaveMode(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isPowerSaveMode();
            }
        } catch (Throwable ignored) {}
        try {
            return Settings.Global.getInt(context.getContentResolver(), "low_power", 0) == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean togglePowerSaveMode(Context context) {
        boolean cur = isPowerSaveMode(context);
        int target = cur ? 0 : 1;
        boolean ok = ShellHelper.runCommand(context, "settings put global low_power " + target);
        if (!ok) {
            try {
                ok = Settings.Global.putInt(context.getContentResolver(), "low_power", target);
            } catch (Throwable ignored) {}
        }
        return ok;
    }

    public static boolean launchBatteryCare(Context context) {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.motorola.batterycare", "com.motorola.batterycare.smartbackground.activity.BackgroundSettingActivity"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable t) {
            try {
                Intent fallback = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
