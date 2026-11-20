"""Check Binder lifecycle, fail-safe recovery and no hidden retry of OEM mutations."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / "overrides/ResolutionMasterApp/src/com/moto/resolutionmaster"
control = (src / "MotoGameController.java").read_text(encoding="utf-8")
policy = (src / "MotorolaBinderFailurePolicy.java").read_text(encoding="utf-8")

assert 'IBinder coreServiceBinder;' in control
assert 'IBinder.DeathRecipient coreDeathRecipient;' in control
assert 'binder.linkToDeath(recipient, 0);' in control
assert 'coreServiceBinder == binder' in control  # stale death cannot clear a newer core
assert 'coreServiceBinder.isBinderAlive()' in control
assert 'coreServiceBinder.pingBinder()' in control
assert 'clearCachedCoreLocked();' in control
assert 'if (preferRegisteredCore) return coreFromRegisteredService(context);' in control
assert control.count('MotorolaBinderFailurePolicy.isDeadBinderFailure(t)') == 2
assert 'Команда автоматически не повторяется.' in control
assert 'сначала проверь состояние Touch Boost' in control
assert '"android.os.DeadObjectException"' in policy
assert 'InvocationTargetException' in policy
# No relaunch of a possibly executed thermal, touch or DPS request inside a catch.
for forbidden in ('setprop ', 'pm grant', 'service call', '/sys/', 'settings put '):
    assert forbidden not in control
print("PASS: Motorola Binder death recovery is fail-safe and does not replay OEM writes")
