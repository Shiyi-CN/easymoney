package com.jiyixia.app.data.dao

import androidx.room.*
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder, id")
    fun getByType(type: Int): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun getAll(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY date DESC")
    fun getAll(): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun getByDateRange(start: Long, end: Long): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE isPendingConfirm = 1 ORDER BY date DESC")
    fun getPendingConfirm(): Flow<List<Record>>

    @Query("SELECT COUNT(*) FROM records WHERE isPendingConfirm = 1")
    fun getPendingConfirmCount(): Flow<Int>

    @Query("""
        SELECT SUM(amount) FROM records 
        WHERE type = :type AND date BETWEEN :start AND :end
    """)
    fun getSumByTypeAndDateRange(type: Int, start: Long, end: Long): Flow<Double?>

    @Query("""
        SELECT categoryId, SUM(amount) as amount FROM records 
        WHERE type = 0 AND date BETWEEN :start AND :end 
        GROUP BY categoryId ORDER BY amount DESC
    """)
    fun getExpenseGroupByCategory(start: Long, end: Long): Flow<List<CategorySum>>

    @Insert
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    @Query("DELETE FROM records WHERE isPendingConfirm = 1")
    suspend fun deleteAllPending()
}

data class CategorySum(
    val categoryId: Long,
    val amount: Double
)
