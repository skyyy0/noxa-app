package com.example.media

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.NoxaApplication

/**
 * Hosts the MediaSession that backs the system media notification, the lock
 * screen controls and background playback.
 *
 * The session wraps the single ExoPlayer owned by [NoxaApplication], so the
 * notification always reflects the player that is actually running. Media3
 * provides the notification and the device-specific media UI (e.g. the compact
 * media player on supported Android versions); NOXA does not draw any custom
 * overlay of its own.
 */
@UnstableApi
class MediaPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as? NoxaApplication
        val player = app?.player?.exoPlayer
        if (player != null) {
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
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // The ExoPlayer instance is owned by NoxaApplication and shared with the
        // UI, so only the session may be released here. Releasing the player
        // would break playback whenever the service is destroyed.
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
