package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteSongEntity::class,
        HistorySongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NoxaDatabase : RoomDatabase() {
    abstract fun noxaDao(): NoxaDao

    companion object {
        @Volatile
        private var INSTANCE: NoxaDatabase? = null

        /**
         * Adds the `playlist_songs` table introduced in version 2.
         *
         * Written as a real migration (rather than relying on the destructive
         * fallback) so upgrading does not throw away the user's favourites,
         * history and playlists.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playlist_songs` (" +
                        "`playlistId` INTEGER NOT NULL, " +
                        "`songId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`artist` TEXT NOT NULL, " +
                        "`album` TEXT NOT NULL, " +
                        "`artworkUrl` TEXT NOT NULL, " +
                        "`streamUrl` TEXT NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`remoteId` TEXT NOT NULL, " +
                        "`isPreview` INTEGER NOT NULL, " +
                        "`shareUrl` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `songId`))"
                )
            }
        }

        fun getDatabase(context: Context): NoxaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoxaDatabase::class.java,
                    "noxa_music.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
