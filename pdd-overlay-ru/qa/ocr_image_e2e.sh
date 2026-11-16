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
# Use the official MediaStore content provider interface instead of ADB
# filesystem writes: Android 35 denies direct write to some DCIM paths.
# This method is documented for AOSP multi-user media tests.
MEDIA='content://media/external/images/media'
NAME='ocr_chinese_sample.png'
timeout 25s adb shell content insert --uri "$MEDIA" \
  --bind "_display_name:s:$NAME" \
  --bind 'mime_type:s:image/png' \
  --bind 'relative_path:s:Pictures/PddQa' > "$OUT/media-insert.txt"
# Query without SQL selection: adb shell argument splitting strips quotes and
# caused Android's provider to reject the filename as a raw SQL token.
timeout 25s adb shell content query --uri "$MEDIA" \
  --projection _id:_display_name > "$OUT/media-rows.txt"
MEDIA_ID="$(grep -F "$NAME" "$OUT/media-rows.txt" | sed -nE 's/.*_id=([0-9]+).*/\1/p' | head -1)"
if [[ ! "$MEDIA_ID" =~ ^[0-9]+$ ]]; then
  echo 'FAIL: no synthetic image row in Android MediaStore' >&2
  exit 1
fi
timeout 25s adb shell content write --uri "$MEDIA/$MEDIA_ID" < "$FIXTURE"
# Prove the provider stored exactly this test image before opening picker.
expected="$(sha256sum "$FIXTURE" | awk '{print $1}')"
actual="$(timeout 25s adb exec-out content read --uri "$MEDIA/$MEDIA_ID" | sha256sum | awk '{print $1}')"
if [[ "$actual" != "$expected" ]]; then
  echo 'FAIL: MediaStore image bytes do not match source PNG' >&2
  exit 1
fi
echo 'PASS: image inserted and byte-verified in Android MediaStore'
sleep 3
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
    # Offline bundled dictionary must translate known product labels even when
# ML Kit translation models have not been downloaded or consented to.
# This assertion catches the old 'if (!engine.ready()) continue' bug.
    adb shell input swipe 520 1710 520 760 450 || true
    sleep 1
    adb shell uiautomator dump /sdcard/pdd-offline-glossary.xml > /dev/null
    adb exec-out cat /sdcard/pdd-offline-glossary.xml > "$OUT/offline-glossary.xml"
    python3 - "$OUT/offline-glossary.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
labels = [n.attrib.get("text", "") for n in root.iter("node")]
expected = ("Переведено 2 из 2 строк", "Описание товара", "Отследить доставку")
missing = [item for item in expected if item not in labels]
if missing:
    print("FAIL: offline built-in glossary not applied after OCR:", missing)
    print("Visible labels:", [v[:80] for v in labels if v][:40])
    sys.exit(1)
print("PASS: offline built-in glossary translated both OCR labels without language models")
PY
    timeout 20s adb exec-out screencap -p > "$OUT/chinese-ocr-with-offline-glossary.png"
echo 'PASS: seeded Chinese PNG → Android picker → real ML Kit OCR → Chinese text visible'
    exit 0
  fi
  adb shell input swipe 520 1680 520 600 500
  sleep 1
done
echo 'FAIL: line count displayed, but Chinese OCR rows not visible' >&2
exit 1
