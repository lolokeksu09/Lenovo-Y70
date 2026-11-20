"""Regression guards for the v3.1 safety release.

These are source-level invariants. CI also compiles and signs the real APK;
runtime behavior on the user's phone remains a separate verification gate.
"""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = root / "src/com/moto/resolutionmaster"
read = lambda name: (src / name).read_text(encoding="utf-8")
shell = read("ShellHelper.java")
pkg = read("PackageHelper.java")
debloat = read("DebloatActivity.java")
nfc = read("NfcFixHelper.java")
menu = read("MainMenuActivity.java")
manifest = (root / "AndroidManifest.xml").read_text(encoding="utf-8")
layout = (root / "res/layout/activity_main_menu.xml").read_text(encoding="utf-8")

# Shell execution must report the actual last-command exit code.
assert "class CommandResult" in shell
assert "runCommandWithResult" in shell
assert "__RM_EXIT_CODE__=" in shell
assert "hasWriteSecureSettings(context)" in shell

# Bulk and manual debloat must only undo changes the app recorded.
assert "getModifiedPackages" in pkg
assert "recordBeforeDisable" in pkg
assert "isModifiedByApp" in pkg
assert "pm disable-user --user 0" in pkg
assert "readEnabledState(context, packageName)" in pkg
assert "isProtectedOrNonDisable(packageName)" in pkg
assert "if (ri == null || ri.activityInfo == null) return true" in pkg
assert '"com.android.systemui".equals(packageName)' in pkg
assert "Не удалось сохранить журнал отката; действие отменено" in pkg
assert "toEnable.add(pkg)" in debloat
assert "PackageHelper.isModifiedByApp" in debloat
assert "PackageHelper.getModifiedPackages" in debloat
assert "Все приложения устройства" not in debloat
assert "Система находится в заводском состоянии" not in debloat
assert "pendingTogglePackages.add(app.packageName)" in debloat
assert "PackageHelper.getLastOperationError(pkg)" in debloat
assert 'setTitle("Частичное выполнение пресета")' in debloat
assert "Разрешения" not in debloat or "Shizuku" in debloat

# NFC must not silently retry aggressive low-level writes or delete other modules.
assert "extractDexHelper(context)" in nfc
assert "if (!extractDexHelper(context))" in nfc
assert "getLiveRoutingInfo(context)" in nfc
assert "force-apply" not in nfc
assert "force-undo" not in nfc
assert 'rm -rf " + MODULE_PATH' not in nfc
assert "return output != null && output.contains(\"result=ok\")" not in nfc
assert "if (ok && updatedInfo.status == NfcFixHelper.NfcStatus.FIXED_100_PERCENT)" in menu
assert "if (ok && updatedInfo.status == NfcFixHelper.NfcStatus.FACTORY_STOCK)" in menu

# Version must be consistent across manifest, menu and downloadable APK.
assert 'android:versionCode="41"' in manifest
assert 'android:versionName="3.1.9-game"' in manifest
assert "LenovoMotorolovichY70 v3.1.9-game" in layout
print("PASS: v3.1 safety invariants")
