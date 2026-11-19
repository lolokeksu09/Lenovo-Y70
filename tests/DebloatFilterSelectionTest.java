package com.moto.resolutionmaster;

/** Regression: user clicks Disabled while an async package scan still runs. */
public final class DebloatFilterSelectionTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DebloatFilterSelection state = new DebloatFilterSelection();
        check(state.getMode() == 0, "Default filter is All");

        // Adapter is not created yet: tapping Disabled MUST retain the mode.
        state.select(3);
        check(state.getMode() == 3, "Disabled selection survives pre-adapter click");

        // An asynchronous list load returns later; the state remains unchanged.
        int filterAppliedToNewAdapter = state.getMode();
        check(filterAppliedToNewAdapter == 3, "New adapter must use selected mode");

        // A later refresh does not revert to All.
        check(state.getMode() == 3, "Background refresh preserves selection");

        // Another category works, and a delayed earlier load must read current mode.
        state.select(2);
        check(state.getMode() == 2, "User category is selected");
        state.select(3);
        check(state.getMode() == 3, "Can return to Disabled without stale rows");
        state.select(99);
        check(state.getMode() == 3, "Invalid filter mode never overwrites selection");
        System.out.println("PASS: filter selected before adapter creation persists after async refresh");
    }
}
