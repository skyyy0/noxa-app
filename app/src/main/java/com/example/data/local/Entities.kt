package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Song

@Entity(tableName = "favorite_songs")
data class FavoriteSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        isFavorite = true
    )

    companion object {
        fun fromSong(song: Song): FavoriteSongEntity = FavoriteSongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            durationMs = song.durationMs
        )
    }
}

@Entity(tableName = "playback_history")
data class HistorySongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val streamUrl: String,
    val durationMs: Long,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toSong(isFav: Boolean = false): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        streamUrl = streamUrl,
        durationMs = durationMs,
        isFavorite = isFav
    )

    companion object {
        fun fromSong(song: Song): HistorySongEntity = HistorySongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkUrl = song.artworkUrl,
            streamUrl = song.streamUrl,
            durationMs = song.durationMs,
            playedAt = System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
