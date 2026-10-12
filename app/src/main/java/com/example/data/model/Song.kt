package com.example.data.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "Single",
    val artworkUrl: String,
    val streamUrl: String = "",
    val durationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lyrics: String? = null,
    val channelId: String? = null,
    val viewCount: String? = null
) {
    val isYouTubeTrack: Boolean
        get() = !streamUrl.startsWith("http://") && !streamUrl.startsWith("https://") || id.length == 11 && !id.startsWith("about_sky")
}
