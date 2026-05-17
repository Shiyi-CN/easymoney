package com.jiyixia.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.jiyixia.app.data.dao.CategoryDao
import com.jiyixia.app.data.dao.RecordDao
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record

@Database(
    entities = [Record::class, Category::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jiyixia.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
