package com.hypertap.autoclicker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MacroEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class HyperTapDatabase : RoomDatabase() {
    abstract fun macroDao(): MacroDao

    companion object {
        @Volatile
        private var INSTANCE: HyperTapDatabase? = null

        fun getDatabase(context: Context): HyperTapDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HyperTapDatabase::class.java,
                    "hypertap_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

