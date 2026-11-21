"""Structural navigation regression: four real, switchable pages with fixed bottom bar."""
import xml.etree.ElementTree as ET
from pathlib import Path

base = Path(__file__).resolve().parents[1]
res = base / "overrides/ResolutionMasterApp/res"
java = (base / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster/MainMenuActivity.java").read_text(encoding="utf-8")
root = ET.parse(res / "layout/activity_main_menu.xml").getroot()
ns = "{http://schemas.android.com/apk/res/android}"
assert root.tag == "LinearLayout"
assert root.attrib[ns + "orientation"] == "vertical"
assert len(list(root)) == 2, "content frame and bottom bar must be the only root children"
frame, bottom = list(root)
assert frame.tag == "FrameLayout"
assert frame.attrib[ns + "layout_height"] == "0dp"
assert frame.attrib[ns + "layout_weight"] == "1"
assert bottom.tag == "LinearLayout"
assert bottom.attrib[ns + "id"] == "@+id/bottom_navigation"
assert bottom.attrib[ns + "orientation"] == "horizontal"
assert bottom.attrib[ns + "minHeight"] == "56dp"
expected = ["overview", "display", "games", "system"]
page_ids = ["page_tab_" + x for x in expected]
button_ids = ["btn_nav_" + x for x in expected]
assert [el.attrib[ns + "id"].removeprefix("@+id/") for el in list(frame)] == page_ids
assert [el.attrib[ns + "id"].removeprefix("@+id/") for el in list(bottom)] == button_ids
assert list(frame)[0].attrib[ns + "visibility"] == "visible"
assert all(el.attrib[ns + "visibility"] == "gone" for el in list(frame)[1:])
assert all(el.attrib[ns + "layout_height"] == "56dp" for el in list(bottom))
assert all(el.attrib[ns + "layout_weight"] == "1" for el in list(bottom))

def all_ids(parent):
    return [el.attrib[ns + "id"].removeprefix("@+id/")
            for el in parent.iter() if ns + "id" in el.attrib]
ids = all_ids(root)
assert len(ids) == len(set(ids)), "IDs duplicated during page partition"
pages = [set(all_ids(el)) for el in frame]
for page, needed in [
    (pages[0], ["tv_menu_res", "tv_menu_fps", "tv_menu_shizuku"]),
    (pages[1], ["card_nav_display", "btn_open_display", "card_touch_control", "card_eye_care"]),
    (pages[2], ["card_featured_game", "btn_open_motorola_game", "card_power_control"]),
    (pages[3], ["card_audio_control", "card_network_speed", "card_nav_debloat",
                "card_nfc_fix", "layout_footer_author"]),
]:
    assert set(needed) <= page, (set(needed) - page)
assert "btn_open_motorola_game" not in pages[0], "no duplicate GameTime entry"

for route in ("initBottomNavigation(savedInstanceState)", "showBottomTab(tab)",
              "setVisibility(active ? View.VISIBLE : View.GONE)",
              "state.getInt(\"main.bottom_tab\", 0)",
              "outState.putInt(\"main.bottom_tab\", activeBottomTab)",
              "setContentDescription(", "onBackPressed()"):
    assert route in java, route
for item in ("R.id.page_tab_overview", "R.id.page_tab_display",
             "R.id.page_tab_games", "R.id.page_tab_system",
             "R.id.btn_nav_overview", "R.id.btn_nav_display",
             "R.id.btn_nav_games", "R.id.btn_nav_system"):
    assert item in java, item
for drawable in ("nav_tab_active", "nav_tab_idle"):
    ET.parse(res / "drawable" / (drawable + ".xml"))
assert "setOnClickListener" in java
print("PASS: four persistent bottom tabs, 56dp touch targets, preserved settings, restored selection")
