"""Regression guards for the opt-in, one-shot Settings accessibility helper."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = root / "src/com/moto/resolutionmaster"
service = (src / "PackageEnableAccessibilityService.java").read_text(encoding="utf-8")
policy = (src / "EnableAssistPolicy.java").read_text(encoding="utf-8")
activity = (src / "DebloatActivity.java").read_text(encoding="utf-8")
manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
ns = "{http://schemas.android.com/apk/res/android}"
xml = ET.parse(root / "res/xml/enable_assist_service.xml").getroot()

assert xml.attrib[ns + "packageNames"] == "com.android.settings"
assert xml.attrib[ns + "canRetrieveWindowContent"] == "true"
assert "android.permission.BIND_ACCESSIBILITY_SERVICE" in manifest
assert 'android:name=".PackageEnableAccessibilityService"' in manifest
assert 'android:resource="@xml/enable_assist_service"' in manifest
assert 'android:name="android.accessibilityservice.AccessibilityService"' in manifest

# Explicit request, exact identity, short lifetime, one-shot, no blanket scans.
assert "public static boolean arm(Context context, String pkg)" in service
assert "PackageHelper.isProtectedInDebloater(context, pkg)" in service
assert "AccessibilityManager" in service
assert "getEnabledAccessibilityServiceList" in service
assert "static final long EXPIRY_MS = 45000" in service
assert '!"com.android.settings".contentEquals(event.getPackageName())' in service
assert '!"com.android.settings".contentEquals(root.getPackageName())' in service
assert "findExactVisibleLabel(root, expectedLabel, 0)" in service
assert "findUniqueEnableButton(root)" in service
assert "matches.size() == 1" in service
assert "cancel();\n        if (clickable.performAction" in service
assert "performGlobalAction(GLOBAL_ACTION_BACK)" in service
assert '"Включить".equals(t)' in service
assert '"Отключить"' not in policy
assert '"Удалить"' not in policy
assert 'return "Включить".equals(label) || "Enable".equalsIgnoreCase(label)' in policy

# The normal Activity displays the privacy consequences and offers manual choice.
assert "private void explainAndRequestAccessibilityGrant()" in activity
assert "PackageEnableAccessibilityService.isEnabled(this)" in activity
assert "PackageEnableAccessibilityService.arm(" in activity
assert "Settings.ACTION_ACCESSIBILITY_SETTINGS" in activity
assert "Settings.ACTION_APPLICATION_DETAILS_SETTINGS" in activity
assert "PackageEnableAccessibilityService.cancel()" in activity
assert "private void verifyReturnedSettingsPackage(final String pkg)" in activity
assert 'setNegativeButton("Завершить"' in activity
print("PASS: opt-in one-shot Settings-only click and user-visible privacy disclosure")
