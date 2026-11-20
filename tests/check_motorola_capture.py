"""Capture must remain user-triggered, bounded, private and read-only."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
app = root / "overrides/ResolutionMasterApp"
src = app / "src/com/moto/resolutionmaster"
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
service = (src / "MotorolaGameCaptureService.java").read_text(encoding="utf-8")
report = (src / "MotorolaCaptureReport.java").read_text(encoding="utf-8")
format_src = (src / "MotorolaCaptureFormat.java").read_text(encoding="utf-8")
store = (src / "MotorolaCaptureStore.java").read_text(encoding="utf-8")
manifest = (app / "AndroidManifest.xml").read_text(encoding="utf-8")
layout = (app / "res/layout/activity_motorola_game.xml").read_text(encoding="utf-8")

assert "btn_game_capture_start" in activity and "@+id/btn_game_capture_start" in layout
assert "btn_game_capture_stop" in activity and "@+id/btn_game_capture_stop" in layout
assert "btn_game_capture_copy" in activity and "@+id/btn_game_capture_copy" in layout
assert "copyGameCapture()" in activity
assert 'ShellHelper.copyToClipboard(this, report, "Legion Y70 game capture")' in activity
assert "tv_game_capture_status" in activity and "@+id/tv_game_capture_status" in layout
assert "startForegroundService(" in activity and "startActivity(game);" in activity
assert "MotorolaCaptureStore.report(this)" in activity
assert "MotorolaCaptureStore.isActive(this)" in activity
assert 'MotorolaGameCaptureService.ACTION_START' in activity
assert 'MotorolaGameCaptureService.ACTION_STOP' in activity

assert "startForeground(" in service
assert "ShellHelper.runCommandWithResult(" in service
assert "MotorolaCaptureFormat.LIGHT_COMMAND" in service
assert '"dumpsys moto_extend_service"' in service
assert "MotorolaCaptureReport.MAX_SAMPLES" in service
assert "MotorolaCaptureReport.SAMPLE_INTERVAL_MS" in service
assert "stopSelf();" in service
assert 'return START_NOT_STICKY;' in service
assert "MotorolaCaptureStore.finish(" in service
assert "private static final String PREFS" in store
assert "Context.MODE_PRIVATE" in store
assert "MAX_SAMPLES = 12" in report
assert "SAMPLE_INTERVAL_MS = 8000L" in report

for forbidden in ("setprop ", "settings put ", "service call ", "pm grant", "su -c",
                  "force_clk_on", "force_bus_on", "thermal-engine-v2.conf"):
    assert forbidden not in format_src + service, forbidden
assert "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" in manifest
assert "motorola_game_capture" in manifest
assert "specialUse" in manifest

ET.fromstring(manifest)
ET.fromstring(layout)
print("PASS: bounded foreground game capture uses read-only Shizuku and private storage")
