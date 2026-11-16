#!/data/data/com.termux/files/usr/bin/bash
# Read-only status check for PDD Overlay RU on the user's Lenovo Legion Y70.
# Requires a running Shizuku and a working ~/rish. Does not change settings.
set -euo pipefail

OUT='/storage/emulated/0/Download/PDD-beta11-device-check.txt'
RISH="${HOME}/rish"
PACKAGE='app.pinduoduo.overlayru'

if [ ! -x "$RISH" ]; then
  printf 'Не найден исполняемый файл %s. Проверь установку Shizuku rish.\n' "$RISH" >&2
  exit 1
fi
if [ ! -d /storage/emulated/0/Download ]; then
  printf 'Папка Download недоступна. Разреши Termux доступ к файлам.\n' >&2
  exit 1
fi
if ! "$RISH" -c 'id' >/dev/null 2>&1; then
  echo 'Shizuku/rish не подключён. Запусти Shizuku и повтори.' >&2
  exit 1
fi

{
  printf '=== DEVICE ===\n'
  "$RISH" -c 'getprop ro.product.model; getprop ro.build.version.release; getprop ro.product.cpu.abi'
  printf '\n=== PACKAGE VERSION ===\n'
  "$RISH" -c "dumpsys package $PACKAGE | grep -E 'versionCode=|versionName=' | head -n 4" || true
  printf '\n=== PACKAGE INSTALLED ===\n'
  "$RISH" -c "pm path $PACKAGE" || true
  printf '\n=== ACCESSIBILITY PERMISSION ===\n'
  "$RISH" -c "settings get secure enabled_accessibility_services | grep -Fq $PACKAGE && echo 'enabled' || echo 'disabled_or_unknown'"
  printf '\n=== PROCESS ===\n'
  "$RISH" -c "pidof $PACKAGE || true"
  printf '\n=== FINISH ===\n'
  echo 'Only read-only device and app state; no logs, permissions or payment data.'
} > "$OUT" 2>&1

printf 'Готово. Отчёт: %s\n' "$OUT"
