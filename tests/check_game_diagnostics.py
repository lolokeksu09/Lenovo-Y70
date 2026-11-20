"""Read-only Motorola GameTime diagnostics wiring and honesty invariants."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
src = app / "src/com/moto/resolutionmaster"
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
parser = (src / "MotorolaGameStateParser.java").read_text(encoding="utf-8")
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

# Exclude all unintended writes to hardware, charge sysfs and touch driver.
assert "setDirectPowerSupplyStatus" not in activity + parser
assert "setGameModeState" not in activity + parser
assert "setTouch" not in activity + parser
assert "settings put " not in activity + parser
assert "charging_enabled" not in activity + parser
assert "service call " not in activity + parser
assert "am startservice" not in activity + parser
assert "SET_DIRECT_POWER_SUPPLY_STATUS" not in manifest

# A request is never presented as proof of driver/hardware performance.
assert 's.hardwareDpsStatus.intValue() == 1' in activity
assert "Touch Boost: запрос ВКЛ" in activity
assert "Touch Boost: нет активного запроса GameTime" in activity
assert 'Boolean.TRUE.equals(s.dpsSupported)' in activity
assert "Поддержка API не означает измеренную частоту" in activity
assert 'game_mode_op_package_name=com.motorola.gamemode' in parser
assert "mCurrentHardwareDpsStatus" in parser
assert "mDpsRequests" in parser
assert "Permission Denial" in parser
assert "return unavailable(" in parser

assert 'android:id="@+id/btn_open_motorola_game"' in layout
assert "MotorolaGameActivity.class" in main
assert 'R.id.btn_open_motorola_game' in main
assert "btnOpenMotorolaGame.setEnabled(false)" not in main
assert '"3.1.9-game"' in manifest
assert '"LenovoMotorolovichY70 v3.1.9-game"' in layout
xml = ET.fromstring(manifest)
screen = [a for a in xml.findall(".//activity")
          if a.attrib.get(ns + "name") == ".MotorolaGameActivity"]
assert len(screen) == 1 and screen[0].attrib[ns + "exported"] == "false"
ET.fromstring(game_layout)
for id in ("tv_game_access", "tv_game_dps", "tv_game_dps_request",
           "tv_game_touch", "tv_game_mode", "tv_game_support",
           "tv_game_observed", "btn_game_refresh", "btn_game_gametime", "btn_game_shizuku"):
    assert '@+id/' + id in game_layout
print("PASS: Motorola diagnostics readback-only, distinct requests/status and navigable UI")
