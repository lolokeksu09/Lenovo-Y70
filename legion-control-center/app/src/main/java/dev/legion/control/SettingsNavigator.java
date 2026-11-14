package dev.legion.control;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Only launches user-facing system Settings. Never writes protected state. */
public final class SettingsNavigator {
    private SettingsNavigator() {}

    public static boolean openAppInfo(Activity activity,String packageName) {
        if(!AccessStatus.validPackage(packageName))return false;
        Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package",packageName,null));
        try {
            activity.startActivity(intent);
            return true;
        }catch(ActivityNotFoundException|SecurityException e){
            return false;
        }
    }

    /** Open the actual system app list instead of direct App Info.
     * Some Settings implementations hide 'Allow restricted settings' on deep-linked App Info.
     */
    public static boolean openAppList(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS));
            return true;
        }catch(ActivityNotFoundException|SecurityException e){
            return false;
        }
    }

    public static boolean openAccessibility(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return true;
        }catch(ActivityNotFoundException|SecurityException e){
            return false;
        }
    }
}
