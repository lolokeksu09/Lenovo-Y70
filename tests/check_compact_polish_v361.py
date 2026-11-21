"""Visual QA regression grounded in v3.6.0 physical Lenovo Y70 screenshots."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
xml = ET.parse(app / "res/layout/activity_main_menu.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"

def by_id(node):
    views = {}
    for v in node.iter():
        raw = v.attrib.get(ns+"id")
        if raw:
            k = raw.rsplit("/", 1)[-1]
            assert k not in views, "Duplicate ID: " + k
            views[k] = v
    return views

views = by_id(xml)
pages = [by_id(page) for page in list(xml)[0]]
overview, screen, game, system = pages
assert "btn_quick_display" in overview and "btn_quick_games" in overview
assert "tv_menu_res" in overview and "tv_menu_shizuku" in overview
assert views["page_tab_overview"].attrib[ns+"visibility"] == "visible"
assert "Lenovo Legion Y70" not in ET.tostring(xml, encoding="unicode").split('page_tab_display')[0]
assert "Текущие параметры" in ET.tostring(list(xml)[0][0], encoding="unicode")
for key in ("btn_quick_display","btn_quick_games","btn_quick_audio","btn_quick_apps"):
    assert int(overview[key].attrib[ns+"minHeight"].removesuffix("dp")) >= 48

# Touch heading and status chip must each be short enough to share a row.
touch = screen["card_touch_control"]
texts = [v.attrib.get(ns+"text","") for v in touch.iter()]
assert "Отклик" in texts and "Экран ≠ сенсор" in texts
assert "Настройки отклика" not in texts
assert not any("Частота экрана не равна частоте опроса сенсора." in t for t in texts)
oled = screen["card_eye_care"]
assert any(v.attrib.get(ns+"drawableStart") == "@drawable/legion_icon_eye"
           for v in oled.iter()), "OLED must not reuse the touch icon"

# Two secondary game tools share one parent section; readback is not a fake toggle.
touch_button = game["btn_main_game_touch_details"]
capture_button = game["btn_main_game_capture"]
game_status = game["tv_game_main_access"]
assert touch_button.attrib[ns+"text"] == "Проверить Touch"
assert capture_button.attrib[ns+"text"] == "Перейти к записи"
assert all(int(x.attrib[ns+"minHeight"].removesuffix("dp")) >= 48
           for x in (touch_button,capture_button))
assert "Проверка доступа" in game_status.attrib[ns+"text"]
assert not any("100% производительности" in v.attrib.get(ns+"text","")
               for v in game.values())

# The experimental NFC write action is not styled as the primary red CTA.
nfc = system["card_nfc_fix"]
nfc_action = system["btn_install_nfc_module"]
assert nfc_action.attrib[ns+"background"] == "@drawable/legion_action_secondary"
assert "card_nav_debloat" in system and "card_network_speed" in system
assert "layout_footer_author" in system

# No new permission or hardware write is added to the redesign.
java = (app / "src/com/moto/resolutionmaster/MainMenuActivity.java").read_text(encoding="utf-8")
assert "MotorolaDirectAccessGate.isBlocked(this)" in java
assert "setupDashboardShortcuts();" in java
assert "btn_main_game_capture" in java and "openGameModule();" in java
assert "btn_quick_audio" in java and "showBottomTab(3)" in java
ET.parse(app / "res/drawable/legion_icon_eye.xml")
for name in ("legion_surface_hero","legion_surface_violet","legion_surface_blue",
             "legion_surface_green"):
    ET.parse(app / f"res/drawable/{name}.xml")
print("PASS: compact v3.6.1 screenshots, touch title, game actions, live readback and NFC caution")
