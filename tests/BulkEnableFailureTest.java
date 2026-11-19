package com.moto.resolutionmaster;

public final class BulkEnableFailureTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String exception = "Exception occurred while executing 'enable':\n" +
            "java.lang.SecurityException: Shell cannot change component state for null to 1\n" +
            " at com.android.server.pm.PackageManagerService.setEnabledSettings(PackageManagerService.java:4286)";
        check(BulkEnableFailure.isSystemRestricted(255, exception),
            "Motorola shell SecurityException must be recognized as device restriction");
        String message = BulkEnableFailure.explain(255, exception);
        check(message.startsWith("Ограничение Android:"),
            "Shell denial should be shown as Android restriction, not generic error");
        check(message.length() < 160 && !message.contains("\n") && !message.contains("java.lang"),
            "User message must be concise, not a raw Java stack trace");
        check(!BulkEnableFailure.isSystemRestricted(0, "result=ok"), "Successful operation is not restricted");
        check(!BulkEnableFailure.isSystemRestricted(1, "No such package"),
            "Missing package must not be mistaken for a permission block");
        check(BulkEnableFailure.explain(1, "No such package").contains("не найден"),
            "Unknown package needs an actionable message");
        check(!BulkEnableFailure.explain(7, "line 1\n at com.foo.Bar").contains("\n"),
            "Other errors must also be one line");
        check(BulkEnableFailure.explain(7, "line 1\n at com.foo.Bar").length() < 160,
            "Other errors must be bounded");
        System.out.println("PASS: restricted OEM package errors classified and stack traces hidden");
    }
}
