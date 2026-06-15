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
    fun getSumByTypeAndDateRange(type: Int, start: Long, end: Long): Flow<Long?>

    @Query("""
        SELECT categoryId, SUM(amount) as amount FROM records
        WHERE type = 0 AND date BETWEEN :start AND :end
        GROUP BY categoryId ORDER BY amount DESC
    """)
    fun getExpenseGroupByCategory(start: Long, end: Long): Flow<List<CategorySum>>

    @Query("""
        SELECT categoryId, SUM(amount) as amount FROM records
        WHERE type = 1 AND date BETWEEN :start AND :end
        GROUP BY categoryId ORDER BY amount DESC
    """)
    fun getIncomeGroupByCategory(start: Long, end: Long): Flow<List<CategorySum>>

    @Insert
    suspend fun insert(record: Record): Long

    @Update
    suspend fun update(record: Record)

    @Delete
    suspend fun delete(record: Record)

    @Query("DELETE FROM records WHERE isPendingConfirm = 1")
    suspend fun deleteAllPending()

    /**
     * 事务删除所有记录
     * 注意：此方法会直接删除所有数据，请确保在调用前已备份
     */
    @Transaction
    @Query("DELETE FROM records")
    suspend fun deleteAllInTransaction()

    // ── 报销相关查询 ──

    /** 待报销金额（isReimbursable=1 且未报销） */
    @Query("SELECT SUM(amount) FROM records WHERE type = 0 AND isReimbursable = 1 AND isReimbursed = 0 AND date BETWEEN :start AND :end")
    fun getReimbursableSumByDateRange(start: Long, end: Long): Flow<Long?>

    /** 全部待报销金额 */
    @Query("SELECT SUM(amount) FROM records WHERE type = 0 AND isReimbursable = 1 AND isReimbursed = 0")
    fun getReimbursableSumAll(): Flow<Long?>

    /** 已报销金额（本月） */
    @Query("SELECT SUM(amount) FROM records WHERE type = 0 AND isReimbursable = 1 AND isReimbursed = 1 AND date BETWEEN :start AND :end")
    fun getReimbursedSumByDateRange(start: Long, end: Long): Flow<Long?>

    /** 标记已报销 / 取消已报销 */
    @Query("UPDATE records SET isReimbursed = :reimbursed WHERE id = :id")
    suspend fun setReimbursed(id: Long, reimbursed: Boolean)

    /** 事务：标记报销并创建收入记录 */
    @Transaction
    suspend fun markReimbursedWithIncome(
        recordId: Long,
        reimbursed: Boolean,
        incomeRecord: Record?
    ) {
        setReimbursed(recordId, reimbursed)
        incomeRecord?.let { insert(it) }
    }

    /** 事务：撤销报销并删除收入记录 */
    @Transaction
    suspend fun undoReimbursedWithIncome(
        recordId: Long,
        incomeRecordId: Long
    ) {
        setReimbursed(recordId, false)
        deleteById(incomeRecordId)
    }

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 待报销笔数 */
    @Query("SELECT COUNT(*) FROM records WHERE type = 0 AND isReimbursable = 1 AND isReimbursed = 0 AND date BETWEEN :start AND :end")
    fun getReimbursableCountByDateRange(start: Long, end: Long): Flow<Int>

    // ── 连续记账天数 ──

    /** 获取所有记录的时间戳列表（用于计算连续天数） */
    @Query("SELECT DISTINCT date FROM records ORDER BY date DESC")
    suspend fun getAllRecordDates(): List<Long>

    /** 检查指定日期是否有记录（按天判断） */
    @Query("SELECT COUNT(*) FROM records WHERE date >= :dayStart AND date < :dayEnd")
    suspend fun getRecordCountByDay(dayStart: Long, dayEnd: Long): Int

    // ── 搜索/筛选 ──

    /** 搜索记录（支持金额范围、分类、关键词、日期范围） */
    @Query("""
        SELECT * FROM records
        WHERE (:minAmount IS NULL OR amount >= :minAmount)
        AND (:maxAmount IS NULL OR amount <= :maxAmount)
        AND (:categoryId IS NULL OR categoryId = :categoryId)
        AND (:keyword IS NULL OR note LIKE '%' || :keyword || '%')
        AND (:startDate IS NULL OR date >= :startDate)
        AND (:endDate IS NULL OR date <= :endDate)
        ORDER BY date DESC
    """)
    suspend fun searchRecords(
        minAmount: Long? = null,
        maxAmount: Long? = null,
        categoryId: Long? = null,
        keyword: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): List<Record>
}

data class CategorySum(
    val categoryId: Long,
    val amount: Long
)
