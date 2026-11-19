package com.moto.resolutionmaster;

/**
 * Single source of truth for Debloater's category selection.
 * It exists before the adapter and survives asynchronous list reloads.
 */
public final class DebloatFilterSelection {
    private int mode = 0; // 0=All, 1=System, 2=User, 3=Disabled

    public void select(int selectedMode) {
        if (selectedMode >= 0 && selectedMode <= 3) mode = selectedMode;
    }

    public int getMode() {
        return mode;
    }
}
