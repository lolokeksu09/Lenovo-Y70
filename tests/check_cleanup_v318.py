"""v3.1.8: no phantom Accessibility auto-enable or fake AppOps-freeze UI."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
src = app / "src/com/moto/resolutionmaster"
activity = (src / "DebloatActivity.java").read_text(encoding="utf-8")
adapter = (src / "AppListAdapter.java").read_text(encoding="utf-8")
manifest = (app / "AndroidManifest.xml").read_text(encoding="utf-8")
workflow = (root / ".github/workflows/build-audit.yml").read_text(encoding="utf-8")
strings = (app / "res/values/strings.xml").read_text(encoding="utf-8")
readme = (root / "README.md").read_text(encoding="utf-8")

# Delete both the user-facing option and its broad-permission implementation.
for removed_file in (
    "PackageEnableAccessibilityService.java", "EnableAssistPolicy.java",
):
    assert not (src / removed_file).exists(), removed_file
assert not (app / "res/xml/enable_assist_service.xml").exists()
for removed_test in ("tests/check_accessibility_enable.py", "tests/EnableAssistPolicyTest.java"):
    assert not (root / removed_test).exists(), removed_test
for text in ("PackageEnableAccessibilityService", "EnableAssistPolicy",
             "BIND_ACCESSIBILITY_SERVICE", "@xml/enable_assist_service"):
    assert text not in manifest + activity + workflow, text
assert "enable_assist_description" not in strings
assert "waitingAccessibilityGrant" not in activity
assert "explainAndRequestAccessibilityGrant" not in activity
assert "Включить автоматически (специальный доступ)" not in activity
assert "Настроить автовключение (один раз)" not in activity
assert "Включить автоматически" not in readme
ET.parse(app / "AndroidManifest.xml")
ET.parse(app / "res/layout/activity_debloat.xml")

# Retain established, working paths and particularly the fixed Disabled filter.
assert "private void showNextSettingsPackage()" in activity
assert "private void openPackageDetailsForRestore(String pkg)" in activity
assert "Settings.ACTION_APPLICATION_DETAILS_SETTINGS" in activity
assert "private void verifyReturnedSettingsPackage(final String pkg)" in activity
assert "private final DebloatFilterSelection filterSelection" in activity
assert "appListAdapter.setFilter(filterSelection.getMode())" in activity
assert "currentFilter == 3 && !DisabledFilterPolicy.matches(a.enabledState)" in adapter
assert "previewEnableAllDisabled()" in activity

# No function should claim an AppOps freeze which PackageHelper explicitly refuses.
assert "Заморозить" not in adapter + activity
assert "Заморожено (AppOps)" not in adapter + activity
assert "isProtectedInDebloater(context, app.packageName)" in adapter
assert "btnToggle.setEnabled(installed && enabledState != null && !isProtected)" in activity
assert "btnToggle.setEnabled(app.enabledState != null && !isProtected)" in adapter
assert "Boolean enabledState = installed" in activity
assert "if (installed && Boolean.FALSE.equals(enabledState))" in activity

# Prevent bulk success count from treating missing/unknown preset packages as disabled.
assert "if (!enabled) {\n                disabledCount++;" not in activity
assert "Отключено: " in activity
assert 'android:versionName="3.3.0-game"' in manifest
print("PASS: removed unverified assistant and fake freeze, preserved Disabled filter and manual restore")
