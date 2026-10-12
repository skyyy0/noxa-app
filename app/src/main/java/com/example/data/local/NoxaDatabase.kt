package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteSongEntity::class,
        HistorySongEntity::class,
        PlaylistEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NoxaDatabase : RoomDatabase() {
    abstract fun noxaDao(): NoxaDao

    companion object {
        @Volatile
        private var INSTANCE: NoxaDatabase? = null

        fun getDatabase(context: Context): NoxaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoxaDatabase::class.java,
                    "noxa_music.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
