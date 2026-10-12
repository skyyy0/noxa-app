package com.example.media

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.NoxaApplication
import com.example.data.model.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Hosts the MediaSession that backs the system media notification, the lock
 * screen controls and background playback.
 *
 * The session wraps the single ExoPlayer owned by [NoxaApplication], so the
 * notification always reflects the player that is actually running. Media3
 * provides the notification and the device-specific media UI; NOXA does not draw
 * any custom overlay of its own.
 *
 * [ResumptionCallback] additionally lets the system offer to continue the last
 * session after the app process is killed or the device is rebooted, which is the
 * only reboot-survival path Android allows for media playback (an app is not
 * allowed to start a `mediaPlayback` foreground service from `BOOT_COMPLETED`).
 */
@UnstableApi
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as? NoxaApplication ?: return
        val player = app.player.exoPlayer

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            // Media3 derives the System UI media controls from the session, so the
            // player's own commands (seek, next/previous, stop) must stay enabled.
            .setCallback(ResumptionCallback(app))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            // Nothing is playing any more, so the service may go away with the task.
            stopSelf()
        }
        // While playback is ongoing the service is deliberately left running so the
        // notification and the lock screen controls survive swiping the app away.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // The ExoPlayer instance is owned by NoxaApplication and shared with the
        // UI, so only the session may be released here. Releasing the player
        // would break playback whenever the service is destroyed.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Serves the "continue listening" affordance the system shows after the app
     * was terminated or the device rebooted.
     */
    private class ResumptionCallback(
        private val app: NoxaApplication
    ) : MediaSession.Callback {

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val session = app.player.prepareResumableSession()
            if (session == null || session.songs.isEmpty()) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }

            // Rebuild the in-app queue too, so the mini player and the notification
            // agree with what the system is about to resume.
            app.player.restoreSession(session)

            val startIndex = session.index.coerceIn(0, session.songs.size - 1)
            return try {
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        session.songs.map { it.toMediaItem() },
                        startIndex,
                        session.positionMs
                    )
                )
            } catch (e: Exception) {
                Log.w("MediaPlaybackService", "Resumption failed: ${e.message}")
                Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }
        }
    }
}

/**
 * Builds the MediaItem the system needs for the resumption list. Artwork is
 * included because the resumption UI can appear before the network is up.
 */
private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(Uri.parse(streamUrl))
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsPlayable(true)
            .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .build()
    )
    .build()
