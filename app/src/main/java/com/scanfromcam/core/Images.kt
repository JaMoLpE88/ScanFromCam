package com.scanfromcam.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

object Images {

    /** Decodes a JPEG applying its EXIF orientation, optionally downsampled. */
    fun decodeUpright(file: File, maxSide: Int = 0): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply {
            inSampleSize = if (maxSide > 0) sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide) else 1
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        val rotation = exifRotation(file)
        return if (rotation == 0f) decoded else rotate(decoded, rotation)
    }

    fun decodeUpright(bytes: ByteArray, maxSide: Int = 0): Bitmap? {
        val tmp = File.createTempFile("decode", ".jpg")
        return try {
            tmp.writeBytes(bytes)
            decodeUpright(tmp, maxSide)
        } finally {
            tmp.delete()
        }
    }

    fun exifRotation(file: File): Float = try {
        when (ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = Matrix().apply { postRotate(degrees) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (out != src) src.recycle()
        return out
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = 92) {
        file.parentFile?.mkdirs()
        ByteArrayOutputStream(1 shl 16).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            file.writeBytes(out.toByteArray())
        }
    }

    fun jpegBytes(bitmap: Bitmap, quality: Int = 92): ByteArray =
        ByteArrayOutputStream(1 shl 16).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= maxSide) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
