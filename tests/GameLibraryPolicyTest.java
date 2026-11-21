package com.moto.resolutionmaster;

public final class GameLibraryPolicyTest {
    private static void check(boolean yes, String message) {
        if (!yes) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(GameLibraryPolicy.include("com.example.arcade", 0, 0),
            "CATEGORY_GAME recognized");
        check(!GameLibraryPolicy.include("com.android.settings", -1, 0),
            "unclassified utility excluded");
        check(GameLibraryPolicy.include(MotorolaCaptureFormat.GAME_PACKAGE, -1, 0),
            "Wild Rift fallback survives missing CATEGORY_GAME");
        check(!GameLibraryPolicy.include(null, 0, 0),
            "invalid package excluded");
        check(!GameLibraryPolicy.include("", 0, 0),
            "empty package excluded");
        System.out.println("PASS: only real game-classified installed packages allowed");
    }
}
