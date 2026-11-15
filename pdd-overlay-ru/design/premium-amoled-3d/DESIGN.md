# DESIGN — PDD Premium AMOLED 3D

**Approved visual direction:** deep AMOLED, restrained translucent surfaces, vivid PDD-red highlights, quality 3D e-commerce illustrations, focused Material 3 navigation. Source of visual direction: the five images in [references](references/). The reference board is inspirational, not a literal pixel or data specification.

## Product intent

A Russian-first, offline-friendly assistant for reading Pinduoduo product interfaces and images. Main action = prepare the existing translation service and open Pinduoduo; secondary = inspect a product image; tertiary = manage local translation corrections. Do not suggest that the app purchases goods, provides a proxy, or guarantees payment accuracy.

## Palette and material

| Role | Value | Usage |
| --- | --- | --- |
| Background | `#06080D` | Root screen, OLED black atmosphere |
| Surface | `#141820` | Functional grouped content |
| Surface raised | `#202632` | Insets, selected list, lower tool surfaces |
| Outline | `#343B48` | Thin 1dp separators where needed |
| Primary text | `#F6F7FA` | Main readable copy |
| Secondary text | `#B7C0CE` | Hints, source text |
| Accent | `#F03B53` | CTA, active destination and focus |
| Accent dark | `#8C172A` | Subtle tonal depth within the CTA only |
| Ready | `#65D7A4` | Verified ready/success state |
| Warning | `#FFC46B` | Model waiting/recoverable setup |
| Error | `#FF8A9A` | Specific failure, never just a red border |

Use Material 3 semantic roles and a single `PddTheme`. Large ambient red glows are decorative image assets, never screen-sized overlapping tap targets. Flat glass effects must not obscure source Chinese text or reduce legibility. Critical actions should be 48dp+ and contrast-compliant.

## Core elements

- **Brand:** use exact name `PDD Overlay RU`; version from actual `BuildConfig.VERSION_NAME`, not artwork. Red shopping bag may appear as a **separate asset** in a home hero, not as baked UI text.
- **Navigation:** four destinations, in this order: Главная, Фото, Словарь, Настройки. On compact width use Material 3 bottom NavigationBar; larger windows may use NavigationRail. System back respects actual stack and leaves the app without trapped dialogs.
- **Typography:** Android/Material 3 titles 24/20sp, regular text 14–16sp, labels 12sp, tab labels 11–12sp. Support 1.3x font scaling; avoid fixed-height text boxes.
- **Spacing:** 4/8/12/16/24dp rhythm, 20dp screen margins, grouped panels 14–18dp radius, primary action 52–56dp, bottom bar at most 72–80dp excluding navigation inset.
- **Iconography:** internally coherent Material Symbols/vector icons at 20–24dp. Decorative 3D icons do **not** replace semantics or clickable targets.
- **Feedback:** one red main CTA, no always-red toggles. Asynchronous operations show actual progress or indeterminate state; never mark a downloaded model “Готова” merely because OCR recognition is installed.

## Screen contracts

### 01. Главная

**Top:** compact wordmark + profile/more menu only if real behavior exists.

**Hero:** “Перевод Pinduoduo”, short one-line promise, small shopping-bag visual; do not push service readiness below the fold.

**Readiness block:** real Chinese translation model, English model, Accessibility permission and Pinduoduo install state. States:
- Checking → explicit status; main action disabled while app resolves it.
- Missing permission → take user to the correct Android settings screen; deep-link to app detail where required.
- Model not present → show download by user consent; Wi-Fi-only condition visible.
- Model download in progress → no false progress percentage; explain how to retry.
- Ready → “Открыть Pinduoduo”, leave free translation accessible.
- Error → concise failure + one recovery action; log private detail only for diagnostics.

**Quick tools:** OCR and personal dictionary. Show actual local floating button state and Free/PRO without fabricated PRO benefits.

### 02. Фото / OCR

**Primary action:** choose image via system picker; no broad gallery permission. Result: preview, original Chinese plus Russian, and “Перевод на фото” with the existing interactive region viewer. Pinch zoom, double tap and bounded pan stay functional.

**Navigation:** entry from bottom bar or Home quick tool should arrive at the same screen; return state should not leak an old OCR task into a new image.

**Model states:** recognition ready without Chinese→Russian translation must not be labeled fully translated. Copy actual recognized results only; do not present fake OCR history.

**CTA copy:** “Выбрать фото”. “Сделать фото”, “Вставить из буфера”, “Недавние фото” appear **only after those actions exist and are tested**; these are concept-only elements in the generated reference.

### 03. Мой словарь

**Primary job:** add, update, delete the user's local correction. Correct handling of duplicates, invalid/sensitive phrases and the maximum number of entries. Keep existing free functionality. Search, type chips (“Товары”, “Цвета”, “Материалы”) and saved-count displays must use real local data; category chips should **not** be enabled until categories are actually stored.

**Empty state:** readable guidance + add-first-translation, not an invented demo list. Active edit form pre-fills source and translation, can cancel safely, and never changes protected payments/amounts.

### 04. Настройки

**Priority:** working service/accessibility → language model and download consent → overlay settings → network/offline/privacy → license → diagnostics (collapsed).

Use Material3 SwitchListItem/Navigation List style, clear labels and current values. Never replicate the old pile of outlined Android View buttons. Only actual toggles/switches should look interactive. A PRO badge is not a purchase flow.

## What NOT to reproduce literally from the concept

- Fake “3 saved translations”, fake “model ready”/all-green service row, fake purchases or premium entitlements.
- English “Premium” version badges or version strings baked into illustrations.
- Overly saturated red glow beneath every card, unnecessary tilt/parallax and glossy red switches for neutral settings.
- Text from image generation: reconstruct all text as native Unicode resources, verify Chinese spellings and Russian copy.
- iOS-style bar or unsupported tabs. Use Material 3 NavigationBar with Android system insets and Back handling.

## Asset policy

Committed `.webp` files are **design references**, not final app assets: they contain entire UI screenshots. Derive standalone transparent illustration and icon assets separately, review their alpha edges and pixel scaling, and only then add them to Android drawables. Do not ship screenshots as interactive screen backgrounds.

## Acceptance checklist

- Android 13 Lenovo Legion Y70/Edge 70 Max profile, portrait, font 1.0 and 1.3, system dark mode.
- Status/navigation system bars do not overlap app headers or bottom navigation; screen captures from real phone or emulator, not generated.
- Navigate Home → Photo → Dictionary → Settings → Home with one consistent navigation component.
- Trigger no permission requests for selecting an image; no network exfiltration of selected screenshots.
- Verify pending ML Kit model, missing Accessibility, offline translation, OCR recognized text and empty dictionary states using **real app state**, not mock visuals.
- Preserve `app.pinduoduo.overlayru`, ARM64-only, certificate update compatibility, private local glossary and existing license boundaries.
