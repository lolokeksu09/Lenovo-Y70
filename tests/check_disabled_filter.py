"""Source regression checks for the Android Disabled app filter."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
pkg = (src / "PackageHelper.java").read_text(encoding="utf-8")
app = (src / "AppInfo.java").read_text(encoding="utf-8")
adapter = (src / "AppListAdapter.java").read_text(encoding="utf-8")
activity = (src / "DebloatActivity.java").read_text(encoding="utf-8")
policy = (src / "DisabledFilterPolicy.java").read_text(encoding="utf-8")

# Unknown must stay distinct from disabled across the entire data flow.
assert "public static Boolean getPackageEnabledState" in pkg
assert "Boolean enabledState = getPackageEnabledState(context, pkg)" in pkg
assert "new AppInfo(pkg, label, icon, isSystem, enabledState," in pkg
assert "catch (Throwable t)" in pkg
assert "return Boolean.valueOf(enabled)" in pkg
assert "public Boolean enabledState;" in app
assert "public void setEnabledState(Boolean state)" in app
assert "Boolean.FALSE.equals(isEnabled)" in policy
assert "currentFilter == 3 && !DisabledFilterPolicy.matches(a.enabledState)" in adapter
assert "holder.btnToggle.setEnabled(app.enabledState != null)" in adapter
assert 'holder.tvStatus.setText("● Состояние неизвестно")' in adapter

# Switching or reloading must recompute the current filter from fresh state.
assert "public void refreshFilter()" in adapter
assert "app.setEnabledState(liveState)" in activity
assert "appListAdapter.refreshFilter()" in activity
assert "appListAdapter.updateList(installedApps)" in activity
assert "final Boolean liveState = PackageHelper.getPackageEnabledState(" in activity
assert "loadInstalledApps();" in activity
print("PASS: disabled filter source invariants")
