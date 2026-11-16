package app.pinduoduo.overlayru;

public final class AccessibilityServiceIdsTest {
    private static final String PKG = "app.pinduoduo.overlayru";
    private static final String CLS = PKG + ".PddAccessibilityService";
    private static void check(boolean actual, boolean expected, String caseName) {
        if (actual != expected) throw new AssertionError(caseName);
    }
    public static void main(String[] args) {
        check(AccessibilityServiceIds.contains(PKG + "/" + CLS, PKG, CLS),true, "diagnostic full name");
        check(AccessibilityServiceIds.contains(PKG + "/.PddAccessibilityService", PKG, CLS),true,"short component");
        check(AccessibilityServiceIds.contains("com.example/.One:" + PKG + "/" + CLS, PKG, CLS),true,"multiple services");
        check(AccessibilityServiceIds.contains("other." + PKG + "/" + CLS, PKG, CLS),false,"wrong package");
        check(AccessibilityServiceIds.contains(PKG + "/" + CLS + "Fake", PKG, CLS),false,"wrong class");
        check(AccessibilityServiceIds.contains(PKG + "/.AnotherService", PKG, CLS),false,"another service");
        check(AccessibilityServiceIds.contains(null, PKG, CLS),false,"null");
        check(AccessibilityServiceIds.contains("garbage", PKG, CLS),false,"malformed");
        System.out.println("PASS: 8 exact accessibility ID scenarios");
    }
}
