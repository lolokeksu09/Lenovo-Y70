"""Check AMOLED redesign preserves each Android control and routes real actions."""
from collections import Counter
from pathlib import Path
import xml.etree.ElementTree as ET

base = Path(__file__).resolve().parents[1]
res = base / "overrides/ResolutionMasterApp/res"
src = base / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
game = ET.parse(res / "layout/activity_motorola_game.xml").getroot()
menu = ET.parse(res / "layout/activity_main_menu.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"

def indexed(root):
    found = {}
    for element in root.iter():
        id_ = element.attrib.get(ns + "id")
        if id_:
            name = id_.removeprefix("@+id/").removeprefix("@id/")
            if name in found:
                raise AssertionError("Duplicate ID: " + name)
            found[name] = element
    return found

g, m = indexed(game), indexed(menu)
required_game = {
    "tv_game_access", "tv_game_control_feedback", "btn_game_refresh",
    "btn_game_shizuku", "tv_game_dps", "tv_game_dps_request",
    "btn_game_dps_set", "tv_game_touch", "tv_game_mode", "btn_game_touch_set",
    "btn_game_direct_retry", "tv_game_support", "tv_game_thermal",
    "btn_game_gametime", "tv_game_observed", "btn_game_copy_report",
    "btn_game_capture_start", "btn_game_capture_stop",
    "btn_game_capture_copy", "tv_game_capture_status",
    "btn_game_details", "layout_game_technical"
}
assert required_game <= g.keys(), required_game - g.keys()
assert game.attrib[ns+"background"] == "@color/legion_black"
assert g["layout_game_technical"].attrib[ns+"visibility"] == "gone"
assert g["btn_game_capture_start"].attrib[ns+"background"] == "@drawable/btn_primary"
assert g["btn_game_direct_retry"].attrib[ns+"visibility"] == "gone"
for id_, elem in g.items():
    if elem.tag == "Button":
        assert int(elem.attrib[ns+"minHeight"].removesuffix("dp")) >= 48, id_
        assert int(elem.attrib[ns+"textSize"].removesuffix("sp")) >= 13, id_

assert "card_featured_game" in m
assert "btn_open_motorola_game" in m
featured = m["card_featured_game"]
assert any(x.attrib.get(ns+"id") == "@+id/btn_open_motorola_game"
           for x in featured.iter()), "Existing GameTime button must move, not duplicate"
ids_order = list(m.keys())
assert "page_tab_games" in m and "page_tab_display" in m
assert "bottom_navigation" in m and "page_tab_overview" in m
assert ids_order.count("btn_open_motorola_game") == 1
assert "layout_footer_author" in m, "Upstream credits must be preserved"

activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
menu_activity = (src / "MainMenuActivity.java").read_text(encoding="utf-8")
assert "btnGameDetails.setOnClickListener(" in activity
assert "technicalExpanded = !technicalExpanded;" in activity
assert 'state.putBoolean("motorola.game.technical_expanded"' in activity
assert "layoutGameTechnical.setVisibility(" in activity
assert "btnGameTime.setVisibility(ownBinderMissing ? View.GONE : View.VISIBLE);" in activity
assert 'btnOpenMotorolaGame.setText("Открыть игровые функции");' in menu_activity

colors = (res / "values/colors.xml").read_text(encoding="utf-8")
assert "#050506" in colors and 'name="legion_red"' in colors
for name in ("colors.xml",):
    ET.parse(res / "values" / name)
for name in ("card_bg", "btn_primary", "btn_chip",
             "btn_secondary", "legion_panel", "legion_action_secondary"):
    ET.parse(res / "drawable" / (name + ".xml"))
assert (res / "drawable/btn_primary.xml").read_text().find("<ripple") > 0
print("PASS: AMOLED layout, real action IDs, collapsible diagnostics, no duplicate GameTime route")
