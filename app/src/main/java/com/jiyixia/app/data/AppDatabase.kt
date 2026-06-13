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
    version = 5,
    exportSchema = true
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

        /** v4 → v5：金额类型从 Double(元) 迁移到 Long(分) */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建新表（amount 字段改为 INTEGER）
                db.execSQL("""
                    CREATE TABLE records_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        categoryId INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        date INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isPendingConfirm INTEGER NOT NULL DEFAULT 0,
                        confidence INTEGER NOT NULL DEFAULT 100,
                        isReimbursable INTEGER NOT NULL DEFAULT 0,
                        isReimbursed INTEGER NOT NULL DEFAULT 0,
                        reimbursementTarget TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (categoryId) REFERENCES categories(id) ON DELETE RESTRICT
                    )
                """)

                // 2. 迁移数据：amount * 100 → amount（分）
                db.execSQL("""
                    INSERT INTO records_new (
                        id, type, amount, categoryId, note,
                        date, createdAt, isPendingConfirm, confidence,
                        isReimbursable, isReimbursed, reimbursementTarget
                    )
                    SELECT
                        id, type,
                        CAST(amount * 100 AS INTEGER),
                        categoryId, note,
                        date, createdAt, isPendingConfirm, confidence,
                        isReimbursable, isReimbursed, reimbursementTarget
                    FROM records
                """)

                // 3. 删除旧表
                db.execSQL("DROP TABLE records")

                // 4. 重命名新表
                db.execSQL("ALTER TABLE records_new RENAME TO records")

                // 5. 重建索引
                db.execSQL("CREATE INDEX index_records_categoryId ON records(categoryId)")
                db.execSQL("CREATE INDEX index_records_date ON records(date)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jiyixia.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
