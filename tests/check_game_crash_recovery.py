"""Source-level regression for crash feedback and OEM worker containment."""
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "overrides/ResolutionMasterApp"
src = root / "src/com/moto/resolutionmaster"
activity = (src / "MotorolaGameActivity.java").read_text(encoding="utf-8")
controller = (src / "MotoGameController.java").read_text(encoding="utf-8")
trace = (src / "MotorolaControlTrace.java").read_text(encoding="utf-8")

assert 'context.getPackageManager().getPackageInfo(gamePackage, 0);' in controller
assert 'catch (SecurityException e)' in controller
assert 'catch (RuntimeException e)' in controller
assert "Сбой PackageManager при проверке игры:" in controller

assert 'MotorolaControlTrace.begin(this, operation)' in activity
assert 'if (!MotorolaControlTrace.begin(this, operation))' in activity
assert 'MotorolaControlTrace.finish(MotorolaGameActivity.this, feedback);' in activity
assert 'catch (Throwable t)' in activity
assert '"Motorola operation interrupted"' in activity
assert 'notifyAction(feedback);' in activity
assert 'MotorolaControlTrace.pending(this)' in activity
assert '"Предыдущая операция «"' in activity
assert 'getSharedPreferences(' in trace
assert '.putString(KEY_PENDING, operation).commit()' in trace
assert '.remove(KEY_PENDING)' in trace
assert '.putString(KEY_LAST,' in trace
assert 'new Thread(new Runnable()' in activity
assert '"logcat -d -b crash -v threadtime -t 600"' in activity
assert 'relevantCrashExcerpt(raw)' in activity
assert '"Process: com.moto.resolutionmaster"' in activity
assert '"Cmdline: com.moto.resolutionmaster"' in activity
assert 'ShellHelper.copyToClipboard(' in activity
assert 'Last relevant crash:' in activity

# Never conflate registering a request with proving actual hardware effect.
assert 'final boolean confirmed = call.sent &&' in activity
assert 'verified(observed, bypass, enable, gamePackage)' in activity
assert 'if (!hasDpsPermission())' in activity

print("PASS: Motorola PackageManager exceptions guarded, recovery marker persists and crash log copied on demand")
