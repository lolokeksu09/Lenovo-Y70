package com.moto.resolutionmaster;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * User-opted-in, one-shot Android Settings assistant.
 *
 * Only observes com.android.settings; no gestures or scanning other apps.
 * No action is taken until the user requests one specific package.
 * It checks the exact application label before clicking ONLY "Включить".
 */
public final class PackageEnableAccessibilityService extends AccessibilityService {
    private static final Object LOCK = new Object();
    private static String pendingPackage;
    private static String pendingLabel;
    private static long pendingAtMs;
    private static final long EXPIRY_MS = 45000;

    public static boolean isEnabled(Context context) {
        try {
            AccessibilityManager manager = (AccessibilityManager)
                context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager == null) return false;
            List<AccessibilityServiceInfo> list =
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            for (AccessibilityServiceInfo info : list) {
                if (info == null || info.getResolveInfo() == null ||
                        info.getResolveInfo().serviceInfo == null) continue;
                android.content.pm.ServiceInfo service = info.getResolveInfo().serviceInfo;
                if (context.getPackageName().equals(service.packageName) &&
                    PackageEnableAccessibilityService.class.getName().equals(service.name)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean arm(Context context, String pkg) {
        if (!isEnabled(context) || pkg == null ||
                !pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") ||
                PackageHelper.isProtectedInDebloater(context, pkg)) {
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            String label = cs == null ? "" : cs.toString().trim();
            if (label.isEmpty()) return false;
            synchronized (LOCK) {
                pendingPackage = pkg;
                pendingLabel = label;
                pendingAtMs = SystemClock.elapsedRealtime();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void cancel() {
        synchronized (LOCK) {
            pendingPackage = null;
            pendingLabel = null;
            pendingAtMs = 0;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null ||
                !"com.android.settings".contentEquals(event.getPackageName())) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;

        String expectedLabel;
        long age;
        synchronized (LOCK) {
            if (pendingPackage == null) return;
            age = SystemClock.elapsedRealtime() - pendingAtMs;
            if (age < 0 || age > EXPIRY_MS) {
                cancel();
                return;
            }
            expectedLabel = pendingLabel;
        }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null ||
                !"com.android.settings".contentEquals(root.getPackageName())) return;

        boolean targetVisible = findExactVisibleLabel(root, expectedLabel, 0);
        AccessibilityNodeInfo button = findUniqueEnableButton(root);
        if (button == null) return;
        CharSequence buttonText = button.getText();
        String action = buttonText == null ? "" : buttonText.toString();
        if (!EnableAssistPolicy.canClick("com.android.settings", targetVisible,
                action, button.isEnabled(), age)) return;

        AccessibilityNodeInfo clickable = button;
        for (int depth = 0; depth < 3 && clickable != null && !clickable.isClickable(); depth++) {
            clickable = clickable.getParent();
        }
        if (clickable == null || !clickable.isClickable() || !clickable.isEnabled()) return;

        // Consume authorization BEFORE interacting. No second click is possible.
        cancel();
        if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            // Return to the originating Lenovo Y70 screen for PackageManager
            // verification. No assumption about the success of the click.
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
            }, 650);
        }
    }

    private static boolean findExactVisibleLabel(AccessibilityNodeInfo node,
                                                 String label, int depth) {
        if (node == null || depth > 28) return false;
        CharSequence text = node.getText();
        if (node.isVisibleToUser() && text != null &&
                label.equals(text.toString().trim())) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findExactVisibleLabel(node.getChild(i), label, depth + 1)) return true;
        }
        return false;
    }

    private static AccessibilityNodeInfo findUniqueEnableButton(AccessibilityNodeInfo root) {
        java.util.ArrayList<AccessibilityNodeInfo> matches = new java.util.ArrayList<>();
        collectEnableButtons(root, 0, matches);
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static void collectEnableButtons(AccessibilityNodeInfo node, int depth,
                                             java.util.List<AccessibilityNodeInfo> matches) {
        if (node == null || depth > 28 || matches.size() > 1) return;
        CharSequence text = node.getText();
        if (node.isVisibleToUser() && node.isEnabled() && text != null) {
            String t = text.toString().trim();
            if ("Включить".equals(t) || "Enable".equalsIgnoreCase(t)) matches.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectEnableButtons(node.getChild(i), depth + 1, matches);
        }
    }

    @Override public void onInterrupt() { cancel(); }
    @Override public void onDestroy() { cancel(); super.onDestroy(); }
}
