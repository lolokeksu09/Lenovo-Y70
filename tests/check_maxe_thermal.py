"""Verify read-only thermal/Motorola integration and honest status reporting."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
src = app / "src/com/moto/resolutionmaster"
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
parser = (src / "MotorolaThermalState.java").read_text(encoding="utf-8")
layout = (app / "res/layout/activity_motorola_game.xml").read_text(encoding="utf-8")

assert 'MotorolaThermalState.READ_COMMAND' in activity
assert 'MotorolaThermalState.parse(result.detail)' in activity
assert 'MotorolaThermalState.unavailable(' in activity
assert 'ShellHelper.runCommandWithResult(' in activity
assert 'tvThermal.setText(thermalSummary);' in activity
assert '"\\nThermal: " + tvThermal.getText()' in activity
assert 'tv_game_thermal' in activity and '@+id/tv_game_thermal' in layout
assert 'getprop vendor.motosxf.mode' in parser
assert 'getprop vendor.thermal.mode' in parser
assert 'getprop init.svc.thermal-engine' in parser
assert 'game-perf.conf' in parser
assert 'Активный thermal-конфиг не установлен этой проверкой.' in parser
for forbidden in ('setprop ', 'settings put ', 'restart thermal', 'stop thermal', 'start thermal', '/sys/class/'):
    assert forbidden not in parser, forbidden
ET.fromstring(layout)
print('PASS: safe MAXE/thermal readback wiring')
