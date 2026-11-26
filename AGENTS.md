# RAM Wiper development rules

This branch is an independent Android project. Never merge its replacement tree into Lenovo-Y70 main. Preserve applicationId com.ramwiper.next.
Prioritize functional improvements, consolidate duplicate paths through MemoryController, then improve UI. Keep Shizuku limited to explicit user-selected third-party targets; do not add unrestricted shell execution or automatic force-stop. Preserve cancellation and guaranteed native release, enforce bounded allocations, report measured results rather than promised acceleration.
Before claiming completion, run focused policy tests and Android assembleDebug in CI. Device and Shizuku behavior require device verification.
