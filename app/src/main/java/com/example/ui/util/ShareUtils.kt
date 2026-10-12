package com.example.ui.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.example.data.model.Song

/**
 * Shares a track through the system share sheet.
 *
 * The link is the provider's own public page (Deezer track page or Audius page),
 * falling back to the raw stream URL, so the recipient gets something playable
 * in a browser even if they do not have NOXA installed.
 */
object ShareUtils {

    fun shareSong(context: Context, song: Song) {
        val link = song.shareUrl.takeIf { it.isNotBlank() }
            ?: song.streamUrl.takeIf { it.isNotBlank() }

        val body = buildString {
            append("🎵 ")
            append(song.title)
            append(" — ")
            append(song.artist)
            if (!link.isNullOrBlank()) {
                append('\n')
                append(link)
            }
            append("\n\nDidengarkan lewat NOXA Music")
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${song.title} — ${song.artist}")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        val chooser = Intent.createChooser(send, "Bagikan \"${song.title}\"")
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            // No app can handle the share; nothing sensible to do.
        }
    }
}
