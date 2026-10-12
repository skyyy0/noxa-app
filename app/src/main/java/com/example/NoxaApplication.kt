package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import com.example.data.local.NoxaDatabase
import com.example.data.remote.AudiusRepository
import com.example.media.NoxaAudioPlayer
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class NoxaApplication : Application(), ImageLoaderFactory {

    lateinit var database: NoxaDatabase
        private set

    lateinit var musicRepository: AudiusRepository
        private set

    lateinit var player: NoxaAudioPlayer
        private set

    lateinit var authManager: com.example.data.auth.AuthManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Handled gracefully in test environment
        }
        database = NoxaDatabase.getDatabase(this)
        musicRepository = AudiusRepository()
        player = NoxaAudioPlayer(this)
        authManager = com.example.data.auth.AuthManager(this)
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
            .respectCacheHeaders(false)
            .crossfade(true)
            .crossfade(300)
            .build()
    }

    companion object {
        lateinit var instance: NoxaApplication
            private set
    }
}
