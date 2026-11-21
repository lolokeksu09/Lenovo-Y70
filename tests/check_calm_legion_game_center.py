"""Canonical spec acceptance for the native Calm Legion Game Center."""
import xml.etree.ElementTree as ET
from pathlib import Path

base = Path(__file__).resolve().parents[1]
app = base / "overrides/ResolutionMasterApp"
src = app / "src/com/moto/resolutionmaster"
layout = ET.parse(app / "res/layout/activity_main_menu.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
views = {}
for view in layout.iter():
    tag = view.attrib.get(ns + "id")
    if tag:
        key = tag.rsplit("/", 1)[-1]
        assert key not in views, "Duplicate id " + key
        views[key] = view

pages, nav = list(layout)
assert len(list(pages)) == 4
assert nav.attrib[ns + "id"] == "@+id/bottom_navigation"
assert nav.attrib[ns + "minHeight"] == "64dp"
games = list(pages)[2]
assert games.attrib[ns + "id"] == "@+id/page_tab_games"
game_ids = {v.attrib[ns + "id"].rsplit("/", 1)[-1]
            for v in games.iter() if ns + "id" in v.attrib}

# Five real sections, preserving all prior bound IDs.
for element in (
    "card_featured_game", "btn_open_motorola_game", "btn_game_launch_wildrift",
    "card_power_control", "tv_battery_status", "btn_toggle_power_saver",
    "btn_open_battery_care", "card_game_touch", "tv_game_main_access",
    "btn_main_game_touch_details", "btn_main_game_capture",
    "btn_game_diag_status", "btn_game_diag_log",
    "btn_game_quick_gametime", "btn_game_quick_screen",
    "btn_game_quick_power", "btn_game_quick_tools",
    "card_my_games", "tv_my_games_title", "layout_game_cards",
):
    assert element in game_ids, element

for key in game_ids:
    if key.startswith("btn_"):
        size = views[key].attrib.get(ns + "minHeight", "")
        assert size.endswith("dp") and int(size[:-2]) >= 48, key

# Game discovery must be genuine and private, with absent games hidden.
library = (src / "GameLibrary.java").read_text(encoding="utf-8")
policy = (src / "GameLibraryPolicy.java").read_text(encoding="utf-8")
menu = (src / "MainMenuActivity.java").read_text(encoding="utf-8")
controller = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
assert "queryIntentActivities(" in library
assert "Intent.CATEGORY_LAUNCHER" in library
assert "ApplicationInfo.CATEGORY_GAME" in library
assert "GameLibraryPolicy.include(" in library
assert "MotorolaCaptureFormat.GAME_PACKAGE" in policy
assert "getLaunchIntentForPackage(" in menu
assert "getApplicationIcon(game.packageName)" in menu
assert "GameLibrary.installedGames(" in menu
assert "section.setVisibility(View.GONE)" in menu
assert "isFinishing() || isDestroyed()" in menu
assert "startActivity(launch)" in menu
assert "getIntent().removeExtra(EXTRA_START_CAPTURE)" in controller
assert "startGameCapture()" in controller
assert "MotorolaCaptureStore.report(this)" in menu
assert "ShellHelper.copyToClipboard(this, report" in menu
assert "PowerHelper.launchBatteryCare(" in menu
assert "openOfficialGameTime()" in menu

# No false certainty in initial statuses, or imaginary features.
game_xml = ET.tostring(games, encoding="unicode")
for forbidden in (
    "Bypass: Активен", "Touch Boost: Готов",
    "1080p", "60 FPS", "0,5×", "0.5x", "100% ускорение",
    "Call of Duty", "Genshin Impact", "Скорость указателя",
):
    assert forbidden not in game_xml, forbidden
assert "Проверка доступа Motorola" in game_xml
assert "Bypass: проверить" in game_xml
assert "card_my_games" in game_ids
assert views["card_my_games"].attrib[ns+"visibility"] == "gone"
assert (base / "docs/CALM-LEGION-GAME-CENTER.md").exists()

# No hardware writes introduced in UI or discovery.
for forbidden in ("setprop ", "service call ", "/sys/", "pm grant ",
                  "pointer_speed", "setPointerSpeed"):
    assert forbidden not in library + policy, forbidden

print("PASS: five real Game Center sections, safe OEM behavior, installed-game discovery")
