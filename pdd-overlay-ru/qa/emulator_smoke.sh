#!/usr/bin/env bash
# Single script file: android-emulator-runner executes its script as a shell command.
set -euo pipefail

APK='pdd-overlay-ru/android/app/build/outputs/apk/debug/app-debug.apk'
OUT='pdd-smoke-evidence'
PACKAGE='app.pinduoduo.overlayru'
mkdir -p "$OUT"

collect_diagnostics() {
  timeout 15s adb logcat -d -t 1200 -s AndroidRuntime:E PddOverlayRU:W > "$OUT/focused-logcat.txt" 2>/dev/null || true
  timeout 15s adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
}
trap collect_diagnostics EXIT

adb install -r "$APK"
timeout 40s adb shell am start -W -n "$PACKAGE/.HomeActivity" > "$OUT/HomeActivity-launch.txt"
sleep 4

audit_screen() {
  local name="$1" expected="$2"
  timeout 20s adb shell pidof "$PACKAGE" > "$OUT/$name.pid"
  timeout 20s adb shell dumpsys activity activities > "$OUT/$name-activity.txt"
  if ! grep -E 'topResumedActivity|mResumedActivity' "$OUT/$name-activity.txt" | grep -Fq "$expected"; then
    echo "FAIL: expected Activity $expected for $name" >&2
    grep -E 'topResumedActivity|mResumedActivity' "$OUT/$name-activity.txt" >&2 || true
    return 1
  fi
  timeout 20s adb exec-out screencap -p > "$OUT/$name.png"
  test "$(wc -c < "$OUT/$name.png")" -gt 4000
}

audit_screen Home HomeActivity

for tab in 'Фото' 'Словарь' 'Настройки' 'Главная'; do
  case "$tab" in
    'Фото') expected='ImageOcrActivity' ;;
    'Словарь') expected='GlossaryEditorActivity' ;;
    'Настройки') expected='MainActivity' ;;
    'Главная') expected='HomeActivity' ;;
  esac
  timeout 35s adb shell uiautomator dump /sdcard/pdd-window.xml > /dev/null
  adb exec-out cat /sdcard/pdd-window.xml > "$OUT/window-$tab.xml"
  python3 pdd-overlay-ru/qa/select_bottom_tab.py "$tab" "$OUT/window-$tab.xml" > "$OUT/point.txt"
  read -r x y < "$OUT/point.txt"
  adb shell input tap "$x" "$y"
  sleep 3
  audit_screen "$tab" "$expected"
done

echo 'PASS: Home / Photo / Dictionary / Settings / Home Android 35 navigation and screenshots'
