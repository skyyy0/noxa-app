package com.example.ui.util

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import androidx.core.content.FileProvider

/**
 * The Spotify-Canvas-style share card: artwork, song title, artist and NOXA
 * branding on one square image. Rendering is plain Bitmap/Canvas so it works
 * anywhere (including from a bottom-sheet coroutine) without a composable
 * host.
 *
 * Sharing flow: the card is rendered, best-effort saved into the gallery
 * (`Pictures/NOXA`) so the user owns the image, and then handed to the system
 * share sheet through the FileProvider.
 */
object ShareCardRenderer {

    private const val CARD_SIZE = 1080
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"
    private const val TAG = "ShareCard"

    /** Colors tuned to the NOXA theme (near-black with lime accents). */
    private val BG_TOP = Color.rgb(24, 30, 12)
    private val BG_BOTTOM = Color.rgb(9, 10, 7)
    private val LIME = Color.rgb(199, 255, 0)
    private val TEXT_PRIMARY = Color.rgb(244, 246, 238)
    private val TEXT_SECONDARY = Color.rgb(168, 174, 156)

    /**
     * Renders the card, saves it to the gallery and opens the share sheet.
     * Returns true when the share sheet was handed the image successfully.
     */
    suspend fun share(context: Context, song: Song): Boolean {
        val file = try {
            val bitmap = render(context, song) ?: return false
            saveToGallery(context, bitmap, song)
            writeToCache(context, bitmap, song)
        } catch (e: Exception) {
            Log.e(TAG, "Rendering/saving the share card failed", e)
            return false
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, NoxaLinks.shareText(song))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Many share targets (and several OEM pickers) silently drop
                // the URI grant carried only by EXTRA_STREAM; putting the uri
                // into ClipData is what actually propagates the read grant.
                clipData = ClipData.newUri(context.contentResolver, "NOXA share card", uri)
            }
            val chooser = Intent.createChooser(send, "Bagikan \"${song.title}\"").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Opening the share sheet failed", e)
            false
        }
    }

    /** Draws the 1080×1080 card. Returns null when nothing could be drawn. */
    internal suspend fun render(context: Context, song: Song): Bitmap? {
        val artwork: Bitmap? = try {
            val loader: ImageLoader = context.imageLoader
            val drawable = loader.execute(
                ImageRequest.Builder(context)
                    .data(song.artworkUrl)
                    .crossfade(false)
                    .build()
            ).drawable
            drawable?.toBitmap()
        } catch (e: Exception) {
            null
        }
        val bitmap = Bitmap.createBitmap(CARD_SIZE, CARD_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: vertical theme gradient.
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, CARD_SIZE.toFloat(),
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, CARD_SIZE.toFloat(), CARD_SIZE.toFloat(), bgPaint)

        // Artwork: rounded square, centered.
        val artSize = 700f
        val artLeft = (CARD_SIZE - artSize) / 2f
        val artTop = 130f
        val artRect = RectF(artLeft, artTop, artLeft + artSize, artTop + artSize)
        if (artwork != null) {
            val scaled = Bitmap.createScaledBitmap(artwork, artSize.toInt(), artSize.toInt(), true)
            val path = Path().apply { addRoundRect(artRect, 44f, 44f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, artLeft, artTop, Paint(Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
        } else {
            val placeholder = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG_TOP }
            canvas.drawRoundRect(artRect, 44f, 44f, placeholder)
        }

        // Vector "PINNED" badge for pinned tracks, top-left of the artwork:
        // a drawn five-point star + label — no emoji glyphs on the card.
        if (PinnedTracks.isPinned(song)) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LIME }
            val badge = RectF(artLeft + 18f, artTop + 18f, artLeft + 172f, artTop + 88f)
            canvas.drawRoundRect(badge, 20f, 20f, badgePaint)
            drawStar(
                canvas = canvas,
                cx = badge.left + 35f,
                cy = badge.centerY(),
                outerRadius = 21f,
                paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
            )
            val starPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 38f
                textAlign = Paint.Align.LEFT
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("PINNED", badge.left + 64f, badge.centerY() + 13f, starPaint)
        }

        // Title (single line, ellipsized) and artist below the artwork.
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 62f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val title = TextUtils.ellipsize(song.title, titlePaint, CARD_SIZE - 160f, TextUtils.TruncateAt.END)
        canvas.drawText(title, 0, title.length, CARD_SIZE / 2f, 930f, titlePaint)

        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 44f
            textAlign = Paint.Align.CENTER
        }
        val artist = TextUtils.ellipsize(song.artist, artistPaint, CARD_SIZE - 200f, TextUtils.TruncateAt.END)
        canvas.drawText(artist, 0, artist.length, CARD_SIZE / 2f, 1000f, artistPaint)

        // Lime divider + NOXA branding footer.
        val linePaint = Paint().apply { color = LIME }
        canvas.drawRect(CARD_SIZE / 2f - 60f, 1042f, CARD_SIZE / 2f + 60f, 1048f, linePaint)

        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f
        }
        canvas.drawText("NOXA MUSIC", CARD_SIZE / 2f, 1105f, brandPaint)

        val linkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("skyvibes.my.id", CARD_SIZE / 2f, 1150f, linkPaint)

        return bitmap
    }

    /** Draws a clean five-point star (vector-style, not an emoji glyph). */
    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerRadius: Float, paint: Paint) {
        val path = Path()
        val innerRadius = outerRadius * 0.42f
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Best-effort gallery save (`Pictures/NOXA`). On Android 10+ this needs no
     * permission; below that it needs WRITE_EXTERNAL_STORAGE, so failures are
     * swallowed — the share sheet still works through the FileProvider copy.
     */
    private fun saveToGallery(context: Context, bitmap: Bitmap, song: Song): Uri? {
        val safeName = song.title
            .replace(Regex("[^A-Za-z0-9 _-]"), "")
            .trim()
            .replace(' ', '-')
            .take(40)
            .ifBlank { "share" }
        val name = "NOXA-$safeName.png"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NOXA")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "NOXA"
                )
                dir.mkdirs()
                val file = File(dir, name)
                file.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.insertImage(context.contentResolver, file.absolutePath, name, null)
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The FileProvider copy the share sheet actually hands out. */
    private fun writeToCache(context: Context, bitmap: Bitmap, song: Song): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "noxa-share-${song.id.hashCode()}.png")
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file
    }
}
