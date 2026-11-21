from pathlib import Path
from xml.etree import ElementTree as ET
r = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = r / "src/com/moto/resolutionmaster"
power = (src/"PowerHelper.java").read_text(encoding="utf-8")
touch = (src/"TouchSpeedHelper.java").read_text(encoding="utf-8")
menu = (src/"MainMenuActivity.java").read_text(encoding="utf-8")
assert "dumpsys battery unplug" not in power
assert "echo 0 > /sys/class/power_supply/battery/charging_enabled" not in power
assert "btnOpenMotorolaGame.setEnabled(true)" in menu
assert "startActivity(new Intent(MainMenuActivity.this, MotorolaGameActivity.class))" in menu
assert '"pointer_speed"' in touch and "Settings.System.getInt" in touch
assert "return hz && animations;" in touch
assert "setPointerSpeed(context, 7)" not in touch
assert "100% терминалов РФ" not in menu
for p in [r/"AndroidManifest.xml",r/"res/values/strings.xml",r/"res/layout/activity_main.xml",r/"res/layout/activity_main_menu.xml"]:
    ET.parse(p)
    assert "Сенсор 1500 Гц" not in p.read_text(encoding="utf-8")
# The NFC header must remain compact: no paragraph-length badge in a weighted row.
nfc = (src/"NfcFixHelper.java").read_text(encoding="utf-8")
layout = (r/"res/layout/activity_main_menu.xml").read_text(encoding="utf-8")
manifest = ET.parse(r/"AndroidManifest.xml").getroot()
android_ns = "{http://schemas.android.com/apk/res/android}"
assert manifest.attrib[android_ns + "versionCode"] == "47"
assert manifest.attrib[android_ns + "versionName"] == "3.3.1-game"
assert 'android:text="Экспериментально"' in layout
assert 'android:text="LenovoMotorolovichY70 v3.3.1-game"' in layout
assert "100% терминалов" not in nfc and "100% терминалов" not in layout
assert "100% Russian POS" not in nfc
assert "Маршрутизация DH обнаружена (оплата не проверена)" in nfc
print("PASS: source assertions, XML, NFC layout and truthful status")
