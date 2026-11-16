package app.pinduoduo.overlayru;

public final class ModelAvailabilityTest {
    public static void main(String[] args) {
        assertCase(false, false, false);
        assertCase(true, false, false);
        assertCase(false, true, false);
        assertCase(true, true, true);
        System.out.println("PASS: 4 offline model availability combinations");
    }
    private static void assertCase(boolean zh, boolean ru, boolean want) {
        if (ModelAvailability.usable(zh,ru) != want)
            throw new AssertionError("zh="+zh+" ru="+ru+" expected="+want);
    }
}
