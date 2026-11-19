package com.moto.resolutionmaster;

public final class EnableAssistPolicyTest {
    private static void eq(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) {
        eq(EnableAssistPolicy.canClick("com.android.settings", true, "Включить", true, 250),
            "Correct app page in Russian permits requested action");
        eq(EnableAssistPolicy.canClick("com.android.settings", true, "Enable", true, 450),
            "English device permits requested action");
        eq(!EnableAssistPolicy.canClick("com.android.settings", false, "Включить", true, 300),
            "Unverified app identity never permits click");
        eq(!EnableAssistPolicy.canClick("com.other.app", true, "Включить", true, 300),
            "Other app window must never be controlled");
        eq(!EnableAssistPolicy.canClick("com.android.settings", true, "Удалить", true, 300),
            "Delete cannot be clicked");
        eq(!EnableAssistPolicy.canClick("com.android.settings", true, "Отключить", true, 300),
            "Disable cannot be clicked");
        eq(!EnableAssistPolicy.canClick("com.android.settings", true, "Включить", false, 300),
            "Disabled action cannot be clicked");
        eq(!EnableAssistPolicy.canClick("com.android.settings", true, "Включить", true, 45001),
            "Expired authorization cannot act");
        eq(!EnableAssistPolicy.canClick("com.android.settings", true, "Включить", true, -1),
            "Negative age is invalid");
        System.out.println("PASS: one-shot Settings-only, exact label and enable-only policy");
    }
}
