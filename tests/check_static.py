from pathlib import Path
from xml.etree import ElementTree as ET
r = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = r / "src/com/moto/resolutionmaster"
power = (src/"PowerHelper.java").read_text(encoding="utf-8")
touch = (src/"TouchSpeedHelper.java").read_text(encoding="utf-8")
menu = (src/"MainMenuActivity.java").read_text(encoding="utf-8")
assert "dumpsys battery unplug" not in power
assert "echo 0 > /sys/class/power_supply/battery/charging_enabled" not in power
assert "btnToggleBypassCharging.setEnabled(false)" in menu
assert '"pointer_speed"' in touch and "Settings.System.getInt" in touch
assert "return hz && animations;" in touch
assert "setPointerSpeed(context, 7)" not in touch
assert "100% терминалов РФ" not in menu
for p in [r/"AndroidManifest.xml",r/"res/values/strings.xml",r/"res/layout/activity_main.xml",r/"res/layout/activity_main_menu.xml"]:
    ET.parse(p)
    assert "Сенсор 1500 Гц" not in p.read_text(encoding="utf-8")
print("PASS: static assertions and XML parsing")
