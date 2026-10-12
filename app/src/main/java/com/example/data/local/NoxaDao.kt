package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A playlist together with how many songs it currently holds. */
data class PlaylistSummary(
    val playlistId: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val songCount: Int
)

@Dao
interface NoxaDao {
    // Favorites
    @Query("SELECT * FROM favorite_songs ORDER BY addedAt DESC")
    fun getFavoriteSongs(): Flow<List<FavoriteSongEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE id = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    /**
     * Read once, inside the toggle transaction, so a double tap can never
     * insert then delete (or the reverse) from a stale in-memory value.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_songs WHERE id = :songId)")
    suspend fun isFavoriteOnce(songId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(song: FavoriteSongEntity)

    @Query("DELETE FROM favorite_songs WHERE id = :songId")
    suspend fun deleteFavorite(songId: String)

    // Playback History
    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT 50")
    fun getPlaybackHistory(): Flow<List<HistorySongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(song: HistorySongEntity)

    @Query("DELETE FROM playback_history")
    suspend fun clearHistory()

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        "SELECT p.playlistId AS playlistId, p.name AS name, p.description AS description, " +
            "p.createdAt AS createdAt, COUNT(s.songId) AS songCount " +
            "FROM playlists p LEFT JOIN playlist_songs s ON s.playlistId = p.playlistId " +
            "GROUP BY p.playlistId, p.name, p.description, p.createdAt " +
            "ORDER BY p.createdAt DESC"
    )
    fun getPlaylistSummaries(): Flow<List<PlaylistSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deletePlaylistSongs(id: Long)

    // Songs inside a playlist
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongIds(playlistId: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    fun countPlaylistSongs(playlistId: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(entity: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: String)
}
