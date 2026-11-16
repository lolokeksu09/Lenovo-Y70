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
  local expected_heading
  case "$expected" in
    HomeActivity) expected_heading='Перевод' ;;
    ImageOcrActivity) expected_heading='Перевод фото' ;;
    GlossaryEditorActivity) expected_heading='Мой словарь' ;;
    MainActivity) expected_heading='Настройки перевода' ;;
  esac
  local visible=0
  for attempt in 1 2 3 4 5; do
    timeout 35s adb shell uiautomator dump /sdcard/pdd-visible.xml > /dev/null
    adb exec-out cat /sdcard/pdd-visible.xml > "$OUT/$name-visible.xml"
    if grep -Fq "text=\"$expected_heading\"" "$OUT/$name-visible.xml"; then
      visible=1
      break
    fi
    sleep 2
  done
  if [ "$visible" != 1 ]; then
    echo "FAIL: $expected resumed but its visible heading '$expected_heading' was not drawn" >&2
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

# Accessibility geometry regression: exercise UI under 1.3x Android font scale.
adb shell settings put system font_scale 1.3
font_scale="$(adb shell settings get system font_scale | tr -d '\r')"
test "$font_scale" = "1.3"
printf '%s\n' "$font_scale" > "$OUT/font-scale-verified.txt"
adb shell am force-stop "$PACKAGE"
timeout 40s adb shell am start -W -n "$PACKAGE/.HomeActivity" > "$OUT/Font13-Home-launch.txt"
sleep 4
audit_screen Font13-Home HomeActivity

for tab in 'Фото' 'Словарь' 'Настройки'; do
  case "$tab" in
    'Фото') expected='ImageOcrActivity' ;;
    'Словарь') expected='GlossaryEditorActivity' ;;
    'Настройки') expected='MainActivity' ;;
  esac
  timeout 35s adb shell uiautomator dump /sdcard/pdd-window.xml > /dev/null
  adb exec-out cat /sdcard/pdd-window.xml > "$OUT/Font13-window-$tab.xml"
  python3 pdd-overlay-ru/qa/select_bottom_tab.py "$tab" "$OUT/Font13-window-$tab.xml" > "$OUT/Font13-point.txt"
  read -r x y < "$OUT/Font13-point.txt"
  adb shell input tap "$x" "$y"
  sleep 3
  audit_screen "Font13-$tab" "$expected"
done

echo 'PASS: 1.3x system font-scale navigation screenshots on Home / Photo / Dictionary / Settings'
