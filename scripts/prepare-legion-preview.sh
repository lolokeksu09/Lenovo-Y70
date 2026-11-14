#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
gradle = Path('legion-control-center/app/build.gradle')
s = gradle.read_text()
needle = '    buildFeatures { aidl true }'
if s.count(needle) != 1:
    raise SystemExit('Expected Gradle insertion point not found')
s = s.replace(needle, "    buildTypes { debug { applicationIdSuffix '.preview11' } }\n" + needle)
gradle.write_text(s)
manifest = Path('legion-control-center/app/src/main/AndroidManifest.xml')
s = manifest.read_text()
if s.count('android:label="Legion Control"') != 1:
    raise SystemExit('Expected app label not found')
manifest.write_text(s.replace('android:label="Legion Control"', 'android:label="Legion Control Preview"'))
PY
