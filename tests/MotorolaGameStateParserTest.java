package com.moto.resolutionmaster;

public final class MotorolaGameStateParserTest {
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }

    private static final String HEADER =
        "MotoExtendService\n" +
        "  GameModeStateController\n" +
        "    mAidlService=com.motorola.hardware.display.touch.IMotTouch$Stub$Proxy@e07b37a\n" +
        "    mReportRateSupported_aidl=true\n" +
        "    mSampleRateSupported_aidl=true\n" +
        "    mTapSensitivitySupported_aidl=true\n" +
        "    mSwipeResponsivenessSupported_aidl=true\n";

    private static String state(boolean touch, int dps, String requests) {
        return HEADER +
            "    isGameModeActive=true\n" +
            "    mGameModeStates:\n" +
            "    token=android.os.BinderProxy@123 bundle=Bundle[{touch_enhance_enable=" +
            (touch ? "1" : "0") +
            ", game_mode_op_package_name=com.motorola.gamemode, game_package_name=com.riotgames.league.wildrift}]\n" +
            "  MotoPowerHealthManagerService State:\n" +
            "    isDPSSupported=true\n" +
            "    mCurrentHardwareDpsStatus=" + dps + "\n" +
            "    mDpsRequests=" + requests + "\n";
    }

    public static void main(String[] args) {
        MotorolaGameStateParser.Snapshot on =
            MotorolaGameStateParser.parse(state(true, 1, "{App-Manual:10202=1}"));
        check(on.readable && on.error == null, "valid dumpsys accepted");
        check(Boolean.TRUE.equals(on.dpsSupported), "DPS capability");
        check(Integer.valueOf(1).equals(on.hardwareDpsStatus), "actual hardware DPS on");
        check("{App-Manual:10202=1}".equals(on.dpsRequests), "request not treated as hardware proof");
        check(Boolean.TRUE.equals(on.gameModeActive), "game mode active");
        check(Boolean.TRUE.equals(on.touchRequested), "GameTime touch request on");
        check("com.riotgames.league.wildrift".equals(on.gamePackage), "actual game package");
        check(on.touchServiceConnected, "touch service binder present");
        check(Boolean.TRUE.equals(on.sampleRateSupported), "AIDL sample rate capability");
        check(Boolean.TRUE.equals(on.reportRateSupported), "AIDL report rate capability");
        check(Boolean.TRUE.equals(on.tapSensitivitySupported), "AIDL tap capability");
        check(Boolean.TRUE.equals(on.swipeResponsivenessSupported), "AIDL swipe capability");

        MotorolaGameStateParser.Snapshot off =
            MotorolaGameStateParser.parse(state(false, 0, "{App-Manual:10202=1}"));
        check(Integer.valueOf(0).equals(off.hardwareDpsStatus), "hardware off regardless of request=1");
        check(Boolean.FALSE.equals(off.touchRequested), "touch off");

        MotorolaGameStateParser.Snapshot idle = MotorolaGameStateParser.parse(
            HEADER + "    isGameModeActive=false\n    mGameModeStates:\n" +
            "  MotoPowerHealthManagerService State:\n" +
            "    isDPSSupported=true\n    mCurrentHardwareDpsStatus=0\n    mDpsRequests={}\n");
        check(idle.touchRequested == null, "idle touch is unavailable, not off");
        check(idle.gamePackage == null, "idle no game");
        check(Integer.valueOf(0).equals(idle.hardwareDpsStatus), "idle power verified off");

        MotorolaGameStateParser.Snapshot foreign = MotorolaGameStateParser.parse(
            state(true, 0, "{}").replace("game_mode_op_package_name=com.motorola.gamemode",
                "game_mode_op_package_name=com.another.app"));
        check(foreign.touchRequested == null, "cannot attribute other app's touch state to GameTime");

        String ownRequest = state(true, 0, "{}") +
            "    token=android.os.BinderProxy@ABC bundle=Bundle[{" +
            "touch_enhance_enable=1, game_mode_op_package_name=com.moto.resolutionmaster, " +
            "game_package_name=com.riotgames.league.wildrift}]\\n";
        MotorolaGameStateParser.Snapshot ours = MotorolaGameStateParser.parse(ownRequest);
        check(Boolean.TRUE.equals(ours.ownTouchRequested),
            "Own request must be attributed to Lenovo Y70, not GameTime");
        check("com.riotgames.league.wildrift".equals(ours.ownGamePackage),
            "Own request must identify exact target game");
        check(Boolean.TRUE.equals(ours.touchRequested),
            "GameTime request must remain distinct from our request");

        String ownOff = state(true, 0, "{}") +
            "    token=android.os.BinderProxy@DEF bundle=Bundle[{" +
            "touch_enhance_enable=0, game_mode_op_package_name=com.moto.resolutionmaster, " +
            "game_package_name=com.riotgames.league.wildrift}]\\n";
        MotorolaGameStateParser.Snapshot offOurs = MotorolaGameStateParser.parse(ownOff);
        check(Boolean.FALSE.equals(offOurs.ownTouchRequested),
            "Own off request must not be confused with GameTime on");

        check(foreign.ownTouchRequested == null,
            "Unknown actor cannot impersonate our own touch request");

        MotorolaGameStateParser.Snapshot denied =
            MotorolaGameStateParser.parse("Permission Denial: can't dump moto_extend_service");
        check(!denied.readable && denied.touchRequested == null &&
            denied.hardwareDpsStatus == null, "permission denial never claimed as off");

        MotorolaGameStateParser.Snapshot malformed =
            MotorolaGameStateParser.parse("MotoExtendService\nGameModeStateController\n");
        check(!malformed.readable && malformed.hardwareDpsStatus == null,
            "truncated dump never claims verified state");

        MotorolaGameStateParser.Snapshot unusual =
            MotorolaGameStateParser.parse(state(true, -1, "{}").replace(
                "mSampleRateSupported_aidl=true", "mSampleRateSupported_aidl=false"));
        check(Integer.valueOf(-1).equals(unusual.hardwareDpsStatus),
            "unknown hardware status preserved");
        check(Boolean.FALSE.equals(unusual.sampleRateSupported), "capability explicitly false");

        System.out.println("PASS: Motorola state readback, request-vs-hardware separation, denial and no-game paths");
    }
}
