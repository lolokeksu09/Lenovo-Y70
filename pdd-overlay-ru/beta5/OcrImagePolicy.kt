package app.pinduoduo.overlayru

import kotlin.math.max
import kotlin.math.roundToInt

/** Resource bounds shared by the Android image decoder and JVM regression tests. */
internal object OcrImagePolicy {
    const val MAX_EDGE = 2560
    const val MAX_ENCODED_BYTES = 24 * 1024 * 1024
    const val PREVIEW_EDGE = 620

    fun targetSize(width: Int, height: Int, edge: Int = MAX_EDGE): Pair<Int, Int>? {
        if (width <= 0 || height <= 0 || edge <= 0) return null
        val longer = max(width, height)
        if (longer <= edge) return width to height
        val ratio = edge.toDouble() / longer.toDouble()
        return (width * ratio).roundToInt().coerceAtLeast(1) to
            (height * ratio).roundToInt().coerceAtLeast(1)
    }

    /** BitmapFactory's power-of-two inSampleSize. Never over-allocates the target edge. */
    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (max(width.toLong(), height.toLong()) / sample > MAX_EDGE) {
            if (sample >= 1 shl 29) break
            sample *= 2
        }
        return sample
    }

    fun withinEncodedLimit(byteCount: Long): Boolean =
        byteCount >= 0L && byteCount <= MAX_ENCODED_BYTES.toLong()
}
