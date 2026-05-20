package com.jiyixia.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jiyixia.app.data.dao.CategoryDao
import com.jiyixia.app.data.dao.RecordDao
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record

@Database(
    entities = [Record::class, Category::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v1 → v2：为 records 表新增 isReimbursable 字段 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE records ADD COLUMN isReimbursable INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v2 → v3：为 records 表新增 isReimbursed 字段 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE records ADD COLUMN isReimbursed INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v3 → v4：为 records 表新增 reimbursementTarget 字段 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE records ADD COLUMN reimbursementTarget TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jiyixia.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
