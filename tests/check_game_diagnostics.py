"""Read-only Motorola GameTime diagnostics wiring and honesty invariants."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
src = app / "src/com/moto/resolutionmaster"
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
parser = (src / "MotorolaGameStateParser.java").read_text(encoding="utf-8")
controller = (src / "MotoGameController.java").read_text(encoding="utf-8")
main = (src / "MainMenuActivity.java").read_text(encoding="utf-8")
layout = (app / "res/layout/activity_main_menu.xml").read_text(encoding="utf-8")
game_layout = (app / "res/layout/activity_motorola_game.xml").read_text(encoding="utf-8")
manifest = (app / "AndroidManifest.xml").read_text(encoding="utf-8")
ns = "{http://schemas.android.com/apk/res/android}"

# Read status with an existing user-authorized shell, not by spoofing GameTime.
assert '"dumpsys moto_extend_service"' in activity
assert "ShellHelper.runCommandWithResult(" in activity
assert "new Thread(new Runnable()" in activity
assert "request != requestSerial" in activity
assert "ShellHelper.isShizukuPermissionGranted()" in activity
assert "ShellHelper.requestShizukuPermission()" in activity
assert "runOnUiThread(" in activity
assert "getLaunchIntentForPackage(\"com.motorola.gamemode\")" in activity
assert "com.riotgames.league.wildrift" in activity

# The only writes are official Motorola APIs from our own UID; no
# signature bypass, binder transaction tricks or undocumented sysfs writes.
assert '"motorola.core_services.misc.MotoExtendManager"' in controller
assert '"setDirectPowerSupplyStatus", int.class' in controller
assert '"setGameModeState", boolean.class, Bundle.class' in controller
assert 'context.checkSelfPermission(DPS_PERMISSION)' in controller
assert 'PackageManager.PERMISSION_GRANTED' in controller
assert 'options.putInt("touch_enhance_enable", enable ? 1 : 0)' in controller
assert 'options.putString("game_package_name", gamePackage)' in controller
assert 'getInstance", Context.class' in controller
# The 3.2.1 failure path was a non-null SDK manager with a NULL Core.
# Require a genuine typed AIDL/Core fallback before any call.
assert 'klass.getDeclaredField("mMotoExtendManagerCore")' in controller
assert 'if (coreField.get(instance) != null) return instance;' in controller
assert "return coreFromRegisteredService(context);" in controller
assert '"android.os.ServiceManager"' in controller
assert '"getService", String.class' in controller
assert 'getService.invoke(null, SERVICE_NAME)' in controller
assert '"com.motorola.misc.IMotoExtendService"' in controller
assert '"com.motorola.misc.MotoExtendManagerCore"' in controller
assert 'stubClass.getMethod("asInterface", IBinder.class)' in controller
assert 'coreClass.getConstructor(' in controller
assert 'interfaceClass, Context.class' in controller
assert 'context.getApplicationContext()' in controller
assert 'private static volatile Object coreInstance;' in controller
assert 'synchronized (CORE_LOCK)' in controller
assert 'coreClass.getMethod(' not in controller
assert 'manager.getClass().getMethod("isTouchEnhanceSupported")' in controller
assert 'manager.getClass().getMethod("setGameModeState", boolean.class, Bundle.class)' in controller
assert 'context.checkSelfPermission(DPS_PERMISSION)' in controller
assert 'MotoGameController.requestBypass(' in activity
assert 'MotoGameController.requestTouch(' in activity
assert '"dumpsys moto_extend_service"' in activity
assert 'final boolean confirmed = call.sent &&' in activity
assert 'private static boolean verified(' in activity
assert 'Integer.valueOf(1) : Integer.valueOf(0)' in activity
assert 'state.ownTouchRequested' in activity
assert 'gamePackage.equals(state.ownGamePackage)' in activity
assert 'hasDpsPermission()' in activity
assert 'PowerHelper.getBatteryInfo(this).isPlugged' in activity
assert 'new Thread(new Runnable()' in activity
assert 'request != requestSerial' in activity

for raw in ("service call ", "charging_enabled", "/sys/", "su -c",
            "pm grant", "setprop ", "settings put "):
    assert raw not in controller, raw

# A request is never presented as proof of driver/hardware performance.
assert 's.hardwareDpsStatus.intValue() == 1' in activity
assert "Touch Boost: запрос ВКЛ" in activity
assert "Touch Boost: нет активного запроса GameTime" in activity
assert 'Boolean.TRUE.equals(s.dpsSupported)' in activity
assert "Поддержка API не означает измеренную частоту" in activity
assert '"com.motorola.gamemode".equals(actor)' in parser
assert '"com.moto.resolutionmaster".equals(actor)' in parser
assert "mCurrentHardwareDpsStatus" in parser
assert "mDpsRequests" in parser
assert "Permission Denial" in parser
assert "return unavailable(" in parser

assert 'android:id="@+id/btn_open_motorola_game"' in layout
assert "MotorolaGameActivity.class" in main
assert 'R.id.btn_open_motorola_game' in main
assert "btnOpenMotorolaGame.setEnabled(false)" not in main
assert '"3.3.1-game"' in manifest
assert '"LenovoMotorolovichY70 v3.3.1-game"' in layout
xml = ET.fromstring(manifest)
screen = [a for a in xml.findall(".//activity")
          if a.attrib.get(ns + "name") == ".MotorolaGameActivity"]
assert len(screen) == 1 and screen[0].attrib[ns + "exported"] == "false"
ET.fromstring(game_layout)
for id in ("tv_game_access", "tv_game_dps", "tv_game_dps_request",
           "tv_game_touch", "tv_game_mode", "tv_game_support",
           "tv_game_observed", "btn_game_refresh", "btn_game_gametime", "btn_game_shizuku"):
    assert '@+id/' + id in game_layout
for id in ("btn_game_dps_set", "btn_game_touch_set", "tv_game_control_feedback",
           "btn_game_copy_report"):
    assert '@+id/' + id in game_layout
assert '<uses-library android:name="moto-core_services" android:required="false"' in manifest
assert 'com.motorola.permission.SET_DIRECT_POWER_SUPPLY_STATUS' in manifest

# Regression: previously, denied permission/failed readback disabled clickable
# controls completely, and status refresh overwrote the last action error.
assert "btnDpsSet.setEnabled(true);" in activity
assert "btnTouchSet.setEnabled(true);" in activity
assert "btnDpsSet.setEnabled(hardwareKnown && hasDpsPermission())" not in activity
assert "btnTouchSet.setEnabled(s.readable && s.touchServiceConnected)" not in activity
update_controls = activity.split("private void updateControls(", 1)[1].split(
    "private void notifyAction(", 1)[0]
assert "tvControlFeedback.setText(" not in update_controls
assert "private void notifyAction(String message)" in activity
assert "Toast.makeText(this, message, Toast.LENGTH_LONG).show();" in activity
assert 'notifyAction("Bypass заблокирован Android:' in activity
assert 'notifyAction("Touch Boost: нет данных' in activity
assert "notifyAction(feedback);" in activity
assert "private void copyDiagnosticReport()" in activity
assert "ShellHelper.copyToClipboard(" in activity
assert '"Motorola diagnostics");' in activity
assert '"logcat -d -b crash -v threadtime -t 600"' in activity
assert "btnCopyReport.setOnClickListener(" in activity

print("PASS: direct Motorola calls gated, verified status and no raw privileged workarounds")
