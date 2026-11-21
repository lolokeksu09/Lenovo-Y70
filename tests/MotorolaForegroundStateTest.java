package com.moto.resolutionmaster;

public final class MotorolaForegroundStateTest {
    private static void eq(String expected, String got, String context) {
        if (!expected.equals(got)) {
            throw new AssertionError(context + ": " + got + " expected " + expected);
        }
    }
    public static void main(String[] args) {
        eq("wild-rift", MotorolaForegroundState.parse(
            "topResumedActivity=ActivityRecord{3fc u0 " +
            "com.riotgames.league.wildrift/.MainActivity t150}"),
            "Android topResumedActivity in game");
        eq("other", MotorolaForegroundState.parse(
            "mResumedActivity: ActivityRecord{abc u0 com.android.settings/.Settings t2}"),
            "different foreground despite game process running");
        eq("wild-rift", MotorolaForegroundState.parse(
            "ResumedActivity: ActivityRecord{abc u0 " +
            "com.riotgames.league.wildrift/.MainActivity t3}"),
            "legacy resumed format");
        eq("unknown", MotorolaForegroundState.parse(
            "Permission Denial: can't dump activity"), "denied");
        eq("unknown", MotorolaForegroundState.parse(""), "empty");
        eq("unknown", MotorolaForegroundState.parse(
            "topResumedActivity=null"), "unknown top");
        eq("unknown", MotorolaForegroundState.parse(
            "mResumedActivity: ActivityRecord{a u0 com.android.settings/.Settings t2}\n" +
            "mResumedActivity: ActivityRecord{b u0 " +
            "com.riotgames.league.wildrift/.MainActivity t3}"),
            "multiple foreground candidates");
        eq("unknown", MotorolaForegroundState.parse(
            "topResumedActivity=ActivityRecord{a u0 " +
            "com.riotgames.league.wildrift/.MainActivity t1}\n" +
            "topResumedActivity=ActivityRecord{b u0 com.android.settings/.Settings t2}"),
            "multiple top resumed candidates");
        eq("other", MotorolaForegroundState.parse(
            "mResumedActivity: ActivityRecord{a u0 " +
            "com.riotgames.league.wildrift/.MainActivity t1}\n" +
            "topResumedActivity=ActivityRecord{b u0 com.android.settings/.Settings t2}"),
            "topResumed overrides fallback");
        System.out.println("PASS: Wild Rift foreground classifier handles unknown and multi-window");
    }
}
