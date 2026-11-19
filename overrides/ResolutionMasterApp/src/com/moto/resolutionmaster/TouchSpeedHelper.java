package com.moto.resolutionmaster;

import android.content.Context;
import android.provider.Settings;

public class TouchSpeedHelper {

    public static final int MIN_SPEED = -7;
    public static final int MAX_SPEED = 7;
    public static final int DEFAULT_SPEED = 0;

    public static void invalidateCache() {
        // No cached success: statuses reflect the current Android setting.
    }

    /** Speed of a mouse/trackpad pointer; never touchscreen sampling. */
    public static int getPointerSpeed(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(), "pointer_speed", DEFAULT_SPEED);
        } catch (Throwable ignored) {
            return DEFAULT_SPEED;
        }
    }

    public static boolean setPointerSpeed(Context context, int speed) {
        speed = Math.max(MIN_SPEED, Math.min(MAX_SPEED, speed));
        boolean ok = ShellHelper.runCommand(context, "settings put system pointer_speed " + speed);
        if (!ok) {
            try { ok = Settings.System.putInt(context.getContentResolver(), "pointer_speed", speed); }
            catch (Throwable ignored) {}
        }
        if (!ok) return false;
        try {
            return Settings.System.getInt(context.getContentResolver(), "pointer_speed", 1000) == speed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String getSpeedLabel(int speed) {
        return "Указатель: " + (speed > 0 ? "+" : "") + speed +
            (speed == 0 ? " (стандарт)" : " (мышь/трекпад)");
    }

    /**
     * Converts SeekBar progress (0..14) to speed (-7..+7).
     */
    public static int progressToSpeed(int progress) {
        return progress - 7;
    }

    /**
     * Converts speed (-7..+7) to SeekBar progress (0..14).
     */
    public static int speedToProgress(int speed) {
        return speed + 7;
    }

    /**
     * Reads current animation scale (1.0 = standard, 0.5 = 2x faster).
     */
    public static float getAnimationScale(Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f);
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    /**
     * Sets animation scales to 0.5x, 1.0x, etc.
     */
    public static boolean setAnimationScales(Context context, float scale) {
        String scaleStr = String.valueOf(scale);
        String cmd = "settings put global window_animation_scale " + scaleStr + " && " +
                     "settings put global transition_animation_scale " + scaleStr + " && " +
                     "settings put global animator_duration_scale " + scaleStr;
        return ShellHelper.runCommand(context, cmd);
    }

    /**
     * Human-readable label for animation scale.
     */
    public static String getAnimationScaleLabel(float scale) {
        if (Math.abs(scale - 0.5f) < 0.05f) {
            return "0.5x (Ускорено в 2 раза 🚀)";
        } else if (Math.abs(scale - 1.0f) < 0.05f) {
            return "1.0x (Стандартное время ⚖️)";
        } else if (scale < 0.1f) {
            return "Без анимаций (0.0x ⚡)";
        } else {
            return scale + "x";
        }
    }

    /**
     * Requests 144 Hz and faster Android UI transitions.
     * Actual refresh rate still depends on power saving and the firmware.
     * The mode does not touch pointer_speed or sampling rate.
     */
    public static boolean enableFullTurboMode(Context context) {
        boolean hz = DisplayManagerHelper.setRefreshRate(context, DisplayManagerHelper.HZ_144);
        boolean animations = setAnimationScales(context, 0.5f);
        return hz && animations;
    }

    /** Restore requested display policy, pointer speed and animation scale. */
    public static boolean resetToStock(Context context) {
        boolean hz = DisplayManagerHelper.setRefreshRate(context, DisplayManagerHelper.HZ_AUTO);
        boolean pointer = setPointerSpeed(context, 0);
        boolean animations = setAnimationScales(context, 1.0f);
        return hz && pointer && animations;
    }

}
