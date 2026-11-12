package com.example.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.MusicSource
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * **Musik lokal** (v3.7.0) — the user's own files.
 *
 * NOXA does not scan the whole device: the user picks files through the system
 * picker (`ACTION_OPEN_DOCUMENT`), and we keep the *persisted* `content://`
 * grant so the same URI keeps opening after a reboot. Nothing is copied and no
 * storage permission is needed.
 *
 * Metadata (title/artist/album/duration) and the embedded cover are read once at
 * import time with [MediaMetadataRetriever]; the cover is written into app
 * storage so the list has artwork without decoding every file on every scroll.
 * The list itself lives in SharedPreferences as JSON — small, synchronous on
 * read, and it predates any Room state, exactly like [PlaybackSessionStore].
 */
object LocalMusicStore {

    private const val TAG = "LocalMusicStore"
    private const val PREFS = "noxa_local_music"
    private const val KEY_TRACKS = "tracks"
    private const val ART_DIR = "local_art"

    /** Every imported track, newest first. Never throws. */
    fun load(context: Context): List<Song> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRACKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                Song(
                    id = json.optString("id"),
                    title = json.optString("title"),
                    artist = json.optString("artist"),
                    album = json.optString("album"),
                    artworkUrl = json.optString("artworkUrl"),
                    streamUrl = json.optString("streamUrl"),
                    durationMs = json.optLong("durationMs"),
                    source = MusicSource.LOCAL,
                    remoteId = json.optString("streamUrl")
                ).takeIf { it.id.isNotBlank() && it.streamUrl.isNotBlank() }
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not read the local library: ${error.message}")
            emptyList()
        }
    }

    /**
     * Imports [uris] and returns **all** tracks afterwards (newest first).
     * Already-imported URIs are skipped instead of duplicated, so re-picking a
     * file is harmless.
     */
    suspend fun import(context: Context, uris: List<Uri>): List<Song> =
        withContext(Dispatchers.IO) {
            val existing = load(context).toMutableList()
            val known = existing.map { it.streamUrl }.toHashSet()
            uris.forEach { uri ->
                val key = uri.toString()
                if (!known.add(key)) return@forEach
                val song = readSong(context, uri) ?: return@forEach
                existing.add(0, song)
            }
            persist(context, existing)
            existing
        }

    /** Removes one imported track from the library (the file is untouched). */
    fun remove(context: Context, songId: String): List<Song> {
        val remaining = load(context).filterNot { it.id == songId }
        persist(context, remaining)
        return remaining
    }

    private fun persist(context: Context, songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("artworkUrl", song.artworkUrl)
                    put("streamUrl", song.streamUrl)
                    put("durationMs", song.durationMs)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRACKS, array.toString())
            .apply()
    }

    /**
     * Reads one picked file. Returns null when the file cannot be opened or
     * carries no audio — better to skip it than to add a row that never plays.
     */
    private fun readSong(context: Context, uri: Uri): Song? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val originalName = queryDisplayName(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: originalName.substringBeforeLast('.').ifBlank { "Tanpa judul" }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Musik lokal"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "Lokal"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            val id = "local:" + Integer.toHexString(uri.toString().hashCode())
            val artwork = saveArtwork(context, id, retriever.embeddedPicture)

            Song(
                id = id,
                title = title,
                artist = artist,
                album = album,
                artworkUrl = artwork,
                streamUrl = uri.toString(),
                durationMs = duration,
                source = MusicSource.LOCAL,
                remoteId = uri.toString()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Skipping ${uri.lastPathSegment}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull().orEmpty()

    /**
     * Writes the embedded cover once and returns a `file://` URL Coil can load.
     * Files live in app storage, so they survive cache eviction of the picker
     * grant itself.
     */
    private fun saveArtwork(context: Context, id: String, bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        return runCatching {
            val dir = File(context.filesDir, ART_DIR).apply { mkdirs() }
            val file = File(dir, id.removePrefix("local:") + ".img")
            if (!file.exists()) file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.getOrElse { "" }
    }
}
