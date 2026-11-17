package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import androidx.core.graphics.drawable.toBitmap
import com.example.data.model.Song
import kotlin.math.cos
import kotlin.math.sin

/**
 * **Noxa Kolase** (v3.7.5) — turns four chosen songs plus a photo from the
 * gallery into a single story poster, drawn entirely on-device.
 *
 * The layout follows the reference the user shared: four slightly rotated dark
 * cards floating over a softened photo, each with artwork, title, artist and a
 * playhead, then a quiet NOXA credit at the bottom. Rotation and overlap are
 * pixels, not Compose, so what is previewed and what lands in the gallery are
 * the same geometry.
 */
object CollageRenderer {

    private const val TAG = "NoxaCollage"
    const val WIDTH = 1080
    const val HEIGHT = 1920

    private val BG = Color.rgb(10, 11, 12)
    private val CARD = Color.rgb(18, 19, 22)
    private val LIME = Color.rgb(199, 255, 0)
    private val TEXT = Color.rgb(246, 246, 240)
    private val TEXT_DIM = Color.rgb(168, 172, 178)
    private val HAIRLINE = Color.argb(34, 255, 255, 255)

    /** One card's geometry on the poster. */
    private data class Slot(
        val cx: Float,
        val cy: Float,
        val width: Float,
        val rotation: Float
    )

    private val SLOTS = listOf(
        Slot(cx = 300f, cy = 480f, width = 420f, rotation = -12f),
        Slot(cx = 780f, cy = 540f, width = 420f, rotation = 10f),
        Slot(cx = 290f, cy = 1420f, width = 420f, rotation = 9f),
        Slot(cx = 800f, cy = 1360f, width = 420f, rotation = -9f)
    )

    /**
     * Renders the poster. [songs] may contain nulls (an empty slot draws a
     * placeholder card so the composition still looks intentional).
     */
    internal suspend fun render(
        context: Context,
        songs: List<Song?>,
        backgroundUri: String?,
        caption: String = ""
    ): Bitmap? = try {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas, context, backgroundUri)

        // A soft pool of light behind the cards so they read as floating.
        canvas.drawRect(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    WIDTH / 2f, HEIGHT * 0.46f, 760f,
                    intArrayOf(Color.argb(90, 199, 255, 0), Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )

        // Four cards, back to front so overlaps look natural. One card's
        // artwork failing must not throw away the whole poster.
        SLOTS.forEachIndexed { index, slot ->
            runCatching { drawCard(canvas, context, slot, songs.getOrNull(index)) }
                .onFailure { Log.w(TAG, "Card $index skipped: ${it.message}") }
        }

        drawFooter(canvas, caption)
        bitmap
    } catch (e: Exception) {
        Log.e(TAG, "Collage render failed", e)
        null
    }

    private suspend fun drawBackground(canvas: Canvas, context: Context, uri: String?) {
        val photo = uri?.let { loadPhoto(context, it) }
        if (photo != null) {
            // A cheap, dependency-free "blur": crop small, then let the canvas
            // stretch it back to poster size. No full-size intermediate list is
            // allocated, which is what used to blow the heap on large photos
            // (and made the export fail with "gagal dibuat").
            val small = centerCrop(photo, WIDTH / 10, HEIGHT / 10)
            canvas.drawBitmap(
                small, null,
                RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            if (small !== photo) small.recycle()
            photo.recycle()
            canvas.drawRect(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                Paint().apply { color = Color.argb(150, 6, 7, 8) }
            )
        } else {
            canvas.drawRect(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                        intArrayOf(Color.rgb(24, 30, 12), Color.rgb(8, 9, 10), Color.rgb(16, 18, 24)),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
            )
        }
        // Vignette so the corners melt into the frame.
        canvas.drawRect(
            0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
            Paint().apply {
                shader = RadialGradient(
                    WIDTH / 2f, HEIGHT / 2f, HEIGHT * 0.72f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(190, 0, 0, 0)),
                    floatArrayOf(0.45f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )
    }

    private suspend fun drawCard(canvas: Canvas, context: Context, slot: Slot, song: Song?) {
        val width = slot.width
        val cardHeight = width * 1.36f
        val left = slot.cx - width / 2f
        val top = slot.cy - cardHeight / 2f
        val rect = RectF(left, top, left + width, top + cardHeight)
        val radius = 34f

        canvas.save()
        canvas.rotate(slot.rotation, slot.cx, slot.cy)

        // Shadow-ish halo, card body, and a hairline that only catches light.
        canvas.drawRoundRect(
            RectF(rect.left - 6f, rect.top + 14f, rect.right + 6f, rect.bottom + 22f),
            radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 0, 0, 0) }
        )
        canvas.drawRoundRect(
            rect, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(238, 17, 18, 21) }
        )
        canvas.drawRoundRect(
            rect, radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = HAIRLINE
            }
        )

        val pad = 26f
        val artSize = width - pad * 2f
        val artRect = RectF(left + pad, top + pad, left + pad + artSize, top + pad + artSize)

        val artwork = song?.let { loadArtwork(context, it.artworkUrl) }
        if (artwork != null) {
            val scaled = centerCrop(artwork, artSize.toInt(), artSize.toInt())
            val path = Path().apply { addRoundRect(artRect, 22f, 22f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, artRect.left, artRect.top, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restore()
            if (scaled !== artwork) scaled.recycle()
            artwork.recycle()
        } else {
            canvas.drawRoundRect(
                artRect, 22f, 22f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 32, 36) }
            )
        }

        // Tiny brand tag inside the card, like the reference's player label.
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LIME
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.18f
        }
        canvas.drawText("NOXA", left + pad, artRect.bottom + 34f, tagPaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = TextUtils.ellipsize(
            song?.displayTitle ?: "Pilih lagu",
            titlePaint,
            width - pad * 2f - 54f,
            TextUtils.TruncateAt.END
        )
        canvas.drawText(title, 0, title.length, left + pad, artRect.bottom + 72f, titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DIM
            textSize = 23f
        }
        val artist = TextUtils.ellipsize(
            song?.displayArtist ?: "Ketuk untuk memilih",
            artistPaint,
            width - pad * 2f - 54f,
            TextUtils.TruncateAt.END
        )
        canvas.drawText(artist, 0, artist.length, left + pad, artRect.bottom + 104f, artistPaint)

        // Faint heart, the familiar "this is a song" mark.
        val heartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 230, 232, 236)
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
        }
        drawHeart(canvas, rect.right - pad - 12f, artRect.bottom + 62f, 13f, heartPaint)

        // Playhead + times, mirroring a player card.
        val barY = artRect.bottom + 140f
        canvas.drawRoundRect(
            RectF(left + pad, barY, rect.right - pad, barY + 4f),
            2f, 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 255, 255, 255) }
        )
        canvas.drawRoundRect(
            RectF(left + pad, barY, left + pad + (width - pad * 2f) * 0.38f, barY + 4f),
            2f, 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
        )
        canvas.drawCircle(
            left + pad + (width - pad * 2f) * 0.38f, barY + 2f, 6f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT }
        )
        val timePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DIM
            textSize = 19f
        }
        canvas.drawText("1:21", left + pad, barY + 30f, timePaint)
        val rightTime = "-2:25"
        canvas.drawText(
            rightTime, 0, rightTime.length,
            rect.right - pad - timePaint.measureText(rightTime), barY + 30f, timePaint
        )

        // Transport glyphs: back, pause capsule, forward.
        val transportY = barY + 62f
        val cx = slot.cx
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 240, 242, 240) }
        drawSkip(canvas, cx - 70f, transportY, glyph, backwards = true)
        canvas.drawRoundRect(
            RectF(cx - 6f, transportY - 12f, cx + 6f, transportY + 12f),
            3f, 3f, glyph
        )
        drawSkip(canvas, cx + 70f, transportY, glyph, backwards = false)

        canvas.restore()
    }

    private fun drawFooter(canvas: Canvas, caption: String) {
        val markSize = 44f
        val markLeft = WIDTH / 2f - 168f
        val markTop = HEIGHT - 132f
        canvas.drawRoundRect(
            RectF(markLeft, markTop, markLeft + markSize, markTop + markSize),
            13f, 13f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME }
        )
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BG
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }
        listOf(-8f, 4f, -3f).forEachIndexed { index, offset ->
            val x = markLeft + 12f + index * 10f
            canvas.drawLine(x, markTop + 22f - offset, x, markTop + 22f + offset, barPaint)
        }

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
        }
        canvas.drawText("Dibuat dengan NOXA", markLeft + markSize + 16f, markTop + 22f, brandPaint)

        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_DIM
            textSize = 19f
        }
        canvas.drawText(
            caption.ifBlank { "NOXA Music · skydev.my.id" },
            markLeft + markSize + 16f,
            markTop + 44f,
            subPaint
        )
    }

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(cx, cy + r * 0.9f)
            cubicTo(cx - r * 1.25f, cy - r * 0.1f, cx - r * 0.65f, cy - r * 1.15f, cx, cy - r * 0.35f)
            cubicTo(cx + r * 0.65f, cy - r * 1.15f, cx + r * 1.25f, cy - r * 0.1f, cx, cy + r * 0.9f)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSkip(canvas: Canvas, cx: Float, cy: Float, paint: Paint, backwards: Boolean) {
        val w = 15f
        val h = 19f
        val dir = if (backwards) -1f else 1f
        val path = Path().apply {
            moveTo(cx - w * dir, cy - h)
            lineTo(cx + w * dir, cy)
            lineTo(cx - w * dir, cy + h)
            close()
        }
        canvas.drawPath(path, paint)
        canvas.drawRoundRect(
            RectF(cx + w * dir - if (backwards) 4f else 0f, cy - h,
                cx + w * dir + if (backwards) 0f else 4f, cy + h),
            2f, 2f, paint
        )
    }

    private suspend fun loadArtwork(context: Context, url: String): Bitmap? = try {
        context.imageLoader.execute(
            ImageRequest.Builder(context)
                .data(url)
                .crossfade(false)
                .size(420)
                .build()
        ).drawable?.toBitmap()
    } catch (e: Exception) {
        null
    }

    private fun loadPhoto(context: Context, uriString: String): Bitmap? = try {
        val uri = Uri.parse(uriString)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
        var sample = 1
        while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        }
    } catch (e: Exception) {
        Log.w(TAG, "Photo could not be opened: ${e.message}")
        null
    }

    private fun centerCrop(source: Bitmap, width: Int, height: Int): Bitmap {
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val scaledW = (source.width * scale).toInt().coerceAtLeast(width)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(height)
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val x = (scaledW - width) / 2
        val y = (scaledH - height) / 2
        return Bitmap.createBitmap(scaled, x, y, width, height)
    }

    /** Gallery + share plumbing, shared with the other NOXA cards. */
    suspend fun saveAndShare(
        context: Context,
        bitmap: Bitmap,
        baseName: String,
        text: String,
        title: String
    ): ShareCardRenderer.ShareResult =
        ShareCardRenderer.saveAndShare(context, bitmap, baseName, text, title)
}
