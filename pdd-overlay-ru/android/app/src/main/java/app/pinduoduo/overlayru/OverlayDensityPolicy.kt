package app.pinduoduo.overlayru

/**
 * Pure, deterministic guard for dense third-party Accessibility layouts.
 * Positions are screen pixels; this class does not inspect, copy or log text.
 * Review paragraphs get the first opportunity to occupy their native space.
 */
internal object OverlayDensityPolicy {
    const val MAX_CAPTIONS = 12
    private const val MAX_PER_BAND = 2

    data class Candidate(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val isReview: Boolean
    )

    /**
     * Returns original indices in paint priority order (reviews first).
     * Avoid system header, sticky shop footer, overpopulated rows and any
     * candidate extending outside its safe viewport. Never move labels.
     */
    fun select(
        candidates: List<Candidate>,
        viewportWidth: Int,
        viewportHeight: Int,
        safeTopPx: Int,
        safeBottomPx: Int,
        bandHeightPx: Int
    ): List<Int> {
        if (viewportWidth <= 0 || viewportHeight <= 0 ||
            safeTopPx < 0 || safeBottomPx < 0 || bandHeightPx <= 0 ||
            safeTopPx >= viewportHeight - safeBottomPx) return emptyList()

        val bottomLimit = viewportHeight - safeBottomPx
        val order = candidates.indices
            .filter { i ->
                val c = candidates[i]
                c.left >= 0 && c.top >= safeTopPx &&
                    c.right > c.left && c.bottom > c.top &&
                    c.right <= viewportWidth && c.bottom <= bottomLimit
            }
            .sortedWith(compareByDescending<Int> { candidates[it].isReview }
                .thenBy { candidates[it].top }.thenBy { candidates[it].left })

        val bands = HashMap<Int, Int>()
        val accepted = ArrayList<Int>(minOf(order.size, MAX_CAPTIONS))
        for (index in order) {
            if (accepted.size >= MAX_CAPTIONS) break
            val c = candidates[index]
            val startBand = c.top / bandHeightPx
            val endBand = (c.bottom - 1) / bandHeightPx
            if ((startBand..endBand).any { bands.getOrDefault(it, 0) >= MAX_PER_BAND }) {
                continue
            }
            accepted.add(index)
            for (band in startBand..endBand) {
                bands[band] = if (c.isReview) MAX_PER_BAND
                              else bands.getOrDefault(band, 0) + 1
            }
        }
        return accepted
    }
}
