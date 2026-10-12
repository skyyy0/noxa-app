package com.example.data.model

/** Where a track (and its audio) came from. */
enum class MusicSource {
    /** Audius public API — full-length, artist-uploaded audio. */
    AUDIUS,

    /** Deezer public API — 30 second, label-approved preview clips. */
    DEEZER
}

data class Song(
    /**
     * Globally unique id, namespaced by provider (`deezer:123`, `audius:abc`).
     * Two providers can return the same song, so the source prefix keeps
     * favourites, history and the playback queue from colliding.
     */
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
    val viewCount: String? = null,
    val source: MusicSource = MusicSource.AUDIUS,
    /** The provider's own id, used when calling that provider's API again. */
    val remoteId: String = "",
    /** True when [streamUrl] is a short preview clip rather than the full track. */
    val isPreview: Boolean = false,
    /** Public web page for this track, used by the share button. */
    val shareUrl: String = ""
) {}
