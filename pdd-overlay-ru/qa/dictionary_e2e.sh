#!/usr/bin/env bash
# No live customer data. Test inputs are synthetic and local to the Android emulator.
set -euo pipefail
APP='app.pinduoduo.overlayru'
QA='pdd-overlay-ru/qa/ui_control.py'
OUT='pdd-dictionary-e2e-evidence'
mkdir -p "$OUT"

diagnose() {
  timeout 20s adb logcat -d -t 1200 -s AndroidRuntime:E PddOverlayRU:W > "$OUT/logcat.txt" 2>/dev/null || true
  timeout 20s adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
}
trap diagnose EXIT

function tap() {
  python3 "$QA" tap "$1" "$OUT/last-screen.xml"
}
function field() {
  python3 "$QA" field "$1" "$OUT/last-screen.xml"
}
function exists() {
  python3 "$QA" exists "$1" "$OUT/last-screen.xml"
}
function absent() {
  python3 "$QA" absent "$1" "$OUT/last-screen.xml"
}
function hide_keyboard() {
  adb shell input keyevent KEYCODE_BACK
  sleep 1
}
function screenshot() {
  timeout 20s adb exec-out screencap -p > "$OUT/$1.png"
  test "$(wc -c < "$OUT/$1.png")" -gt 4000
}

adb install -r pdd-overlay-ru/android/app/build/outputs/apk/debug/app-debug.apk
timeout 40s adb shell am start -W -n "$APP/.HomeActivity" > "$OUT/home-launch.txt"
sleep 4
tap 'Словарь'
sleep 2
exists 'Мой словарь'
exists '0/200'
screenshot '00-empty'

tap 'Добавить перевод'
exists 'Новый перевод'
field 'Оригинальная надпись'
adb shell input text 'trial%swidget'
field 'Русский перевод'
adb shell input text 'test%sones'
hide_keyboard
tap 'Сохранить перевод'
sleep 2
exists 'trial widget'
exists 'test ones'
exists '1/200'
screenshot '01-created'

# Relaunch the actual app process, not the test harness, to verify durable storage.
adb shell am force-stop "$APP"
timeout 40s adb shell am start -W -n "$APP/.HomeActivity" > "$OUT/relaunch.txt"
sleep 3
tap 'Словарь'
sleep 2
exists 'trial widget'
exists '1/200'

tap 'Править'
exists 'Изменить перевод'
field 'Оригинальная надпись'
adb shell input keyevent KEYCODE_MOVE_END
adb shell input text '%ssaved'
hide_keyboard
tap 'Сохранить изменения'
sleep 2
exists 'trial widget saved'
absent 'trial widget'
exists '1/200'
screenshot '02-renamed'

tap 'Удалить'
exists 'Удалить перевод?'
tap 'Отмена'
exists 'trial widget saved'
tap 'Удалить'
exists 'Удалить перевод?'
tap 'Удалить'
sleep 2
exists '0/200'
exists 'Словарь пока пуст'
screenshot '03-deleted'

# Picker cancel must never remove the user's ability to use the OCR screen.
tap 'Фото'
sleep 2
exists 'Перевод фото'
tap 'Выбрать фото'
sleep 2
adb shell input keyevent KEYCODE_BACK
sleep 2
exists 'Перевод фото'
timeout 20s adb shell pidof "$APP" > "$OUT/photo-after-cancel.pid"
screenshot '04-ocr-picker-cancel'
echo 'PASS: dictionary add/rename/persist/cancel/delete and OCR picker cancel on Android emulator'
