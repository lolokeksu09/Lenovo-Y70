"""v3.1.3 safety and UI data-flow regression checks for explicit bulk restore."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = root / "src/com/moto/resolutionmaster"
package = (src / "PackageHelper.java").read_text(encoding="utf-8")
activity = (src / "DebloatActivity.java").read_text(encoding="utf-8")
policy = (src / "BulkEnablePolicy.java").read_text(encoding="utf-8")
xml_file = root / "res/layout/activity_debloat.xml"
tree = ET.parse(xml_file)
android = "{http://schemas.android.com/apk/res/android}"
nodes = {v.attrib.get(android + "id"): v for v in tree.iter()}
for view_id in ("btn_enable_all_disabled", "btn_refresh_apps", "tv_disabled_summary",
                "pb_bulk_enable", "tv_bulk_enable_progress", "lv_apps"):
    assert "@+id/" + view_id in nodes, view_id

assert nodes["@+id/lv_apps"].attrib[android + "layout_height"] == "0dp"
assert nodes["@+id/lv_apps"].attrib[android + "layout_weight"] == "1"
assert nodes["@+id/btn_enable_all_disabled"].attrib[android + "text"] == "Включить все отключённые"

# The bulk action must be an explicit preview/confirm flow, not an onCreate mutation.
assert "private void previewEnableAllDisabled()" in activity
assert "private void performBulkEnable(final List<String> targets)" in activity
assert "BulkEnablePolicy.select(candidates)" in activity
assert "PackageHelper.getInstalledApps(DebloatActivity.this)" in activity
assert '.setTitle("Включить все отключённые?")' in activity
assert '.setNegativeButton("Отмена", null)' in activity
assert "PackageHelper.enableConfirmedDisabledPackage(" in activity
assert "setBulkBusy(true)" in activity
assert "pbBulkEnable.setProgress(completed)" in activity
assert "loadInstalledApps();" in activity
assert "appListAdapter.updateList(installedApps)" in activity
assert "appsLoadSerial" in activity
assert "pendingTogglePackages.isEmpty()" in activity

# Every selected package must be rechecked before mutation, and verified after.
method = package.split("public static boolean enableConfirmedDisabledPackage(", 1)[1].split(
    "public static List<AppInfo> getInstalledApps(", 1)[0]
assert "isProtectedInDebloater(context, packageName)" in method
assert "getVerifiedPackageEnabledState(context, packageName)" in method
assert "if (current == null)" in method
assert "if (current.booleanValue())" in method
assert '"pm enable --user 0 "' in method
assert "command.success && Boolean.TRUE.equals(after)" in method
assert "clearAfterRestore(context, packageName)" in method
assert "pm unsuspend" not in method
assert "cmd appops" not in method

# Pure Java selection excludes enabled/unknown/protected and deduplicates.
assert "Boolean.FALSE.equals(c.enabled)" in policy
assert "c.protectedPackage" in policy
assert "LinkedHashSet" in policy
print("PASS: bulk-enable confirmation, strict eligibility, per-package verification, progress and refresh")
