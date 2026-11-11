# Lenovo Y70 — Android companion

Реконструированный Android-проект Lenovo Y70 Clone v2.7 с Shizuku. Это новая реализация, **не оригинальные исходники APK v2.6**.

## Установка

[Скачать APK из успешной GitHub Actions сборки](https://github.com/lolokeksu09/Lenovo-Y70/actions/runs/43055169862).

Откройте **Artifacts** → `LenovoY70-v2.7-Preview-APK`, скачайте ZIP и установите вложенный `app-debug.apk`. Debug-версия имеет ID `com.moto.resolution.clone.preview` и устанавливается отдельно от рабочего клона v2.6; она подписана стандартным отладочным ключом Android, а не ключом релиза. Время хранения артефакта — 14 дней.

## Исходный код и сборка

Android Studio-проект хранится в архиве, разбитом на шесть частей `source/chunk-01.b64` … `source/chunk-06.b64`. GitHub Actions восстанавливает его с проверкой SHA-256, накладывает исправление `patches/app/src/main/aidl/com/moto/resolution/clone/shizuku/ICommandService.aidl` и запускает `gradle :app:assembleDebug` с Android SDK 35.

Workflow: `.github/workflows/build-preview.yml`.

**Безопасность:** ключ подписи существующей v2.6 в репозиторий не загружен. Перед использованием системных функций протестируйте v2.7 отдельно на устройстве.
