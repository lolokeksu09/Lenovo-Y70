package com.moto.resolutionmaster;

public final class MotorolaThermalStateTest {
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }

    public static void main(String[] args) {
        String cmd = MotorolaThermalState.READ_COMMAND;
        check(cmd.contains("getprop vendor.motosxf.mode"), "MAXE read");
        check(cmd.contains("getprop vendor.thermal.mode"), "thermal read");
        check(cmd.contains("getprop init.svc.thermal-engine"), "engine read");
        check(cmd.contains("-f /vendor/etc/thermal-engine-avr-game-perf.conf"), "config existence");
        check(!cmd.contains("setprop ") && !cmd.contains("restart ") &&
              !cmd.contains("stop thermal-engine") && !cmd.contains("start thermal-engine"),
              "probe must not write or restart");

        String running = MotorolaThermalState.parse(
            "MOTOSXF=game-perf\nTHERMAL=game-perf\n" +
            "ENGINE=running\nGAME_PERF_CONFIG=present\n");
        check(running.contains("MAXE: game-perf"), "MAXE value");
        check(running.contains("Thermal property: game-perf"), "thermal value");
        check(running.contains("game-perf.conf: найден"), "config found");
        check(running.contains("не установлен"), "no false claim of loaded profile");

        String empty = MotorolaThermalState.parse(
            "MOTOSXF=\nTHERMAL=\nENGINE=running\nGAME_PERF_CONFIG=present\n");
        check(empty.contains("MAXE: не задано"), "empty MAXE not treated as default");
        check(empty.contains("Thermal property: не задано"), "empty thermal not active");
        check(!empty.contains("стандартный"), "do not infer default config");

        String missing = MotorolaThermalState.parse(
            "MOTOSXF=\nTHERMAL=\nENGINE=running\nGAME_PERF_CONFIG=absent\n");
        check(missing.contains("game-perf.conf: не найден"), "absent file");

        String partial = MotorolaThermalState.parse("ENGINE=running\n");
        check(partial.contains("нет данных"), "truncated output must not seem valid");
        check(MotorolaThermalState.parse("").contains("нет данных"), "empty output");
        check(MotorolaThermalState.unavailable("Shizuku отсутствует")
            .contains("Shizuku отсутствует"), "denied access explanation");
        System.out.println("PASS: Motorola MAXE/thermal readback parser, absence, and safety");
    }
}
