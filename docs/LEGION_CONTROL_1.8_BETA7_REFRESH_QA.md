# Legion Control 1.8.0 Beta 7 — проверка надежности «Проверить повторно»

## Исходная жалоба
На Motorola ROM Android 17 нажатие «Проверить повторно» выглядело как отсутствие реакции.

## Подтверждено при анализе исходников Beta 6
- Повторная проверка выполнялась в одном фоне через `AccessRepository.read()`; при длительном системном Binder-вызове никакого тайм-аута не было.
- Повторная проверка из `onResume`, поступившая до завершения предыдущей, отбрасывалась без нового чтения.
- Если Android возвращал неизменный список, это не ошибка: Beta 6 показывает сообщение «Изменений нет», но реальный отклик конкретной прошивки не подтвержден.

## Beta 7: исправления
- `AccessRefreshCoordinator` — единое владение запросом, номера поколений, объединение повторных запросов, отклонение устаревших ответов.
- Ограничение чтения **12 секунд**. Если система не отвечает, UI показывает тайм-аут, кнопка перестает быть бесконечно заблокированной.
- После первого зависания выполняется один безопасный повтор через новый executor; после двух тайм-аутов дальнейшие повторы останавливаются до нового запуска раздела, чтобы не создавать бесконечное число подвисших потоков Android.
- Повторное чтение `onResume` не теряется во время активного запроса, а выполняется после завершения текущего.
- Поведение дополнительных функций (Shizuku, управление профилями, AppOps) не изменено.
- Отладочные метки `LegionAccessCenter` сохранены: `refresh_tap`, `refresh_started`, `refresh_completed`, `refresh_timeout`. Они не содержат имен сторонних приложений.

## Тесты
- JVM: `AccessRefreshCoordinatorTest` — отложенные обновления, гонки последовательности, устаревшие результаты после тайм-аута и закрытие Activity.
- JVM: `AccessRefreshSummaryTest` — идентичные и измененные состояния, ошибки чтения.
- GitHub Actions: компиляция Android Release, проверка одинаковой постоянной подписи и versionCode.

## Не проверено
- Физические касания кнопки и поведение Android AccessibilityManager на Lenovo XT2611-1 с Motorola ROM;
- точное происхождение отсутствия реакции на исходном устройстве (для этого нужен logcat).

## Инструкция проверки на устройстве
1. Установить Beta 7 **поверх** Beta 6 (с сохранением данных).
2. Открыть «Система» → «Доступ приложений» → Kimi Claw → «Проверить повторно».
3. Убедиться, что кнопка меняется на «Проверка…», затем отображается «Изменений нет» / «Обнаружены изменения» / «Android не ответил за 12 секунд».
4. Выйти в Android Settings, вернуться, проверить что перечитывание выполняется.
5. Если касание совсем без отклика, выполнить в Termux:
```sh
./rish -c 'logcat -d -v time -s LegionAccessCenter:I' > /storage/emulated/0/Download/legion_refresh_log.txt
```
6. Сопоставить наличие меток `refresh_tap` и `refresh_completed`. Без этих меток нельзя приписывать причину Shizuku, системе или UI.

## Параметры выпуска
- `dev.legion.control`, `versionCode 16`, `versionName 1.8.0-beta7`.
- Публичную сборку для 4PDA не выпускать до фактического smoke-тестирования.

## Verified CI evidence — 2026-11-14

- GitHub Actions run: https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43589034197 — **success**.
- Source revision tested and built: `cdee38f73bcd8e5dd2b48e2de960f8d5242071ae`. Subsequent documentation-only changes do not alter its APK.
- Focused `AccessRefreshSummaryTest`: 10 checks passed.
- Focused `AccessRefreshCoordinatorTest`: 17 checks passed.
- Gradle Release: **BUILD SUCCESSFUL**.
- `apksigner verify --verbose` and pinned release signing fingerprint: **PASS**.
- `aapt dump badging`: `dev.legion.control`, code `16`, name `1.8.0-beta7`.
- Exact APK size: **230023 bytes**; SHA-256: `7f70776a305fe31c924526112ac62dc4d51610a7029e8a586af8e3bd09563a02`.
- ZIP archive integrity and APK ZIP integrity: **PASS**; APK manifest present; native ELF libraries: 0.
- Runtime UI on Motorola XT2611-1: **NOT RUN**.
