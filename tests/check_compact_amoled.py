"""Compact AMOLED regression: primary routes visible, advanced controls preserved."""
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1]
res = root / "overrides/ResolutionMasterApp/res"
src = root / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
ns = "{http://schemas.android.com/apk/res/android}"
main = ET.parse(res / "layout/activity_main_menu.xml").getroot()
game = ET.parse(res / "layout/activity_motorola_game.xml").getroot()

def elements(root):
    views = {}
    for el in root.iter():
        key = el.attrib.get(ns + "id")
        if not key:
            continue
        name = key.rsplit("/", 1)[-1]
        assert name not in views, f"duplicate ID: {name}"
        views[name] = el
    return views

m, g = elements(main), elements(game)
assert "layout_menu_advanced" in m and "btn_menu_advanced" in m
assert m["layout_menu_advanced"].attrib[ns+"visibility"] == "gone"
primary = ["btn_open_motorola_game", "btn_open_display", "btn_open_debloat"]
for name in primary:
    assert name in m
    assert m[name].attrib[ns+"minHeight"].endswith("dp")
    assert int(m[name].attrib[ns+"minHeight"][:-2]) >= 48
assert "layout_footer_author" in m, "credits cannot be dropped"
assert len(m) >= 65, "unexpected removal of established controls"

# All optional tools remain attached to the advanced container.
advanced = {el.attrib[ns+"id"].rsplit("/",1)[-1]
            for el in m["layout_menu_advanced"].iter()
            if ns+"id" in el.attrib}
for name in ["card_touch_control", "card_eye_care",
             "card_audio_control", "card_power_control",
             "card_network_speed", "card_nfc_fix",
             "btn_menu_touch_stock", "btn_toggle_power_saver",
             "btn_install_nfc_module"]:
    assert name in advanced, name
for name in ["card_nav_display", "card_nav_debloat", "card_featured_game"]:
    assert name not in advanced, name

menu_activity = (src / "MainMenuActivity.java").read_text(encoding="utf-8")
assert "menuAdvancedExpanded = !menuAdvancedExpanded;" in menu_activity
assert 'state.putBoolean("menu.advanced_expanded"' in menu_activity
assert 'savedInstanceState.getBoolean("menu.advanced_expanded"' in menu_activity
assert "layoutMenuAdvanced.setVisibility(" in menu_activity
assert "btnMenuAdvanced.setContentDescription(" in menu_activity
assert 'btnOpenMotorolaGame.setText("Игровые функции");' in menu_activity

for name in ["btn_game_capture_start", "btn_game_capture_stop",
             "btn_game_capture_copy", "btn_game_refresh",
             "btn_game_shizuku", "btn_game_touch_set", "btn_game_dps_set",
             "btn_game_details", "btn_game_copy_report"]:
    assert name in g
    assert int(g[name].attrib[ns+"minHeight"][:-2]) >= 48, name
assert game.attrib[ns+"background"] == "@color/legion_black"
assert "layout_game_technical" in g
assert g["layout_game_technical"].attrib[ns+"visibility"] == "gone"
print("PASS: compact dashboard, preserved routes, advanced toggle and accessible tap targets")
