package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Resolves a pushed update link into a direct APK download URL.
 *
 * The developer publishes either a direct `.apk` URL or a MediaFire page
 * link; MediaFire pages embed the real file host, which is extracted here
 * without any API key. Anything else is passed through unchanged and the
 * system DownloadManager does the rest — an unusable link then fails
 * honestly instead of being guessed at.
 */
object UpdateRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** The direct download URL for [url], or null when it cannot be resolved. */
    fun resolveDirectApkUrl(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http")) return null
        if (!trimmed.contains("mediafire.com", ignoreCase = true)) {
            // Already a direct file link (or any non-MediaFire host).
            return trimmed
        }
        return try {
            val request = Request.Builder()
                .url(trimmed)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            } ?: return null
            // MediaFire embeds the direct host link in its download button.
            Regex("https://download\\d*\\.mediafire\\.com/[^\"\\s]+/[^\"\\s]+\\.apk")
                .find(body)?.value
                ?: Regex("\"downloadURL\":\"(https:[^\"]+)\"")
                    .find(body)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
}
