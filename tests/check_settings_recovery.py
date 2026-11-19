"""Source-level safety invariants for the Settings-based no-root recovery."""
from pathlib import Path
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = root / "src/com/moto/resolutionmaster"
pkg = (src / "PackageHelper.java").read_text(encoding="utf-8")
activity = (src / "DebloatActivity.java").read_text(encoding="utf-8")
policy = (src / "RestoreRoute.java").read_text(encoding="utf-8")
method = pkg.split("public static boolean enableConfirmedDisabledPackage(", 1)[1].split(
    "public static List<AppInfo> getInstalledApps(", 1)[0]

# Verified disabled + enabled=2 or enabled=4 must NOT go through pm enable.
assert "RestoreRoute.choose(current" in method
assert "readApplicationEnabledSetting(context, packageName)" in method
assert "if (route == RestoreRoute.Mode.SETTINGS)" in method
assert 'error(packageName, "Ограничение Android: требуется включить через системные настройки")' in method
assert "if (route != RestoreRoute.Mode.SHELL)" in method
assert method.index("route != RestoreRoute.Mode.SHELL") < method.index('"pm enable --user 0 "')
assert 'enabledSetting == 2 || enabledSetting == 4' in policy
assert 'enabledSetting == 3' in policy
assert 'if (!Boolean.FALSE.equals(verifiedEnabled)) return Mode.UNKNOWN' in policy

# Guided UI opens a specific installed package, does not mutate another user/profile.
assert "Settings.ACTION_APPLICATION_DETAILS_SETTINGS" in activity
assert 'Uri.parse("package:" + pkg)' in activity
assert "private void beginSettingsRecovery(List<String> blocked)" in activity
assert "settingsRestoreQueue.contains(pkg)" in activity
assert "PackageHelper.isProtectedInDebloater(this, pkg)" in activity
assert "private void verifyReturnedSettingsPackage(final String pkg)" in activity
assert "PackageHelper.getVerifiedPackageEnabledState(" in activity
assert "if (Boolean.TRUE.equals(confirmed))" in activity
assert "settingsRestoreIndex++" in activity
assert '.setNegativeButton("Через настройки"' in activity
assert '"Открыть системную страницу вручную"' in activity
assert "openPackageDetailsForRestore(pkg)" in activity
assert '.setNegativeButton("Завершить"' in activity
assert "pendingSettingsPackage != null && settingsPageLeft" in activity
assert "settingsRestoreQueue.clear()" in activity
ET.parse(root / "AndroidManifest.xml")
print("PASS: Android Settings recovery, per-app readback and safe no-root routing")
