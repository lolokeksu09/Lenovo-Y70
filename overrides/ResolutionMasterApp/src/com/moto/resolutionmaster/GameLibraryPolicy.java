package com.moto.resolutionmaster;

/** Minimal game recognition without claiming OEM GameTime optimization. */
public final class GameLibraryPolicy {
    private GameLibraryPolicy() {}

    public static boolean include(String packageName, int appCategory, int gameCategory) {
        if (packageName == null || packageName.length() == 0) return false;
        return appCategory == gameCategory ||
            MotorolaCaptureFormat.GAME_PACKAGE.equals(packageName);
    }
}
