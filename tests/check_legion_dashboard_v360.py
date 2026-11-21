"""Regression: premium four-page UI must keep real live state and working controls."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
layout = ET.parse(app / "res/layout/activity_main_menu.xml").getroot()
activity = (app / "src/com/moto/resolutionmaster/MainMenuActivity.java").read_text(encoding="utf-8")
ns = "{http://schemas.android.com/apk/res/android}"

def index(element):
    ids = {}
    for view in element.iter():
        identifier = view.attrib.get(ns + "id")
        if identifier:
            key = identifier.rsplit("/", 1)[-1]
            assert key not in ids, f"duplicate view id {key}"
            ids[key] = view
    return ids

all_views = index(layout)
frame, nav = list(layout)
assert frame.tag == "FrameLayout"
assert nav.attrib[ns + "id"] == "@+id/bottom_navigation"
assert nav.attrib[ns + "minHeight"] == "64dp"
pages = [index(view) for view in frame]
assert len(pages) == 4

# Preserve actual dynamic sources; never render mock values as true measurements.
for key in ("tv_app_title", "tv_menu_res", "tv_menu_dpi", "tv_menu_fps",
            "tv_menu_shizuku", "tv_menu_secure", "tv_menu_touch_status"):
    assert key in pages[0], key
assert "100%" not in ET.tostring(frame[0], encoding="unicode")
assert "Общий статус: Готов" not in ET.tostring(frame[0], encoding="unicode")
for key in ("btn_quick_display", "btn_quick_games", "btn_quick_audio",
            "btn_quick_apps"):
    assert key in pages[0], key
    assert int(pages[0][key].attrib[ns + "minHeight"].removesuffix("dp")) >= 48

# The 4 existing functional sections survive the reskin.
for key in ("card_nav_display", "card_touch_control", "card_eye_care"):
    assert key in pages[1], key
for key in ("card_featured_game", "btn_open_motorola_game", "card_power_control",
            "btn_game_launch_wildrift", "tv_game_main_access",
            "btn_main_game_touch_details", "btn_main_game_capture"):
    assert key in pages[2], key
for key in ("card_nav_debloat", "card_audio_control",
            "card_network_speed", "card_nfc_fix", "layout_footer_author"):
    assert key in pages[3], key

for key in ("btn_game_launch_wildrift", "btn_main_game_touch_details",
            "btn_main_game_capture"):
    assert int(pages[2][key].attrib[ns + "minHeight"].removesuffix("dp")) >= 48
assert pages[2]["card_featured_game"].attrib[ns + "background"] == "@drawable/legion_surface_hero"
assert pages[2]["tv_game_main_access"].attrib[ns + "text"].startswith("Проверка")

for index_id,icon in zip(
    ("overview", "display", "games", "system"),
    ("home", "display", "games", "system")
):
    button = all_views[f"btn_nav_{index_id}"]
    assert button.attrib[ns + "drawableTop"] == f"@drawable/nav_icon_{icon}"
    assert button.attrib[ns + "layout_height"] == "64dp"

# All new buttons have native listeners and no simulated behavior.
for phrase in (
    "setupDashboardShortcuts();",
    "R.id.btn_quick_display",
    "R.id.btn_quick_games",
    "R.id.btn_quick_audio",
    "R.id.btn_quick_apps",
    "R.id.btn_game_launch_wildrift",
    "R.id.btn_main_game_touch_details",
    "R.id.btn_main_game_capture",
    "getLaunchIntentForPackage(",
    "MotorolaCaptureFormat.GAME_PACKAGE",
    "startActivity(launch);",
    "new Intent(MainMenuActivity.this, DebloatActivity.class)",
    "new Intent(MainMenuActivity.this, MainActivity.class)",
    "MotorolaDirectAccessGate.isBlocked(this)",
    "setCompoundDrawableTintList(",
):
    assert phrase in activity, phrase

# Gradients and native SVG-equivalent VectorDrawable resources must compile.
for name in (
    "legion_surface_hero", "legion_surface_blue",
    "legion_surface_violet", "legion_surface_green",
    "legion_surface_neutral", "legion_gamepad_hero",
    "legion_icon_apps", "legion_icon_audio", "legion_icon_network",
    "legion_icon_nfc", "legion_icon_touch",
    "nav_icon_home", "nav_icon_display", "nav_icon_games",
    "nav_icon_system"
):
    ET.parse(app / f"res/drawable/{name}.xml")
assert "setprop vendor.thermal.mode" not in activity
assert "service call moto_extend_service" not in activity
print("PASS: redesigned Legion screens preserve 4 tabs, native actions, genuine statuses and view IDs")
