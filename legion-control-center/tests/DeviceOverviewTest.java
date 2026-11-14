package dev.legion.control;

public final class DeviceOverviewTest {
    private static int tests = 0;
    private static void ok(boolean condition, String label) {
        tests++;
        if (!condition) throw new AssertionError(label);
    }
    private static String value(SystemInspector.Result result, String name) {
        for (SystemInspector.Item item : result.items) if (item.name.equals(name)) return item.state;
        return "";
    }
    public static void main(String[] args) {
        String raw = "Model: motorola edge 70 max\n"+
                "SKU: XT2611-1\nSoC: SM8845\nAndroid: 17\n"+
                "Power saving: 1\nPeak refresh: 120.0\nMinimum refresh: 120.0\n";
        SystemInspector.Result result = SystemInspector.device(raw);
        ok(result.headline.equals("Lenovo Legion Y70"), "SKU identifies Lenovo Y70 rather than firmware model");
        ok(result.items.size() == 6, "six structured facts");
        ok(value(result, "Аппаратный код").equals("XT2611-1"), "hardware identifier");
        ok(value(result, "Установленная прошивка").equals("motorola edge 70 max"), "software name separated");
        ok(value(result, "Платформа SoC").equals("SM8845"), "soc verified");
        ok(value(result, "Версия Android").equals("17"), "android version verified");
        ok(value(result, "Энергосбережение").equals("Включено"), "power saving is expected");
        ok(value(result, "Частота в настройках").equals("120 Гц"), "no fabricated current fps");
        ok(result.items.get(5).detail.contains("НЕ фактическая"), "distinguishes configured limits from active refresh");
        String brief = SystemInspector.deviceBrief(result);
        ok(brief.contains("Android 17"), "summary contains android version");
        ok(brief.contains("энергосбережение включено"), "summary contains power saving state");
        ok(!brief.contains("uid="), "summary does not expose shell UID");
        ok(!brief.contains("groups="), "summary does not expose shell groups");
        ok(!brief.contains("motorola edge 70 max"), "summary does not confuse software name for device name");
        ok(CommandPolicy.permittedCommand(CommandPolicy.DEVICE_SUMMARY_COMMAND), "new read permitted via allowlist");
        ok(!CommandPolicy.DEVICE_SUMMARY_COMMAND.contains(" id;"), "new command does not request shell identity");
        ok(CommandPolicy.DEVICE_SUMMARY_COMMAND.length() <= 500, "service command size supported");
        ok(!CommandPolicy.permittedCommand(CommandPolicy.DEVICE_SUMMARY_COMMAND+"; reboot"), "no command injection");
        SystemInspector.Result alt = SystemInspector.device(raw.replace("XT2611-1","OTHER"));
        ok(!alt.headline.contains("Lenovo"), "unknown SKU does not get false Lenovo label");
        SystemInspector.Result empty = SystemInspector.device("error: service unavailable");
        ok(empty.items.isEmpty(), "malformed output does not produce fake device facts");
        ok(SystemInspector.deviceBrief(empty).contains("Не удалось"), "empty display has recovery text");
        SystemInspector.Result auto=SystemInspector.device(raw.replace("Peak refresh: 120.0","Peak refresh: null")
                .replace("Minimum refresh: 120.0","Minimum refresh: null").replace("Power saving: 1","Power saving: 0"));
        ok(value(auto,"Частота в настройках").equals("Авто"), "auto refresh mode represented correctly");
        ok(value(auto,"Энергосбережение").equals("Выключено"), "power saver off");
        System.out.println("DeviceOverviewTest PASS (" + tests + " checks)");
    }
}
