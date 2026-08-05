package com.mj.yata.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ProfilePhotoUtils {
    private const val FILE_NAME = "profile_photo.png"
    private const val AVATAR_DIR = "avatars"
    private const val MAX_DECODE_DIMENSION = 1600
    private const val MATERIAL_GLYPH_QUERY_PARAM = "m3Glyph"

    enum class PresetAvatar(val label: String) {
        PERSON("Person"),
        FOCUS("Focus"),
        STAR("Star"),
        HEART("Heart"),
        ROCKET("Rocket"),
        WORK("Work"),
        LEAF("Leaf"),
        SPARK("Spark"),
        HOME("Home"),
        STUDY("Study"),
        TRAVEL("Travel"),
        FITNESS("Fitness"),
        FOOD("Food"),
        BOOK("Book"),
        MUSIC("Music"),
        CODE("Code"),
        ART("Art"),
        CAMERA("Camera"),
        IDEA("Idea"),
        SHIELD("Shield"),
        CLOUD("Cloud"),
        CHECK("Check"),
        COFFEE("Coffee"),
        CALENDAR("Calendar"),
        LOOP("Loop"),
        WAVE("Wave"),
        ORBIT("Orbit"),
        BLOOM("Bloom"),
        SMILE("Smile"),
        GLASSES("Glasses"),
        FRIENDS("Friends"),
        TEAM("Team"),
        FAMILY("Family"),
        HELPER("Helper"),
        THINKER("Thinker"),
        CHILD("Child"),
        GUIDE("Guide"),
        CREATOR("Creator"),
        LISTENER("Listener"),
        LEADER("Leader")
    }

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

    fun savePresetProfileAvatar(context: Context, preset: PresetAvatar): Uri {
        val bitmap = presetAvatarBitmap(context, preset)
        return saveCircularProfilePhoto(
            context = context,
            squareBitmap = bitmap,
            isMaterialGlyph = true
        )
    }

    fun presetAvatarBitmap(context: Context, preset: PresetAvatar): Bitmap =
        when (preset) {
            PresetAvatar.LOOP -> BitmapFactory.decodeResource(
                context.resources,
                com.mj.yata.R.drawable.avatar_white_transp
            ) ?: drawPresetGlyph(preset)
            else -> drawPresetGlyph(preset)
        }

    private fun drawPresetGlyph(preset: PresetAvatar, size: Int = 512): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val s = size.toFloat()
        fun circle(cx: Float, cy: Float, r: Float) = canvas.drawCircle(cx * s, cy * s, r * s, paint)
        fun roundRect(l: Float, t: Float, r: Float, b: Float, radius: Float) =
            canvas.drawRoundRect(RectF(l * s, t * s, r * s, b * s), radius * s, radius * s, paint)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 0.055f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = width * s
            canvas.drawLine(x1 * s, y1 * s, x2 * s, y2 * s, paint)
            paint.style = Paint.Style.FILL
        }
        fun path(block: Path.() -> Unit) {
            canvas.drawPath(Path().apply(block), paint)
        }
        fun star(cx: Float, cy: Float, outer: Float, inner: Float, points: Int = 5) {
            path {
                repeat(points * 2) { index ->
                    val radius = if (index % 2 == 0) outer else inner
                    val angle = (-90.0 + index * 180.0 / points) * Math.PI / 180.0
                    val x = (cx + kotlin.math.cos(angle).toFloat() * radius) * s
                    val y = (cy + kotlin.math.sin(angle).toFloat() * radius) * s
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }

        when (preset) {
            PresetAvatar.PERSON -> {
                circle(0.5f, 0.36f, 0.15f)
                roundRect(0.24f, 0.55f, 0.76f, 0.82f, 0.18f)
            }
            PresetAvatar.FOCUS -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.055f * s
                circle(0.5f, 0.5f, 0.28f)
                circle(0.5f, 0.5f, 0.13f)
                paint.style = Paint.Style.FILL
                circle(0.5f, 0.5f, 0.045f)
            }
            PresetAvatar.STAR -> star(0.5f, 0.5f, 0.31f, 0.13f)
            PresetAvatar.HEART -> {
                path {
                    moveTo(0.5f * s, 0.78f * s)
                    cubicTo(0.18f * s, 0.56f * s, 0.18f * s, 0.32f * s, 0.36f * s, 0.28f * s)
                    cubicTo(0.46f * s, 0.26f * s, 0.5f * s, 0.34f * s, 0.5f * s, 0.34f * s)
                    cubicTo(0.5f * s, 0.34f * s, 0.54f * s, 0.26f * s, 0.64f * s, 0.28f * s)
                    cubicTo(0.82f * s, 0.32f * s, 0.82f * s, 0.56f * s, 0.5f * s, 0.78f * s)
                    close()
                }
            }
            PresetAvatar.ROCKET -> {
                path {
                    moveTo(0.5f * s, 0.15f * s)
                    cubicTo(0.68f * s, 0.28f * s, 0.66f * s, 0.58f * s, 0.55f * s, 0.72f * s)
                    lineTo(0.45f * s, 0.72f * s)
                    cubicTo(0.34f * s, 0.58f * s, 0.32f * s, 0.28f * s, 0.5f * s, 0.15f * s)
                    close()
                }
                roundRect(0.34f, 0.59f, 0.45f, 0.79f, 0.04f)
                roundRect(0.55f, 0.59f, 0.66f, 0.79f, 0.04f)
                circle(0.5f, 0.39f, 0.055f)
            }
            PresetAvatar.WORK -> {
                roundRect(0.22f, 0.34f, 0.78f, 0.75f, 0.06f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.42f, 0.26f, 0.58f, 0.38f, 0.03f)
                paint.xfermode = null
            }
            PresetAvatar.LEAF -> {
                path {
                    moveTo(0.25f * s, 0.66f * s)
                    cubicTo(0.34f * s, 0.28f * s, 0.68f * s, 0.18f * s, 0.78f * s, 0.22f * s)
                    cubicTo(0.8f * s, 0.54f * s, 0.56f * s, 0.82f * s, 0.25f * s, 0.66f * s)
                    close()
                }
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.04f * s
                canvas.drawLine(0.33f * s, 0.62f * s, 0.67f * s, 0.33f * s, paint)
                paint.style = Paint.Style.FILL
            }
            PresetAvatar.SPARK -> {
                star(0.5f, 0.46f, 0.29f, 0.08f, points = 4)
                star(0.72f, 0.72f, 0.1f, 0.035f, points = 4)
            }
            PresetAvatar.HOME -> {
                path {
                    moveTo(0.18f * s, 0.48f * s)
                    lineTo(0.5f * s, 0.2f * s)
                    lineTo(0.82f * s, 0.48f * s)
                    lineTo(0.73f * s, 0.48f * s)
                    lineTo(0.73f * s, 0.78f * s)
                    lineTo(0.27f * s, 0.78f * s)
                    lineTo(0.27f * s, 0.48f * s)
                    close()
                }
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.44f, 0.58f, 0.56f, 0.78f, 0.02f)
                paint.xfermode = null
            }
            PresetAvatar.STUDY -> {
                path {
                    moveTo(0.18f * s, 0.38f * s)
                    lineTo(0.5f * s, 0.24f * s)
                    lineTo(0.82f * s, 0.38f * s)
                    lineTo(0.5f * s, 0.52f * s)
                    close()
                }
                roundRect(0.3f, 0.52f, 0.7f, 0.66f, 0.04f)
                line(0.76f, 0.42f, 0.76f, 0.66f, width = 0.035f)
                circle(0.76f, 0.7f, 0.035f)
            }
            PresetAvatar.TRAVEL -> {
                path {
                    moveTo(0.48f * s, 0.17f * s)
                    lineTo(0.58f * s, 0.17f * s)
                    lineTo(0.56f * s, 0.47f * s)
                    lineTo(0.8f * s, 0.62f * s)
                    lineTo(0.8f * s, 0.72f * s)
                    lineTo(0.55f * s, 0.62f * s)
                    lineTo(0.52f * s, 0.82f * s)
                    lineTo(0.44f * s, 0.82f * s)
                    lineTo(0.41f * s, 0.62f * s)
                    lineTo(0.2f * s, 0.72f * s)
                    lineTo(0.2f * s, 0.62f * s)
                    lineTo(0.43f * s, 0.47f * s)
                    close()
                }
            }
            PresetAvatar.FITNESS -> {
                roundRect(0.16f, 0.42f, 0.28f, 0.62f, 0.04f)
                roundRect(0.72f, 0.42f, 0.84f, 0.62f, 0.04f)
                roundRect(0.28f, 0.46f, 0.38f, 0.58f, 0.03f)
                roundRect(0.62f, 0.46f, 0.72f, 0.58f, 0.03f)
                roundRect(0.36f, 0.49f, 0.64f, 0.55f, 0.03f)
            }
            PresetAvatar.FOOD -> {
                roundRect(0.5f, 0.3f, 0.62f, 0.8f, 0.04f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.54f, 0.36f, 0.58f, 0.6f, 0.02f)
                paint.xfermode = null
                roundRect(0.28f, 0.28f, 0.34f, 0.8f, 0.03f)
                roundRect(0.22f, 0.28f, 0.4f, 0.34f, 0.02f)
                roundRect(0.22f, 0.38f, 0.4f, 0.44f, 0.02f)
            }
            PresetAvatar.BOOK -> {
                roundRect(0.24f, 0.24f, 0.5f, 0.78f, 0.04f)
                roundRect(0.5f, 0.24f, 0.76f, 0.78f, 0.04f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.47f, 0.28f, 0.53f, 0.76f, 0.02f)
                paint.xfermode = null
            }
            PresetAvatar.MUSIC -> {
                roundRect(0.58f, 0.2f, 0.66f, 0.66f, 0.03f)
                roundRect(0.36f, 0.28f, 0.44f, 0.72f, 0.03f)
                roundRect(0.42f, 0.2f, 0.66f, 0.3f, 0.03f)
                circle(0.34f, 0.74f, 0.1f)
                circle(0.56f, 0.68f, 0.1f)
            }
            PresetAvatar.CODE -> {
                line(0.36f, 0.34f, 0.2f, 0.5f, width = 0.06f)
                line(0.2f, 0.5f, 0.36f, 0.66f, width = 0.06f)
                line(0.64f, 0.34f, 0.8f, 0.5f, width = 0.06f)
                line(0.8f, 0.5f, 0.64f, 0.66f, width = 0.06f)
                line(0.56f, 0.28f, 0.44f, 0.72f, width = 0.055f)
            }
            PresetAvatar.ART -> {
                circle(0.48f, 0.5f, 0.27f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.62f, 0.64f, 0.08f)
                paint.xfermode = null
                circle(0.38f, 0.38f, 0.04f)
                circle(0.52f, 0.34f, 0.04f)
                circle(0.32f, 0.52f, 0.04f)
            }
            PresetAvatar.CAMERA -> {
                roundRect(0.2f, 0.36f, 0.8f, 0.74f, 0.07f)
                roundRect(0.34f, 0.28f, 0.52f, 0.39f, 0.04f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.5f, 0.55f, 0.11f)
                paint.xfermode = null
            }
            PresetAvatar.IDEA -> {
                circle(0.5f, 0.38f, 0.18f)
                roundRect(0.39f, 0.55f, 0.61f, 0.66f, 0.04f)
                roundRect(0.42f, 0.69f, 0.58f, 0.78f, 0.035f)
            }
            PresetAvatar.SHIELD -> {
                path {
                    moveTo(0.5f * s, 0.18f * s)
                    lineTo(0.76f * s, 0.29f * s)
                    cubicTo(0.74f * s, 0.58f * s, 0.66f * s, 0.73f * s, 0.5f * s, 0.84f * s)
                    cubicTo(0.34f * s, 0.73f * s, 0.26f * s, 0.58f * s, 0.24f * s, 0.29f * s)
                    close()
                }
            }
            PresetAvatar.CLOUD -> {
                circle(0.38f, 0.55f, 0.14f)
                circle(0.52f, 0.45f, 0.18f)
                circle(0.66f, 0.57f, 0.13f)
                roundRect(0.28f, 0.55f, 0.74f, 0.72f, 0.08f)
            }
            PresetAvatar.CHECK -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.085f * s
                val check = Path().apply {
                    moveTo(0.25f * s, 0.52f * s)
                    lineTo(0.43f * s, 0.68f * s)
                    lineTo(0.76f * s, 0.32f * s)
                }
                canvas.drawPath(check, paint)
                paint.style = Paint.Style.FILL
            }
            PresetAvatar.COFFEE -> {
                roundRect(0.28f, 0.38f, 0.64f, 0.72f, 0.07f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.055f * s
                canvas.drawArc(RectF(0.58f * s, 0.45f * s, 0.8f * s, 0.66f * s), -80f, 160f, false, paint)
                line(0.36f, 0.28f, 0.36f, 0.2f, width = 0.035f)
                line(0.5f, 0.28f, 0.5f, 0.2f, width = 0.035f)
                paint.style = Paint.Style.FILL
            }
            PresetAvatar.CALENDAR -> {
                roundRect(0.24f, 0.26f, 0.76f, 0.78f, 0.06f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.3f, 0.42f, 0.7f, 0.48f, 0.02f)
                circle(0.39f, 0.59f, 0.035f)
                circle(0.5f, 0.59f, 0.035f)
                circle(0.61f, 0.59f, 0.035f)
                paint.xfermode = null
            }
            PresetAvatar.LOOP -> {
                roundRect(0.2f, 0.32f, 0.8f, 0.68f, 0.18f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.38f, 0.5f, 0.095f)
                circle(0.62f, 0.5f, 0.095f)
                roundRect(0.45f, 0.42f, 0.55f, 0.58f, 0.05f)
                paint.xfermode = null
            }
            PresetAvatar.WAVE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.07f * s
                repeat(3) { index ->
                    val y = (0.34f + index * 0.14f) * s
                    val wave = Path().apply {
                        moveTo(0.2f * s, y)
                        cubicTo(0.34f * s, y - 0.1f * s, 0.46f * s, y + 0.1f * s, 0.6f * s, y)
                        cubicTo(0.7f * s, y - 0.075f * s, 0.76f * s, y - 0.045f * s, 0.82f * s, y)
                    }
                    canvas.drawPath(wave, paint)
                }
                paint.style = Paint.Style.FILL
            }
            PresetAvatar.ORBIT -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.045f * s
                canvas.drawOval(RectF(0.2f * s, 0.32f * s, 0.8f * s, 0.68f * s), paint)
                canvas.save()
                canvas.rotate(62f, 0.5f * s, 0.5f * s)
                canvas.drawOval(RectF(0.2f * s, 0.32f * s, 0.8f * s, 0.68f * s), paint)
                canvas.restore()
                paint.style = Paint.Style.FILL
                circle(0.5f, 0.5f, 0.08f)
                circle(0.74f, 0.4f, 0.055f)
            }
            PresetAvatar.BLOOM -> {
                repeat(6) { index ->
                    val angle = index * Math.PI.toFloat() / 3f
                    circle(
                        0.5f + kotlin.math.cos(angle) * 0.16f,
                        0.5f + kotlin.math.sin(angle) * 0.16f,
                        0.12f
                    )
                }
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.5f, 0.5f, 0.095f)
                paint.xfermode = null
                circle(0.5f, 0.5f, 0.06f)
            }
            PresetAvatar.SMILE -> {
                circle(0.5f, 0.5f, 0.31f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.4f, 0.43f, 0.04f)
                circle(0.6f, 0.43f, 0.04f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.045f * s
                canvas.drawArc(RectF(0.36f * s, 0.44f * s, 0.64f * s, 0.68f * s), 20f, 140f, false, paint)
                paint.style = Paint.Style.FILL
                paint.xfermode = null
            }
            PresetAvatar.GLASSES -> {
                circle(0.5f, 0.5f, 0.3f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.39f, 0.46f, 0.085f)
                circle(0.61f, 0.46f, 0.085f)
                roundRect(0.47f, 0.44f, 0.53f, 0.48f, 0.02f)
                paint.xfermode = null
                roundRect(0.32f, 0.42f, 0.46f, 0.5f, 0.04f)
                roundRect(0.54f, 0.42f, 0.68f, 0.5f, 0.04f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.35f, 0.44f, 0.43f, 0.48f, 0.02f)
                roundRect(0.57f, 0.44f, 0.65f, 0.48f, 0.02f)
                paint.xfermode = null
            }
            PresetAvatar.FRIENDS -> {
                circle(0.38f, 0.38f, 0.12f)
                circle(0.62f, 0.38f, 0.12f)
                roundRect(0.2f, 0.56f, 0.5f, 0.78f, 0.12f)
                roundRect(0.5f, 0.56f, 0.8f, 0.78f, 0.12f)
            }
            PresetAvatar.TEAM -> {
                circle(0.5f, 0.32f, 0.11f)
                circle(0.32f, 0.43f, 0.095f)
                circle(0.68f, 0.43f, 0.095f)
                roundRect(0.24f, 0.58f, 0.76f, 0.8f, 0.14f)
            }
            PresetAvatar.FAMILY -> {
                circle(0.38f, 0.34f, 0.12f)
                circle(0.62f, 0.34f, 0.12f)
                circle(0.5f, 0.5f, 0.09f)
                roundRect(0.18f, 0.55f, 0.82f, 0.78f, 0.15f)
            }
            PresetAvatar.HELPER -> {
                circle(0.5f, 0.36f, 0.14f)
                roundRect(0.25f, 0.56f, 0.75f, 0.82f, 0.16f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                roundRect(0.45f, 0.61f, 0.55f, 0.77f, 0.02f)
                roundRect(0.37f, 0.66f, 0.63f, 0.72f, 0.02f)
                paint.xfermode = null
            }
            PresetAvatar.THINKER -> {
                circle(0.5f, 0.43f, 0.22f)
                roundRect(0.41f, 0.62f, 0.59f, 0.76f, 0.04f)
                circle(0.67f, 0.34f, 0.055f)
                circle(0.76f, 0.26f, 0.035f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.42f, 0.41f, 0.03f)
                circle(0.56f, 0.41f, 0.03f)
                paint.xfermode = null
            }
            PresetAvatar.CHILD -> {
                circle(0.5f, 0.43f, 0.19f)
                roundRect(0.34f, 0.62f, 0.66f, 0.79f, 0.1f)
                circle(0.36f, 0.3f, 0.055f)
                circle(0.5f, 0.25f, 0.06f)
                circle(0.64f, 0.3f, 0.055f)
            }
            PresetAvatar.GUIDE -> {
                circle(0.5f, 0.34f, 0.13f)
                roundRect(0.3f, 0.55f, 0.7f, 0.79f, 0.12f)
                star(0.72f, 0.32f, 0.1f, 0.04f, points = 5)
            }
            PresetAvatar.CREATOR -> {
                circle(0.5f, 0.36f, 0.13f)
                roundRect(0.28f, 0.56f, 0.72f, 0.78f, 0.12f)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.045f * s
                canvas.drawArc(RectF(0.28f * s, 0.21f * s, 0.72f * s, 0.56f * s), 205f, 130f, false, paint)
                paint.style = Paint.Style.FILL
            }
            PresetAvatar.LISTENER -> {
                circle(0.5f, 0.43f, 0.2f)
                paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                circle(0.42f, 0.42f, 0.03f)
                circle(0.56f, 0.42f, 0.03f)
                paint.xfermode = null
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 0.055f * s
                canvas.drawArc(RectF(0.62f * s, 0.4f * s, 0.84f * s, 0.64f * s), 105f, 150f, false, paint)
                paint.style = Paint.Style.FILL
                roundRect(0.36f, 0.64f, 0.64f, 0.79f, 0.08f)
            }
            PresetAvatar.LEADER -> {
                circle(0.5f, 0.34f, 0.13f)
                roundRect(0.28f, 0.56f, 0.72f, 0.79f, 0.12f)
                star(0.5f, 0.18f, 0.11f, 0.045f, points = 5)
            }
        }
        return bitmap
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
