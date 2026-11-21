"""Check foreground diagnostics do not claim an active game from PID alone."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
capture = (src / "MotorolaCaptureFormat.java").read_text(encoding="utf-8")
foreground = (src / "MotorolaForegroundState.java").read_text(encoding="utf-8")
service = (src / "MotorolaGameCaptureService.java").read_text(encoding="utf-8")
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
layout = (root / "overrides/ResolutionMasterApp/res/layout/activity_motorola_game.xml").read_text(encoding="utf-8")

assert 'game-process=' in capture, "PID cannot imply foreground state"
assert 'MotorolaForegroundState.READ_COMMAND' in service
assert 'MotorolaForegroundState.parse(' in service
assert 'foreground=' in service
assert 'if (i == 0 || i == 4 || i == 8 || i == 11)' in service
assert 'return "unknown"' in foreground
assert 'wild-rift' in foreground and 'other' in foreground
assert 'dumpsys activity activities' in foreground
assert 'getPid(' not in foreground  # not an identity proof
assert '"Service: " + serviceLabel + "\\n"' in activity, "no duplicate Binder status"
assert 'tvAccess.setText(access + "\\n" +' in activity
assert 'Direct Motorola Binder: ' in activity
for bad in ("setprop ", "settings put ", "pm grant ", "service call ", "/sys/"):
    assert bad not in capture + foreground + service
print("PASS: foreground vs process diagnostics and nonduplicated service status")
