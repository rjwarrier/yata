package com.mj.yata.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ProfilePhotoUtils {
    private const val FILE_NAME = "profile_photo.png"
    private const val MAX_DECODE_DIMENSION = 1600

    /** Downsampled decode so a multi-megapixel photo doesn't blow up memory in the cropper. */
    fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream always returns null when inJustDecodeBounds is set — it only mutates
        // `bounds` as a side effect. Check stream availability separately, not its return value.
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DECODE_DIMENSION || bounds.outHeight / sampleSize > MAX_DECODE_DIMENSION) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /**
     * Masks a square bitmap into a circle (transparent corners) and writes it as the app's
     * single profile photo file, overwriting any previous one. The returned Uri has a
     * cache-busting query param so avatar widgets keyed on the Uri string reload it.
     */
    fun saveCircularProfilePhoto(context: Context, squareBitmap: Bitmap): Uri {
        val size = minOf(squareBitmap.width, squareBitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val rect = Rect(0, 0, size, size)
        canvas.drawBitmap(squareBitmap, rect, rect, paint)

        val file = File(context.filesDir, FILE_NAME)
        FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }

        return Uri.fromFile(file).buildUpon()
            .appendQueryParameter("t", System.currentTimeMillis().toString())
            .build()
    }
}
