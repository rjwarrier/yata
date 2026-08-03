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
    private const val AVATAR_DIR = "avatars"
    private const val MAX_DECODE_DIMENSION = 1600
    private const val MATERIAL_GLYPH_QUERY_PARAM = "m3Glyph"

    /**
     * Whether [uriString] points at a transparent glyph that should be colored at render time.
     * The PNG remains transparent so wallpaper-driven Material colors can change independently
     * of the stored image bytes.
     */
    fun isMaterialGlyphUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            Uri.parse(uriString).getQueryParameter(MATERIAL_GLYPH_QUERY_PARAM) == "1"
        } catch (_: Exception) {
            false
        }
    }

    /** Adds the render-time Material glyph marker used by avatar Uris and backup restore. */
    fun withMaterialGlyphFlag(uri: Uri, isMaterialGlyph: Boolean): Uri =
        if (isMaterialGlyph) {
            uri.buildUpon().appendQueryParameter(MATERIAL_GLYPH_QUERY_PARAM, "1").build()
        } else {
            uri
        }

    /** Downsampled decode so a multi-megapixel photo doesn't blow up memory in the cropper. */
    fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int = MAX_DECODE_DIMENSION): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream always returns null when inJustDecodeBounds is set — it only mutates
        // `bounds` as a side effect. Check stream availability separately, not its return value.
        val boundsStream = resolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    /** Masks a square bitmap into a circle with transparent corners. */
    private fun maskCircular(squareBitmap: Bitmap): Bitmap {
        val size = minOf(squareBitmap.width, squareBitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val rect = Rect(0, 0, size, size)
        canvas.drawBitmap(squareBitmap, rect, rect, paint)
        return output
    }

    /**
     * Masks a square bitmap into a circle and writes it as the app's single profile photo file,
     * overwriting any previous one. The returned Uri has a cache-busting query param so avatar
     * widgets keyed on the Uri string reload it.
     */
    fun saveCircularProfilePhoto(
        context: Context,
        squareBitmap: Bitmap,
        isMaterialGlyph: Boolean = false
    ): Uri {
        val output = maskCircular(squareBitmap)
        val file = File(context.filesDir, FILE_NAME)
        FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }

        val cacheBustedUri = Uri.fromFile(file).buildUpon()
            .appendQueryParameter("t", System.currentTimeMillis().toString())
            .build()
        return withMaterialGlyphFlag(cacheBustedUri, isMaterialGlyph)
    }

    /**
     * Masks a cropped square bitmap into a circle and writes it to a uniquely-named file under
     * filesDir/avatars, returning a stable file:// Uri. Used for per-person avatars.
     *
     * A picked image must not be persisted as the Photo Picker's raw content:// Uri: that grant
     * is a *one-time* process-scoped read (takePersistableUriPermission is unsupported for it), so
     * the reference goes dead on relaunch and the avatar silently reverts to initials. Writing the
     * bytes into an owned file fixes that. Unlike [saveCircularProfilePhoto]'s single fixed file,
     * each call gets a unique name so multiple people don't overwrite one another.
     */
    fun saveCircularAvatar(
        context: Context,
        squareBitmap: Bitmap,
        isMaterialGlyph: Boolean = false
    ): Uri {
        val output = maskCircular(squareBitmap)
        val dir = File(context.filesDir, AVATAR_DIR).apply { mkdirs() }
        val file = File(dir, "avatar_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.png")
        FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return withMaterialGlyphFlag(Uri.fromFile(file), isMaterialGlyph)
    }

    /**
     * True when [bitmap] looks like a white/near-white glyph on a transparent background — a
     * logomark exported as a transparent PNG (an SVG can't be decoded by [decodeSampledBitmap] at
     * all, since [BitmapFactory] only reads raster formats; a transparent PNG is the export that
     * actually reaches here) — rather than a photo. A real photo essentially never has meaningful
     * transparency; this only needs to be roughly right, not exact.
     *
     * Sampled on a coarse grid rather than every pixel — this runs on a full-resolution crop, and
     * a rough read is all the caller needs to decide whether to recolor it.
     */
    fun looksLikeTransparentGlyph(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return false
        val stepX = maxOf(1, width / 64)
        val stepY = maxOf(1, height / 64)

        var sampled = 0
        var transparent = 0
        var opaqueNearWhite = 0
        var opaqueOther = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                sampled++
                if ((pixel ushr 24) and 0xFF < 32) {
                    transparent++
                } else {
                    val r = (pixel ushr 16) and 0xFF
                    val g = (pixel ushr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (r > 200 && g > 200 && b > 200) opaqueNearWhite++ else opaqueOther++
                }
                x += stepX
            }
            y += stepY
        }
        if (sampled == 0) return false

        val opaqueCount = opaqueNearWhite + opaqueOther
        // Needs real transparency — a photo rarely has any at all — and, of whatever isn't
        // transparent, needs to be overwhelmingly near-white: a few anti-aliased edge pixels are
        // fine, a photo's mix of colors is not.
        return transparent.toFloat() / sampled > 0.15f &&
            opaqueCount > 0 &&
            opaqueNearWhite.toFloat() / opaqueCount > 0.85f
    }

}
