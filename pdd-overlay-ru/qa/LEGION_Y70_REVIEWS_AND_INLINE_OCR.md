# Lenovo Y70 acceptance: reviews and inline photo OCR — 2026-11-16

User-reported, visibly observed on an actual Pinduoduo product-review screen:
- A large English prose review about the condition of shipped kiwifruit stays in English.
- Several short Russian overlay labels are drawn on product details and thumbnails; some reduce readability.
- User also reports that translated OCR output is not substituted at the original text position in the picked photo.

## Evidence distinction

The screenshot verifies the untranslated long English paragraph and current overlay appearance, **not** whether the English-to-Russian ML Kit model was downloaded or whether every review is exposed in the AccessibilityNodeInfo tree.

Code confirms two independent functional gaps:
1. `TranslationPolicy.shouldTranslate` rejects more than 180 characters and `sourceLanguage` rejects Unicode punctuation in English. `PddAccessibilityService.findText` also currently accepts only short single-line source text.
2. `OcrImageViewer` outlines `OcrRegion` but never renders the Russian text inside the selected region; the result remains a separate bottom panel.

## Acceptance (no release until verified)

1. Split a synthetic 300–900 character English review into <=180-character chunks only when the user separately consented to English model download and the model is ready. Never translate payment/OTP/contact/price fields.
2. Join all successfully translated parts, show as a multiline caption inside the original paragraph, and preserve existing short controls. Do not shorten the original review to misleading fragments; if translation is incomplete keep original visible.
3. For OCR-selected photos, default to overlaying translated text within eligible recognized boxes, with a visible, usable control to show the unaltered original. If a box is too small, keep the original and allow the detail panel; no cropped translation misrepresented as complete.
4. Verify Android 35 on-device ML Kit recognition with synthetic labels, controls and screen capture. Verify ARM64 release build and regression suite.
5. Validate on the physical Lenovo Y70, including model consent and enabled Accessibility. No claim about inaccessible Pinduoduo Canvas text before real-device verification.

All work remains local/on-device; no automatic screenshot capture, network upload, hidden model downloads, or payment-control replacement.
