# RAM Wiper development rules

This branch is an independent Android project. Never merge its replacement tree into Lenovo-Y70 main. Preserve applicationId com.ramwiper.next.
Prioritize functional improvements, consolidate duplicate paths through MemoryController, then improve UI. Keep Shizuku limited to explicit user-selected third-party targets; do not add unrestricted shell execution or automatic force-stop. Preserve cancellation and guaranteed native release, enforce bounded allocations, report measured results rather than promised acceleration.
Before claiming completion, run focused policy tests and Android assembleDebug in CI. Device and Shizuku behavior require device verification.

## Engineering completion standard
Quality and depth take precedence over speed. Audit the affected architecture and dependencies before a large patch; address root causes; consolidate rather than duplicate. Implement working behavior before UI, verify actual states and failure paths, and review the exact revision iteratively. Do not equate changed file counts, a commit, or CI with device verification. Report untested behavior and release blockers. Avoid artificial delays and premature completion.
