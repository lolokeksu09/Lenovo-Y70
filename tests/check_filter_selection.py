"""Regression guard for initial-load/filter-click race from Lenovo Y70 screenshot."""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = root / "src/com/moto/resolutionmaster"
activity = (src / "DebloatActivity.java").read_text(encoding="utf-8")
adapter = (src / "AppListAdapter.java").read_text(encoding="utf-8")
selection = (src / "DebloatFilterSelection.java").read_text(encoding="utf-8")
policy = (src / "DisabledFilterPolicy.java").read_text(encoding="utf-8")

# Clicking disabled before first async load must persist chosen filter.
assert "private final DebloatFilterSelection filterSelection = new DebloatFilterSelection()" in activity
filter_method = activity.split("private void selectFilter(int mode)", 1)[1].split(
    "private void resetFilterButtons()", 1)[0]
assert "filterSelection.select(mode)" in filter_method
assert "final int selected = filterSelection.getMode()" in filter_method
assert "active.setBackgroundResource(R.drawable.btn_primary)" in filter_method
assert "appListAdapter.setFilter(selected)" in filter_method
assert "selectFilter(3)" in activity
assert "selectFilter(0)" in activity

# The adapter gets selected mode even if it didn't exist at click time.
load = activity.split("private void loadInstalledApps()", 1)[1].split(
    "private void updateDisabledSummary()", 1)[0]
assert "if (request != appsLoadSerial || isFinishing()) return" in load
assert load.count("appListAdapter.setFilter(filterSelection.getMode())") == 2
assert load.index("appListAdapter.setFilter(filterSelection.getMode())") < load.index(
    "lvApps.setAdapter(appListAdapter)")
assert "appListAdapter.updateList(installedApps)" in load

# Disabled is exactly false, never enabled/unknown, and remains filtered after reload.
assert "currentFilter == 3 && !DisabledFilterPolicy.matches(a.enabledState)" in adapter
assert "Boolean.FALSE.equals(isEnabled)" in policy
assert "private int mode = 0" in selection
print("PASS: selected Disabled category persists through async creation and reload")
