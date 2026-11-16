package app.pinduoduo.overlayru

import org.junit.Assert.*
import org.junit.Test

class ReviewTranslationPolicyTest {
    private val longReview =
        "I took a photo of the package upon receiving it. " +
        "The kiwifruits in the box are quite uniform in size and protected by foam padding—" +
        "none of them have dents or bruises. The skin looks fresh with intact fuzz. " +
        "They feel moderately firm when squeezed, and I have bought from this seller before. " +
        "The package arrived in good condition and nothing was broken or damaged."

    @Test fun splitsWholeEnglishReviewWithoutLosingWords() {
        val chunks = ReviewTranslationPolicy.chunks(longReview)!!
        assertTrue(chunks.size in 2..10)
        assertTrue(chunks.all { it.length <= 180 })
        assertEquals(longReview, chunks.joinToString(" "))
    }

    @Test fun doesNotSplitRussianOrMixedScriptReviews() {
        assertNull(ReviewTranslationPolicy.chunks("Нормальная доставка и вкусные фрукты. ".repeat(9)))
        assertNull(ReviewTranslationPolicy.chunks("Хорошие киви; excellent delivery and seller. ".repeat(8)))
    }

    @Test fun neverCoversReviewWithPaymentsContactsAndOtp() {
        assertNull(ReviewTranslationPolicy.chunks(longReview + " Total: ¥99"))
        assertNull(ReviewTranslationPolicy.chunks(longReview + " Contact: me@example.com"))
        assertNull(ReviewTranslationPolicy.chunks(longReview + " Confirmation 453278"))
    }

    @Test fun longAccentedReviewWithEmojiGetsEveryWordTranslated() {
        val text = "These crème-green kiwifruits are delicious—and fresh! " +
            "Every bite is sweet and the packaging is excellent. " +
            "The texture is soft, the balance is perfect, and delivery was quick. " +
            "I bought from the seller before and will happily buy again! 👍"
        val chunks = ReviewTranslationPolicy.chunks(text)
        assertNotNull(chunks)
        assertEquals(text, chunks!!.joinToString(" "))
        assertTrue(chunks.all { it.length <= 180 })
    }

    @Test fun rejectsOversizeAndUnboundedSingleTokens() {
        assertNull(ReviewTranslationPolicy.chunks("pleasant packaging ".repeat(100)))
        assertNull(ReviewTranslationPolicy.chunks("a".repeat(170) + " " + longReview))
    }

    @Test fun shortLabelsStayOnExistingTranslationPath() {
        assertNull(ReviewTranslationPolicy.chunks("Shipping options"))
        assertTrue(TranslationPolicy.shouldTranslate("Shipping options", false, false))
    }
}
