#!/usr/bin/env bash
# Actual ML Kit Chinese OCR on an isolated, synthetic image; no translation-model consent.
set -euo pipefail
APP='app.pinduoduo.overlayru'
OUT='pdd-ocr-image-e2e-evidence'
FIXTURE='pdd-overlay-ru/qa/fixtures/ocr_chinese_sample.png'
QA='pdd-overlay-ru/qa/ui_control.py'
mkdir -p "$OUT"
diagnostics() {
  timeout 20s adb logcat -d -t 2000 -s AndroidRuntime:E PddOverlayRU:W > "$OUT/logcat.txt" 2>/dev/null || true
  timeout 20s adb shell dumpsys activity activities > "$OUT/activities.txt" 2>/dev/null || true
}
trap diagnostics EXIT
test -s "$FIXTURE"
sha256sum "$FIXTURE" > "$OUT/fixture-sha256.txt"
adb install -r pdd-overlay-ru/android/app/build/outputs/apk/debug/app-debug.apk
# ADB push alone does not index media on Android 35. Seed a standard
# camera location, explicitly scan and require a verified MediaStore row.
REMOTE=/sdcard/DCIM/Camera/ocr_chinese_sample.png
adb shell mkdir -p /sdcard/DCIM/Camera
adb push "$FIXTURE" "$REMOTE"
timeout 15s adb shell cmd media_scanner scan-file "$REMOTE" > "$OUT/media-scan.txt" 2>&1 || true
timeout 15s adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file://$REMOTE" >> "$OUT/media-scan.txt" 2>&1 || true
timeout 30s adb shell content call --uri content://media/ --method scan_volume --arg external_primary >> "$OUT/media-scan.txt" 2>&1 || true
indexed=0
for attempt in $(seq 1 15); do
  timeout 20s adb shell content query --uri content://media/external/images/media --projection _id:_display_name > "$OUT/media-rows.txt" 2>&1 || true
  if grep -Fq 'ocr_chinese_sample.png' "$OUT/media-rows.txt"; then
    indexed=1
    break
  fi
  sleep 3
done
if [[ "$indexed" != 1 ]]; then
  echo 'FAIL: synthetic PNG not indexed in Android MediaStore, picker not exercised' >&2
  exit 1
fi
echo 'PASS: synthetic PNG indexed in MediaStore'
timeout 40s adb shell am start -W -n "$APP/.HomeActivity" > "$OUT/launch.txt"
sleep 4
python3 "$QA" tap 'Фото' "$OUT/window-before-photo.xml"
sleep 3
python3 "$QA" exists 'Перевод фото' "$OUT/window-photo.xml"
python3 "$QA" tap 'Выбрать фото' "$OUT/window-photo.xml"
sleep 4
timeout 20s adb exec-out screencap -p > "$OUT/photo-picker.png"
python3 pdd-overlay-ru/qa/pick_photo.py "$OUT/photo-picker.xml"

recognized=0
for attempt in $(seq 1 30); do
  sleep 3
  timeout 40s adb shell uiautomator dump /sdcard/pdd-recognition.xml > /dev/null
  adb exec-out cat /sdcard/pdd-recognition.xml > "$OUT/recognition.xml"
  if grep -Fq 'Распознано строк:' "$OUT/recognition.xml"; then
    recognized=1
    break
  fi
  if grep -Fq 'Не удалось распознать текст' "$OUT/recognition.xml"; then
    echo 'FAIL: ML Kit OCR error visible' >&2
    exit 1
  fi
done
if [[ "$recognized" != 1 ]]; then
  echo 'FAIL: OCR did not return a recognized-line count' >&2
  exit 1
fi
# The OCR rows are below the preview and may be outside the first viewport.
for attempt in 1 2 3 4; do
  timeout 40s adb shell uiautomator dump /sdcard/pdd-recognition.xml > /dev/null
  adb exec-out cat /sdcard/pdd-recognition.xml > "$OUT/recognition.xml"
  if python3 - "$OUT/recognition.xml" <<'PY'
import sys, re, xml.etree.ElementTree as ET
root=ET.parse(sys.argv[1]).getroot()
labels=[n.attrib.get('text','') for n in root.iter('node')]
matches=[s for s in labels if re.search(r'[\u3400-\u9fff]{2,}',s)]
if matches:
    print('PASS: real Chinese OCR text is rendered:',[m[:50] for m in matches])
    sys.exit(0)
sys.exit(1)
PY
  then
    timeout 20s adb exec-out screencap -p > "$OUT/chinese-ocr-result.png"
    echo 'PASS: seeded Chinese PNG → Android picker → real ML Kit OCR → Chinese text visible'
    exit 0
  fi
  adb shell input swipe 520 1680 520 600 500
  sleep 1
done
echo 'FAIL: line count displayed, but Chinese OCR rows not visible' >&2
exit 1
