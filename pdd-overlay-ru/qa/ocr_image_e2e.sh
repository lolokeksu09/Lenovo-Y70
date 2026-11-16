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
# Some hosted Android 35 emulators show a *system launcher* ANR over our
# Activity immediately after boot. Handle only that exact unrelated system
# dialog; a PDD crash is never dismissed or treated as a pass.
timeout 40s adb shell am start -W -n "$APP/.HomeActivity" > "$OUT/launch.txt"
sleep 4
for attempt in 1 2 3; do
  timeout 40s adb shell uiautomator dump /sdcard/pdd-home-preflight.xml > /dev/null
  adb exec-out cat /sdcard/pdd-home-preflight.xml > "$OUT/window-before-photo.xml"
  if grep -Fq "Pixel Launcher isn" "$OUT/window-before-photo.xml"; then
    echo 'WARN: Android emulator launcher ANR; dismissing system-only dialog'
    python3 "$QA" tap 'Close app' "$OUT/system-launcher-dialog.xml"
    adb shell am force-stop com.google.android.apps.nexuslauncher || true
    timeout 40s adb shell am start -W -n "$APP/.HomeActivity" >> "$OUT/launch.txt"
    sleep 3
    continue
  fi
  if grep -Fq 'Фото' "$OUT/window-before-photo.xml"; then
    break
  fi
  sleep 3
done
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
    # Collect visible Compose texts across a few bounded scroll positions:
    # Android's system font/viewport can move the row summary out of view.
    found_summary=0
    found_description=0
    found_tracking=0
    for step in 1 2 3 4; do
      adb shell uiautomator dump /sdcard/pdd-offline-glossary.xml > /dev/null
      adb exec-out cat /sdcard/pdd-offline-glossary.xml > "$OUT/offline-glossary-$step.xml"
      grep -Fq 'Переведено 2 из 2 строк' "$OUT/offline-glossary-$step.xml" && found_summary=1 || true
      grep -Fq 'Описание товара' "$OUT/offline-glossary-$step.xml" && found_description=1 || true
      grep -Fq 'Отследить доставку' "$OUT/offline-glossary-$step.xml" && found_tracking=1 || true
      if [[ "$found_summary:$found_description:$found_tracking" == "1:1:1" ]]; then
        break
      fi
      adb shell input swipe 520 1710 520 760 450
      sleep 2
    done
    if [[ "$found_summary:$found_description:$found_tracking" != "1:1:1" ]]; then
      echo "FAIL: offline glossary OCR incomplete: summary=$found_summary description=$found_description tracking=$found_tracking" >&2
      exit 1
    fi
    echo 'PASS: offline glossary translated both Chinese OCR labels without downloading models'
    timeout 20s adb exec-out screencap -p > "$OUT/chinese-ocr-with-offline-glossary.png"
    # Verify actual reversible translated overlays, not only the result list.
    # Return toward the top from OCR results; the viewer action sits next to
    # the preview, above the translated list. Do not assume a fixed screen size.
    opened=0
    for attempt in 1 2 3 4; do
      if python3 "$QA" tap 'Показать перевод поверх фото' "$OUT/ocr-screen-before-viewer.xml"; then
        opened=1
        break
      fi
      adb shell input swipe 520 620 520 1750 470
      sleep 2
    done
    if [[ "$opened" != 1 ]]; then
      echo 'FAIL: inline OCR viewer button unavailable after returning to preview' >&2
      exit 1
    fi
    sleep 3
    python3 "$QA" exists 'Русский текст поверх фото' "$OUT/translated-photo-viewer.xml"
    python3 "$QA" exists 'Показать оригинал' "$OUT/translated-photo-viewer.xml"
    timeout 20s adb exec-out screencap -p > "$OUT/inline-russian-caption-viewer.png"
    python3 "$QA" tap 'Показать оригинал' "$OUT/translated-photo-viewer.xml"
    python3 "$QA" exists 'Оригинальные надписи' "$OUT/original-photo-viewer.xml"
    python3 "$QA" exists 'Показать перевод' "$OUT/original-photo-viewer.xml"
    timeout 20s adb exec-out screencap -p > "$OUT/original-photo-viewer.png"
    python3 "$QA" tap 'Показать перевод' "$OUT/original-photo-viewer.xml"
    python3 "$QA" exists 'Русский текст поверх фото' "$OUT/restored-translation-viewer.xml"
    echo 'PASS: actual image OCR → offline Russian captions → reversible original/translation viewer'
    exit 0
  fi
  adb shell input swipe 520 1680 520 600 500
  sleep 1
done
echo 'FAIL: line count displayed, but Chinese OCR rows not visible' >&2
exit 1
