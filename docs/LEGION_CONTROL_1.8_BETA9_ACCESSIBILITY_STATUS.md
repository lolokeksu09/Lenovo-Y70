# Legion Control 1.8.0 Beta 9 — проверяемые статусы Accessibility

## Факт, подтверждённый скриншотами Lenovo XT2611-1

- На странице Android «Scene — специальные возможности» доступен выключенный переключатель: службу можно включить обычным способом.
- В Legion Control Beta 8 SCENE, Kimi Claw, MT Manager, Shizuku и некоторые системные службы показывались одинаково: «Выключена · доступ не проверен».
- PDD · Перевод экрана определялся корректно как включённый.
- Ранее Android явно показывал Kimi Claw как «Контролируется настройками с ограниченным доступом».

## Причина

`AccessRepository` пытается прочитать скрытый метод `AccessibilityManager.isAccessibilityTargetAllowed()`, но на Motorola ROM стандартное приложение не может полагаться на этот внутренний API. Он может отсутствовать либо блокироваться hidden-API restrictions. Данные `enabled` не дают права заявлять, что выключенная служба ограничена, а `AppOps ACCESS_RESTRICTED_SETTINGS: default` в современных Android не доказывает отсутствие ECM — состояние может определяться динамически.

Источники:
- AOSP EnhancedConfirmationManager: https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/android/app/ecm/EnhancedConfirmationManager.java
- AOSP AccessibilityManagerService: https://android.googlesource.com/platform/frameworks/base/+/master/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java

## Исправление Beta 9

- Для статуса `DISABLED_UNVERIFIED` пользователь может после просмотра системных настроек указать **«Переключатель доступен, но выключен»** или **«Android пишет “Ограниченный доступ”»**.
- Ручная отметка обозначается как таковая. Она никогда не представляется результатом Android API.
- Автоматический статус Android имеет безусловный приоритет при изменении режима на `ENABLED`, `DISABLED` или `RESTRICTED`.
- Метка привязана к конкретному **service ID**, срок действия 7 дней; обновление пакета сбрасывает её, также есть ручной сброс.
- Для явно доступной службы исчезает мастер Restricted Settings — остаётся обычное включение через настройки.
- Для отмеченной как ограниченной остаётся инструкция по системному подтверждению, без автоматической выдачи разрешений.
- Системные службы скрыты под отдельной кнопкой «Показать системные службы (N)».
- Пользовательские приложения показываются первыми, элементы списка компактнее, диагностика перенесена ниже.
- Приложение не получает права `WRITE_SECURE_SETTINGS`, не выдаёт Accessibility, не изменяет AppOps и не обходит Enhanced Confirmation Mode.

## Тестирование

- `AccessEvidenceTest`: обработка доверенной/ручной информации, инвалидация по времени и обновлению, приоритет системы, без ложных разрешений.
- Предыдущие проверки `AccessStatusTest`, `AccessRefreshCoordinatorTest`, `AccessRefreshSummaryTest`, `AccessDiagnosticsTest` продолжают выполняться в CI.
- Signed Release через прежний ключ `dev.legion.control`, versionCode **18**, versionName **1.8.0-beta9**.

## Что ещё не подтверждено

Ручная отметка **не заменяет автоматическую диагностику** и может устареть после изменения разрешений. На Android 17 получить точное состояние ECM из обычного публичного API для произвольного приложения нельзя. Для реализации автоматического определения нужна отдельная read-only проверка через Shizuku на реальном устройстве с подтверждением, что конкретная прошивка раскрывает необходимые сведения.

Физическое тестирование Beta 9 на Lenovo Y70 ещё не выполнено. Для публикации 4PDA необходимы скриншоты именно новой версии и smoke-тестирование.
