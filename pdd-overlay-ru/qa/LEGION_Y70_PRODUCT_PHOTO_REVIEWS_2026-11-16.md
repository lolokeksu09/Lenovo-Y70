# Lenovo Y70: three supplied screenshots, Nov 16 2026

## Observed (user screenshots, not committed to GitHub)

1. A selected product-photo screenshot with antifreeze windshield washer bottles has
   **28 recognized OCR regions** and *no visible Russian replacement* on the bottle labels.
   The red borders indicate OCR detections; they did not prove successful translation.
   The small `РУ` bubble and phone status strip are original pixels *inside* the chosen screenshot.
2. The first kiwi review is translated in Russian, but its source-height opaque
   caption is taller than the translated paragraph, creating a large empty background.
3. Later English reviews containing `👍` and `crème` are not translated. Source
   `TranslationPolicy.sourceLanguage` rejected supplementary emoji and accented Latin.

## Changes in PR #24

- Unicode codepoint-safe English classification with accents, emoji and flag glyphs,
  retaining Cyrillic/Arabic/currency rejection and existing payment/OTP protections.
- Review text font can grow to 20sp if the complete translation fits inside its
  original paragraph rectangle; vertical centering prevents text stuck at the top.
- OCR viewer separates recognized from translated regions and does not blanket
  the photo with 28 red boxes. When zero Russian translations exist, it presents
  that fact clearly rather than claiming translated captions.
- Image-screen readiness checks Chinese and English models independently. An
  available English model does not imply Chinese→Russian readiness.
- Six exact Chinese washer-label strings bundled into local, user-overridable
  glossary; this is NOT equivalent to an all-products Chinese translation model.

## Remaining real-device checks

- [ ] Does Settings confirm that the Chinese→Russian model is downloaded
      with explicit user consent? If not, download through the existing app's
      Settings only. Do not request background recording or external APIs.
- [ ] With the model ready, select the same bottle screenshot and compare
      recognition count vs actual Russian translation count.
- [ ] Use the image viewer at 1x and 2–3x; tiny OCR regions may only be legible
      via selecting the area, not opaque in-image replacement.
- [ ] Confirm long English reviews with `👍` or accented words become Russian
      if English model is separately consented/downloaded AND Accessibility
      exposes the review text. Otherwise identify the missing boundary.
- [ ] Check paragraph readability and preserved buy/price buttons at font scale
      1.0 and 1.3 on physical Lenovo Legion Y70.
- [ ] Device install/update over same-signer beta11 without data loss.

No user screenshots, avatars, customer text or order information added to repo.
