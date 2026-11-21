package com.moto.resolutionmaster;

public final class MotorolaCaptureFormatTest {
    private static void ok(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        String command = MotorolaCaptureFormat.LIGHT_COMMAND;
        ok(command.contains("getprop vendor.motosxf.mode"), "read MAXE");
        ok(command.contains("getprop vendor.thermal.mode"), "read thermal mode");
        ok(command.contains("pidof com.riotgames.league.wildrift"), "detect game");
        ok(!command.contains("setprop ") && !command.contains("/sys/") &&
           !command.contains("restart ") && !command.contains("service call "),
           "capture must not change device state");

        String empty = MotorolaCaptureFormat.light(
            "MOTOSXF=\nTHERMAL=\nENGINE=running\nGAME_PID=\n");
        ok(empty.contains("MAXE=unset"), "empty property not assumed default");
        ok(empty.contains("game-process=not-running"), "no game PID");
        String game = MotorolaCaptureFormat.light(
            "MOTOSXF=game-perf\nTHERMAL=game-perf\nENGINE=running\nGAME_PID=2012\n");
        ok(game.contains("game-process=running"), "detect game process");
        ok(game.contains("thermal-property=game-perf"), "real property value");
        ok(!game.contains("loaded"), "property does not prove loaded config");
        ok(MotorolaCaptureFormat.light("ENGINE=running\n").contains("неполный"),
           "truncated readback must be unknown");

        String moto = "MotoExtendService\n  GameModeStateController\n" +
            "    isGameModeActive=true\n" +
            "    mAidlService=com.motorola.hardware.display.touch.IMotTouch\n" +
            "    token=x bundle=Bundle[{touch_enhance_enable=1, " +
            "game_mode_op_package_name=com.motorola.gamemode, " +
            "game_package_name=com.riotgames.league.wildrift}]\n" +
            "  MotoPowerHealthManagerService State:\n" +
            "    isDPSSupported=true\n    mCurrentHardwareDpsStatus=0\n";
        String detected = MotorolaCaptureFormat.motorola(moto);
        ok(detected.contains("GameTime-mode=on"), "read actual game mode");
        ok(detected.contains("GameTime-touch-request=on"), "read touch request");
        ok(detected.contains("DPS-hardware=0"), "read hardware status");
        ok(MotorolaCaptureFormat.motorola("Permission Denial").contains("нет подтверждённых"),
           "permission denied must not report off");

        MotorolaCaptureReport report = new MotorolaCaptureReport();
        for (int i = 0; i < 20; i++) {
            report.append(i * 8, "game-process=running", null, i == 3);
        }
        ok(report.samples() == 12, "bounded sample count");
        ok(report.failures() == 1, "error count");
        String rendered = report.render("Завершено");
        ok(rendered.contains("Сеанс: Завершено"), "completion label");
        ok(rendered.contains("не измерялись"), "hardware measurement disclaimer");
        ok(!rendered.contains("t+96s"), "must not leak samples past cap");
        System.out.println("PASS: read-only Wild Rift capture summary, errors and bounded report");
    }
}
