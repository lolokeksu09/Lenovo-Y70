# PDD Overlay RU

**Version 0.1.9** · Standalone Russian accessibility translation overlay for the **official** Pinduoduo Android app (`com.xunmeng.pinduoduo`). Min API 26, target API 35. Source language: Chinese or English. UI: Russian.

## Features

- Does **not** modify, re-sign, intercept traffic from or hook the original Pinduoduo APK.
- Displays local Russian captions over visible Pinduoduo text using `AccessibilityService` and non-touchable `TYPE_ACCESSIBILITY_OVERLAY` windows.
- Uses a 484-entry Chinese glossary and a curated English settings glossary before on-device ML Kit (`com.google.mlkit:translate:17.0.3`).
- Separate, opt-in Chinese→Russian and English→Russian offline-model downloads, over Wi-Fi or mobile data.
- Compact draggable **РУ** floating control: 36dp visible disc, 48dp touch area, snaps to nearest screen edge, Pinduoduo-only.
- Reuses existing caption views across accessibility events to reduce flicker. Source-anchored placement limits expansion into adjacent controls/rows.
- AMOLED-first settings UI with primary Pinduoduo launch, translation controls, model actions and collapsible technical details.
- Shows a **possible** VPN interference warning when the active network is a VPN and a translation model is not ready. Does not claim to diagnose the specific Google server route.
- Shizuku is **not required** and shell permissions are never requested by the app.

## Setup

1. Install the signed release APK. To update an existing version **the signing certificate must match** the previous APK.
2. Open **PDD · Русский**, enable **PDD · Перевод экрана** in Android Accessibility settings. On some Android versions, sideloaded apps require manually allowing **Restricted settings** on the app info page.
3. Enable translation and, if needed, download the Chinese→Russian and English→Russian ML Kit language models separately. Initial downloads may use tens of MB of data. If a model task gets stuck through a VPN (previously observed with Material Xray), temporarily turn the VPN off **only for the initial model download**, then restore it afterward.
4. Open official Pinduoduo using the primary action. Press **РУ** to toggle Russian captions or drag it to an edge. Toggle the floating button off from the helper app if unwanted.

## Privacy / safety

- The Accessibility permission is sensitive and system-wide. The service receives some system events, but processing is restricted to the active Pinduoduo window; password nodes, editable fields, recognized verification codes and monetary amounts are skipped.
- Captions use a non-touchable overlay; only the floating button receives touch events. No screen text is sent to a custom server; ML Kit translation runs locally after models have been obtained from Google.
- This is an **assistive overlay** and **not** an authoritative translation of payment, legal, address or order information. Inspect the original when making a transaction.

## Limits

- No OCR; text embedded in product images/canvas and inaccessible WebView text cannot be translated.
- The English router intentionally skips unknown one-word English labels and mixed-script strings; some text remains original.
- Long translated captions may be ellipsized rather than cover unrelated controls; users can switch translations off instantly.
- ML Kit download tasks are not cancelable. Single-flight guards avoid duplicate tasks; model inspection and timeouts report stuck operations without pretending they were canceled.
- Android compilation and local tests do not prove real-device overlay alignment, device compatibility, or model CDN reachability.

## Build and tests

JDK 17, Gradle 8.10.2, Android SDK API 35:

```sh
bash scripts/test_local.sh
gradle :app:assembleRelease --no-daemon
```

Builds are also performed via GitHub Actions in the isolated `pdd-overlay-ru-build-20261114` branch of `lolokeksu09/Lenovo-Y70`. The normal unsigned release artifact **cannot** update an installed signed release. Signing uses a separate, offline keystore; **never add signing secrets to source control**.

## Version 0.1.9

- Stable caption view reconciliation replaces full remove-and-recreate cycles.
- Source-row-constrained geometry reduces overlaps with trailing actions and adjacent rows.
- Smaller visual floating button with a 48dp accessible hit target, snap-to-edge and periodic foreground check.
- Clearer AMOLED UI with short sections, primary open-app CTA, checked-state switch accents and collapsible diagnostics.
- VPN warning when translation models are missing and an active VPN is detected.
- Updated source layout and geometry regression tests.

## Known remaining risks

Device smoke tests still needed: switches/Accessibility reconnect, rotating screen, overlays over lists with prices, two-language offline translation and toggling Material Xray. No OCR and no exact per-element hitbox obstruction analysis. See `docs/AUDIT_0.1.9_RU.md`.


## PRO лицензия (пилотный клиент v0.1.9)

Добавлен отдельный экран PRO и серверное лицензирование. Состояние PRO **не влияет** на существующий перевод китайский/английский → русский, плавающую кнопку или загрузку ML Kit. Если сервер ещё не развёрнут, экран сообщает, что PRO недоступен, и не предлагает мнимую активацию.

Для сборки настоящего клиента с работающей активацией задаются **два публичных параметра** (не приватные ключи):

- `PDD_LICENSE_API_BASE_URL`: HTTPS URL доступного сервера без конечного `/`, например `https://license.example.org` (пример, не действующий адрес).
- `PDD_LICENSE_PUBLIC_KEY_B64`: Base64 DER-encoding X.509 SubjectPublicKeyInfo RSA **публичного** ключа из той же пары, что подписывает JWT на VDS. Пример получения из `public.pem`: `openssl pkey -pubin -in public.pem -outform DER | base64 -w0`.

`app/build.gradle` отвергает частичную настройку. Отсутствие обоих параметров допускается для демонстрации UI/сохранения совместимости, но запросы на активацию в такой сборке отключены. В APK не должны попадать приватный ключ, pepper, пароли и платежные секреты.

Контракт API: `POST /v1/activate` `{key, install_id}` и `POST /v1/renew` `{token, install_id}`; полученный JWT проверяется офлайн `SHA256withRSA` с точными iss, aud, installation UUIDv4, планом, временем и пределом аренды 7 дней. Отзыв действует при следующем онлайн-продлении либо при истечении сохранённого офлайн-разрешения. Код активации не сохраняется и не журналируется. Данные экранов не передаются.

Для проверки клиента в GitHub Actions: `gradle -p pinduoduo_overlay_ru :app:testReleaseUnitTest :app:assembleRelease`. Тест подписи JWT использует собственную временную ключевую пару в тестовом процессе, не production-ключ.

**Границы:** URL production API и публичный ключ не известны — реальная активация и запуск сервера на VDS не проверялись. Оплата/тарифы не подключены. Не продавать будущие функции как реализованные.
