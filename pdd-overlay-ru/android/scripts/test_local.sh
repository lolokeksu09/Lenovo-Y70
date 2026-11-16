#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
python3 scripts/validate_sources.py





# Four migrated Kotlin test suites run under Gradle :app:testReleaseUnitTest.

# Kotlin migration of Java helpers is covered by :app:testReleaseUnitTest.
