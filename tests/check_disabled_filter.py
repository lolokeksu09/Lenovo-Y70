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
ui_state_reader = pkg.split("private static LocalState readLocalState", 1)[1].split("public static Boolean getPackageEnabledState", 1)[0]
assert "isPackageSuspended" not in ui_state_reader, "Suspended must not mean disabled in UI filter"
assert "COMPONENT_ENABLED_STATE_DISABLED_USER" in ui_state_reader
assert "Boolean enabledState = snapshot.resolve(" in pkg
assert "new AppInfo(pkg, label, icon, isSystem, enabledState," in pkg
assert "catch (Throwable t)" in pkg
assert "return new LocalState(ai.enabled ? Boolean.TRUE : null, false);" in pkg
assert "public Boolean enabledState;" in app
assert "public void setEnabledState(Boolean state)" in app
assert "Boolean.FALSE.equals(isEnabled)" in policy
assert "currentFilter == 3 && !DisabledFilterPolicy.matches(a.enabledState)" in adapter
assert "holder.btnToggle.setEnabled(app.enabledState != null && !isProtected)" in adapter
assert 'holder.tvStatus.setText("● Состояние неизвестно")' in adapter

# Switching or reloading must recompute the current filter from fresh state.
assert "public void refreshFilter()" in adapter
assert "app.setEnabledState(liveState)" in activity
assert "appListAdapter.refreshFilter()" in activity
assert "appListAdapter.updateList(installedApps)" in activity
assert "final Boolean liveState = PackageHelper.getVerifiedPackageEnabledState(" in activity
assert "loadInstalledApps();" in activity
print("PASS: disabled filter source invariants")

# Regression: Motorola UI says com.android.dialer is enabled, while
# app-side PackageManager can report a contradictory state.
resolver = (src / "PackageStateResolver.java").read_text(encoding="utf-8")
assert '"pm list packages -e --user 0"' in pkg
assert '"pm list packages -d --user 0"' in pkg
assert "PackageStateResolver.Snapshot snapshot = readShellPackageSnapshot(context)" in pkg
assert "snapshot.resolve(" in pkg
assert "isProtectedDialer(context, packageName)" in pkg
assert "tm.getDefaultDialerPackage()" in pkg
assert "PackageHelper.isProtectedInDebloater(context, app.packageName)" in adapter
assert "localExplicitDisabled && Boolean.FALSE.equals(local)" in resolver
assert "if (inEnabled) return Boolean.TRUE" in resolver
print("PASS: per-user shell/PM reconciled disabled status")
