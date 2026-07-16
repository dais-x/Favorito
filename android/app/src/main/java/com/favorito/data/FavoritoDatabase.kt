package com.favorito.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PendingLikeEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FavoritoDatabase : RoomDatabase() {
    abstract fun pendingLikeDao(): PendingLikeDao

    companion object {
        @Volatile private var instance: FavoritoDatabase? = null

        fun get(context: Context): FavoritoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FavoritoDatabase::class.java,
                    "favorito.db"
                ).build().also { instance = it }
            }
        }
    }
}
