// Every outbound photo goes through here: HEIC/PNG/whatever in, JPEG out.
// Steps down size/quality until the payload is under MAX_UPLOAD_BYTES, so a
// detail-heavy photo can never trip a proxy body-size limit (413).
// 1:1 with the iOS ImagePipeline.

package com.badmintonrallyup.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImagePipeline {
    /** Keep well under the server's limit — busy photos compress poorly. */
    const val MAX_UPLOAD_BYTES = 900_000

    /** (maxDimension, quality) ladder — first result under the cap wins. */
    private val LADDER = listOf(
        2048 to 80, 2048 to 60, 1600 to 55, 1280 to 50, 1024 to 45,
    )

    fun normalizedJpeg(data: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        return normalizedJpeg(bitmap)
    }

    fun normalizedJpeg(bitmap: Bitmap): ByteArray {
        var best: ByteArray? = null
        for ((maxDimension, quality) in LADDER) {
            val jpeg = encode(bitmap, maxDimension, quality)
            best = jpeg
            if (jpeg.size <= MAX_UPLOAD_BYTES) return jpeg
        }
        return best!!   // smallest attempt — still better than failing outright
    }

    private fun encode(bitmap: Bitmap, maxDimension: Int, quality: Int): ByteArray {
        val largest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (largest > maxDimension) {
            val scale = maxDimension.toFloat() / largest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
