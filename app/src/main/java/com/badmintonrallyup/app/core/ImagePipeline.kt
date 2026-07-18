// Every outbound photo goes through here: HEIC/PNG/whatever in, reasonably
// sized JPEG out (max 2048px, ~0.3–1MB) — safely under proxy upload limits.
// 1:1 with the iOS ImagePipeline.

package com.badmintonrallyup.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

object ImagePipeline {
    fun normalizedJpeg(data: ByteArray, maxDimension: Int = 2048, quality: Int = 80): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return null
        return normalizedJpeg(bitmap, maxDimension, quality)
    }

    fun normalizedJpeg(bitmap: Bitmap, maxDimension: Int = 2048, quality: Int = 80): ByteArray {
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
