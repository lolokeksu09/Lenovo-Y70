package app.pinduoduo.overlayru

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException

internal enum class OcrOpenError {
    PERMISSION, UNAVAILABLE, TOO_LARGE, INVALID_IMAGE
}

internal sealed class OcrImageOpenResult {
    data class Ready(val full: Bitmap, val preview: Bitmap) : OcrImageOpenResult()
    data class Failed(val error: OcrOpenError) : OcrImageOpenResult()
}

/**
 * Gallery/SAF content URI decoder. Modern Android photo providers may expose
 * non-seekable or one-shot streams; the old BitmapFactory two-pass decoder would
 * silently return null. Try ImageDecoder's URI-aware source first. If the source
 * codec/provider fails, fall back to one bounded read + decodeByteArray.
 *
 * No photo data, path, or URI is logged or sent to a server.
 */
internal object OcrImageOpener {
    fun open(context: Context, uri: Uri): OcrImageOpenResult {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return OcrImageOpenResult.Failed(OcrOpenError.INVALID_IMAGE)
        }

        val resolver = context.contentResolver
        try {
            // The provider may not report SIZE (null/-1). Do not reject those
            // valid virtual documents; the buffered fallback has its own cap.
            val declaredSize = try {
                resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                }
            } catch (_: RuntimeException) { null }
            if (declaredSize != null && declaredSize > OcrImagePolicy.MAX_ENCODED_BYTES) {
                return OcrImageOpenResult.Failed(OcrOpenError.TOO_LARGE)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val source = ImageDecoder.createSource(resolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val target = OcrImagePolicy.targetSize(
                            info.size.width, info.size.height
                        ) ?: throw IOException("Invalid image dimensions")
                        decoder.setTargetSize(target.first, target.second)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    return ready(bitmap)
                } catch (_: SecurityException) {
                    return OcrImageOpenResult.Failed(OcrOpenError.PERMISSION)
                } catch (_: OutOfMemoryError) {
                    return OcrImageOpenResult.Failed(OcrOpenError.TOO_LARGE)
                } catch (_: Exception) {
                    // An alternate/virtual Android document provider may only
                    // support a bounded single stream read. Try that once.
                }
            }

            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(16 * 1024)
                val output = ByteArrayOutputStream()
                var total = 0L
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    total += size.toLong()
                    if (!OcrImagePolicy.withinEncodedLimit(total)) {
                        return OcrImageOpenResult.Failed(OcrOpenError.TOO_LARGE)
                    }
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            } ?: return OcrImageOpenResult.Failed(OcrOpenError.UNAVAILABLE)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            OcrImagePolicy.targetSize(bounds.outWidth, bounds.outHeight)
                ?: return OcrImageOpenResult.Failed(OcrOpenError.INVALID_IMAGE)

            val options = BitmapFactory.Options().apply {
                inSampleSize = OcrImagePolicy.sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: return OcrImageOpenResult.Failed(OcrOpenError.INVALID_IMAGE)
            return ready(bitmap)
        } catch (_: SecurityException) {
            return OcrImageOpenResult.Failed(OcrOpenError.PERMISSION)
        } catch (_: FileNotFoundException) {
            return OcrImageOpenResult.Failed(OcrOpenError.UNAVAILABLE)
        } catch (_: OutOfMemoryError) {
            return OcrImageOpenResult.Failed(OcrOpenError.TOO_LARGE)
        } catch (_: IOException) {
            return OcrImageOpenResult.Failed(OcrOpenError.UNAVAILABLE)
        } catch (_: RuntimeException) {
            return OcrImageOpenResult.Failed(OcrOpenError.INVALID_IMAGE)
        }
    }

    private fun ready(bitmap: Bitmap): OcrImageOpenResult {
        try {
            val size = OcrImagePolicy.targetSize(
                bitmap.width, bitmap.height, OcrImagePolicy.PREVIEW_EDGE
            ) ?: throw IllegalStateException("Decoded bitmap has no dimensions")

            // Always create a separate preview. ML Kit can release the full
            // bitmap while Compose continues rendering the preview.
            val preview = if (size.first == bitmap.width && size.second == bitmap.height) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                Bitmap.createScaledBitmap(bitmap, size.first, size.second, true)
            }
            return OcrImageOpenResult.Ready(bitmap, preview)
        } catch (error: OutOfMemoryError) {
            bitmap.recycle()
            return OcrImageOpenResult.Failed(OcrOpenError.TOO_LARGE)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            return OcrImageOpenResult.Failed(OcrOpenError.INVALID_IMAGE)
        }
    }
}
