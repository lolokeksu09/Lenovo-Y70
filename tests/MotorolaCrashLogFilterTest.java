package com.moto.resolutionmaster;

public final class MotorolaCrashLogFilterTest {
    private static void ok(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        String raw =
            "11-20 11:59:34.993 26173 26488 E AndroidRuntime: FATAL EXCEPTION: MotoGameControl\n" +
            "11-20 11:59:34.993 26173 26488 E AndroidRuntime: Process: com.moto.resolutionmaster, PID: 26173\n" +
            "11-20 11:59:34.993 26173 26488 E AndroidRuntime: java.lang.NoClassDefFoundError\n" +
            "11-20 11:59:34.993 26173 26488 E AndroidRuntime: at com.moto.resolutionmaster.MotoGameController.explain\n" +
            "11-20 11:59:35.010 901 901 E Other: unrelated interleaved log\n" +
            "11-20 22:11:52.103 4895 4895 E AndroidRuntime: FATAL EXCEPTION: main\n" +
            "11-20 22:11:52.103 4895 4895 E AndroidRuntime: Process: com.android.settings, PID: 4895\n" +
            "11-20 22:11:52.103 4895 4895 E AndroidRuntime: java.lang.ArrayIndexOutOfBoundsException\n";
        String report = MotorolaCrashLogFilter.relevantCrashExcerpt(raw);
        ok(report.contains("NoClassDefFoundError"), "relevant Motorola crash kept");
        ok(report.contains("MotoGameController.explain"), "stack retained");
        ok(!report.contains("com.android.settings"), "other app excluded");
        ok(!report.contains("unrelated interleaved"), "other PID excluded");
        String missing = MotorolaCrashLogFilter.relevantCrashExcerpt(
            "11-20 22:11:52.103 4895 4895 E AndroidRuntime: Process: com.android.settings");
        ok(missing.contains("нет аварии"), "no matching process");
        ok(MotorolaCrashLogFilter.relevantCrashExcerpt("").contains("пуст"),
            "empty log handled");
        System.out.println("PASS: crash excerpt excludes unrelated process stacks");
    }
}
