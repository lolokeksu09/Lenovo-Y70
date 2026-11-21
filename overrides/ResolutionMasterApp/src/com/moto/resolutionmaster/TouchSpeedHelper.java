package com.moto.resolutionmaster;

import android.content.Context;
import android.provider.Settings;

public class TouchSpeedHelper {

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
     * This does not tune the touchscreen sampling rate.
     */
    public static boolean enableFullTurboMode(Context context) {
        boolean hz = DisplayManagerHelper.setRefreshRate(context, DisplayManagerHelper.HZ_144);
        boolean animations = setAnimationScales(context, 0.5f);
        return hz && animations;
    }

    /** Restore display refresh policy and Android animation scales only. */
    public static boolean resetToStock(Context context) {
        boolean hz = DisplayManagerHelper.setRefreshRate(context, DisplayManagerHelper.HZ_AUTO);
        boolean animations = setAnimationScales(context, 1.0f);
        return hz && animations;
    }

}
