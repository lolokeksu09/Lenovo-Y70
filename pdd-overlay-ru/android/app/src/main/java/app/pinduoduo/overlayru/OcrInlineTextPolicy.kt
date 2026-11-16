package app.pinduoduo.overlayru

/** Only the user's selected photo preview may receive reversible OCR captions.
 * Never cover prices, checkout/payment actions, protected identifiers or text
 * that has no completed Russian translation. No source pixels are modified.
 */
internal object OcrInlineTextPolicy {
    fun canCover(
        source: String, translated: String?, region: OcrRegion?,
        widthPx: Float, heightPx: Float
    ): Boolean =
        region != null &&
            !translated.isNullOrBlank() &&
            translated.trim() != source.trim() &&
            widthPx.isFinite() && heightPx.isFinite() &&
            widthPx >= 48f && heightPx >= 18f &&
            OcrTextPolicy.canTranslate(source) &&
            !PaymentActionPolicy.isProtected(source)
}
