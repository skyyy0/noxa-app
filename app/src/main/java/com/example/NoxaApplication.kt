package com.example

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.example.data.local.AppSettingsStore
import com.example.data.local.NoxaDatabase
import com.example.data.local.UsagePolicyStore
import com.example.data.remote.LyricsRepository
import com.example.data.remote.MusicRepository
import com.example.data.remote.StreamResolver
import com.example.data.remote.YouTubeSourceRepository
import com.example.media.MediaPlaybackService
import com.example.media.NoxaAudioPlayer
import com.google.common.util.concurrent.ListenableFuture
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NoxaApplication : Application(), ImageLoaderFactory {

    lateinit var database: NoxaDatabase
        private set

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    lateinit var streamResolver: StreamResolver
        private set

    lateinit var youtubeSourceRepository: YouTubeSourceRepository
        private set

    lateinit var usagePolicyStore: UsagePolicyStore
        private set

    lateinit var appSettingsStore: AppSettingsStore
        private set

    lateinit var player: NoxaAudioPlayer
        private set

    lateinit var authManager: com.example.data.auth.AuthManager
        private set

    /**
     * Connecting a [MediaController] to [MediaPlaybackService] is what keeps the
     * media session (and therefore the notification, the lock screen controls and
     * background playback) alive, and what lets the system offer to resume the
     * last session after a reboot.
     */
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Handled gracefully in test environment
        }
        database = NoxaDatabase.getDatabase(this)
        musicRepository = MusicRepository()
        lyricsRepository = LyricsRepository()
        streamResolver = StreamResolver()
        youtubeSourceRepository = YouTubeSourceRepository()
        usagePolicyStore = UsagePolicyStore(this)
        appSettingsStore = AppSettingsStore(this)
        player = NoxaAudioPlayer(this)
        authManager = com.example.data.auth.AuthManager(this)
        connectMediaSession()
    }

    private fun connectMediaSession() {
        try {
            val token = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener(
                {
                    try {
                        future.get()
                    } catch (e: Exception) {
                        Log.w(TAG, "Media controller connection failed: ${e.message}")
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not connect to the media session: ${e.message}")
        }
    }

    override fun onTerminate() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onTerminate()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            // Track/artist artwork is immutable, so honour the cache instead of
            // refetching it: this is the single biggest win for scroll smoothness.
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .crossfade(200)
            .build()
    }

    companion object {
        private const val TAG = "NoxaApplication"

        lateinit var instance: NoxaApplication
            private set
    }
}
