"""Regression for confirmed NoClassDefFoundError in Android crash log."""
from pathlib import Path

src = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
controller = (src / "MotoGameController.java").read_text(encoding="utf-8")
classifier = (src / "MotorolaReflectionFailurePolicy.java").read_text(encoding="utf-8")
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
filter_src = (src / "MotorolaCrashLogFilter.java").read_text(encoding="utf-8")

# Android does not expose Java 9's InaccessibleObjectException on this device.
assert "instanceof java.lang.reflect.InaccessibleObjectException" not in controller
assert "MotorolaReflectionFailurePolicy.isRestrictedAccess(cause)" in controller
assert '"java.lang.reflect.InaccessibleObjectException".equals(' in classifier
assert "InaccessibleObjectException.class" not in classifier
assert "instanceof java.lang.reflect.InaccessibleObjectException" not in classifier

# Error handling must not reveal unrelated Android Settings process logs.
assert "MotorolaCrashLogFilter.relevantCrashExcerpt(raw)" in activity
assert "Process: com.moto.resolutionmaster" in filter_src
assert "getPid(" in filter_src
print("PASS: no Android-incompatible class linkage and scoped crash report")
