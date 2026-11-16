# Lenovo Legion Y70 — overlay density and long-review acceptance

Scope: PDD Overlay RU over official Pinduoduo, only after the user explicitly enables Accessibility. This is a **manual phone verification**, not an Android-emulator or proof of Chinese→Russian translation-model downloads.

## Before install

1. Retain the installed signed APK and existing glossary/settings; **do not uninstall or clear data**.
2. Update in place only with an APK signed by the already verified certificate. Check package `app.pinduoduo.overlayru` and unchanged `versionCode 21`.
3. Confirm Pinduoduo is foreground and the floating `РУ` control is enabled.
4. English→Russian model is separate from Chinese→Russian: check consent, download and readiness in the app's **Настройки**. Do not infer model readiness from a short bundled-dictionary translation.

## Tests

| Scenario | Expected outcome | Real Lenovo |
| --- | --- | --- |
| Review page with many short labels | At most 12 visible translated captions, at most two in each ~84dp vertical band | NOT RUN |
| English long review near other chips | Full review has priority if fully translated and can fit inside its original rectangle; neighboring chips are omitted instead of covering the paragraph | NOT RUN |
| Pinduoduo sticky footer / top system area | No translation caption obscures the lower ~104dp area or top ~32dp | NOT RUN |
| Narrow short text box | Translation rendered in full at 10sp or larger, **or original text stays visible**; never a misleading ellipsis | NOT RUN |
| Original review with loaded English model | Real long English review becomes complete Russian if exposed as an Accessibility text node; an absent model is reported, not silently downloaded | NOT RUN |
| Scroll and leave Pinduoduo | Overlay positions follow visible nodes; captions disappear outside Pinduoduo | NOT RUN |
| Price, checkout, refund, OTP | Never obscured by translated captions | NOT RUN |
| OCR from gallery | Select photo, view Russian text on its image and toggle back to original without changing pixels | Prior Android 35 emulator PASS, phone NOT RUN |

## Limitations and privacy

- Pinduoduo may render some review text in a WebView or Canvas that does not expose `AccessibilityNodeInfo.text`. The overlay cannot translate inaccessible content without a **separate, explicitly initiated** on-device screenshot/OCR flow. No automatic screen capture is introduced here.
- User's screenshot includes customer names, avatars, product prices and messages. **Do not commit or upload raw screens or full Accessibility hierarchy dumps** to GitHub.
- If the same review remains untranslated with the English model marked ready, inspect whether its text is present as an accessible node before changing translation logic.
- ARM64 Release compilation and JVM policy tests are not a substitute for Lenovo overlay placement, model readiness, or Accessibility verification.

Reference: source implementation plan `pdd-overlay-ru/design/premium-amoled-3d/IMPLEMENTATION_PLAN.md`.