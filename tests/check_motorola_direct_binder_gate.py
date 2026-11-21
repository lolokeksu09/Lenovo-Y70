"""Regression: known Binder miss routes to official app, without privilege bypass."""
from pathlib import Path
from xml.etree import ElementTree as ET

base = Path(__file__).resolve().parents[1]
src = base / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
controller = (src / "MotoGameController.java").read_text(encoding="utf-8")
gate = (src / "MotorolaDirectAccessGate.java").read_text(encoding="utf-8")
route = (src / "MotorolaTouchRoute.java").read_text(encoding="utf-8")
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
layout = (base / "overrides/ResolutionMasterApp/res/layout/activity_motorola_game.xml").read_text(encoding="utf-8")

assert 'MotorolaDirectAccessGate.recordMissingBinder(context)' in controller
assert 'MotorolaDirectAccessGate.isBlocked(context)' in controller
assert 'MotorolaDirectAccessGate.reset(context)' in controller
assert 'MotorolaTouchRoute.useOfficialGameTime(' in activity
assert 'MotorolaTouchRoute.actionLabel(' in activity
assert 'MotorolaTouchRoute.accessSummary(' in activity
assert 'MotorolaDirectAccessGate.reset(this)' in activity
assert 'openGameTime();' in activity
assert 'btn_game_direct_retry' in activity and '@+id/btn_game_direct_retry' in layout
assert 'Build.FINGERPRINT' in gate and 'MODE_PRIVATE' in gate
assert 'game_package_name' in controller and 'setGameModeState' in controller
assert 'Прямой API' in route and 'штатный GameTime' in route
assert 'getLaunchIntentForPackage("com.motorola.gamemode")' in activity
assert 'getLaunchIntentForPackage(' in activity
for illegal in ('setprop ', 'pm grant ', 'service call ', '/sys/', 'su -c'):
    assert illegal not in controller + activity + gate + route
ET.fromstring(layout)
print("PASS: scoped OEM Binder denial routes to supported GameTime")
