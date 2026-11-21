"""Regression: remove mouse/trackpad pointer-speed control, retain display and OEM touch."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
java_src = app / "src/com/moto/resolutionmaster"
menu = (java_src / "MainMenuActivity.java").read_text(encoding="utf-8")
helper = (java_src / "TouchSpeedHelper.java").read_text(encoding="utf-8")
game = (java_src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
layout_src = (app / "res/layout/activity_main_menu.xml").read_text(encoding="utf-8")
layout = ET.fromstring(layout_src)
ns = "{http://schemas.android.com/apk/res/android}"

removed = [
    "tv_menu_touch_status", "tv_menu_touch_speed_val",
    "sb_menu_touch_speed", "btn_menu_touch_stock",
    "btn_menu_touch_game", "btn_menu_touch_turbo",
]
for identifier in removed:
    assert "@+id/" + identifier not in layout_src, identifier
    assert "R.id." + identifier not in menu, identifier

for snippet in (
    'pointer_speed', "getPointerSpeed", "setPointerSpeed",
    "applyTouchSpeed", "updateTouchSpeedDisplay",
    "speedToProgress", "progressToSpeed",
    "Скорость указателя", "мышь/трекпад",
):
    assert snippet not in menu + helper + layout_src, snippet

assert "private void setupTouchControls()" in menu
assert "btnMenuApplyTurbo.setOnClickListener(" in menu
assert "btnMenuResetStock.setOnClickListener(" in menu
assert "TouchSpeedHelper.enableFullTurboMode(" in menu
assert "TouchSpeedHelper.resetToStock(" in menu
assert "applyAnimScale(0.5f)" in menu
assert "applyAnimScale(1.0f)" in menu
assert "getAnimationScale" in helper
assert "setAnimationScales" in helper
assert "DisplayManagerHelper.HZ_144" in helper
assert "DisplayManagerHelper.HZ_AUTO" in helper

# Reset must not silently alter the user's pre-existing pointer speed.
reset = helper.split("public static boolean resetToStock(Context context)", 1)[1]
assert "setPointerSpeed" not in reset and "pointer_speed" not in reset
assert "return hz && animations;" in reset
assert "return hz && pointer" not in reset

def names(root):
    return {v.attrib[ns + "id"].split("/")[-1] for v in root.iter()
            if ns + "id" in v.attrib}
ids = names(layout)
for control in (
    "btn_menu_apply_turbo", "btn_menu_reset_stock",
    "btn_menu_anim_boost", "btn_menu_anim_stock",
    "tv_menu_anim_speed_val", "btn_open_display",
    "btn_open_motorola_game", "btn_main_game_touch_details",
):
    assert control in ids, control
assert 'android:id="@+id/page_tab_display"' in layout_src
assert 'android:id="@+id/bottom_navigation"' in layout_src
assert "requestTouch" in game or "btnTouchSet" in game
print("PASS: mouse pointer tuning removed; refresh, animations and Motorola Touch Boost remain")
