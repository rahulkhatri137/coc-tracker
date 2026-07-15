package com.rk.clashtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AccountEntity::class, UpgradeEntity::class], version = 2, exportSchema = false)
abstract class ClashDatabase : RoomDatabase() {
    abstract fun clashDao(): ClashDao

    companion object {
        @Volatile
        private var INSTANCE: ClashDatabase? = null

        fun getDatabase(context: Context): ClashDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClashDatabase::class.java,
                    "clash_tracker_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
