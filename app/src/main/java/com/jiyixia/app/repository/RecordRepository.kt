package com.jiyixia.app.repository

import com.jiyixia.app.data.dao.CategoryDao
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.dao.RecordDao
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class RecordRepository(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao
) {
    // Records
    fun getAllRecords(): Flow<List<Record>> = recordDao.getAll().flowOn(Dispatchers.IO)
    fun getRecordsByDateRange(start: Long, end: Long): Flow<List<Record>> =
        recordDao.getByDateRange(start, end).flowOn(Dispatchers.IO)

    fun getPendingConfirmRecords(): Flow<List<Record>> = recordDao.getPendingConfirm().flowOn(Dispatchers.IO)
    fun getPendingConfirmCount(): Flow<Int> = recordDao.getPendingConfirmCount().flowOn(Dispatchers.IO)
    fun getSumByType(type: Int, start: Long, end: Long): Flow<Long?> =
        recordDao.getSumByTypeAndDateRange(type, start, end).flowOn(Dispatchers.IO)

    /**
     * 获取指定类型的收支总和（一次性查询，非 Flow）
     */
    suspend fun getSumByTypeOnce(type: Int, start: Long, end: Long): Long = withContext(Dispatchers.IO) {
        recordDao.getSumByTypeAndDateRange(type, start, end).first() ?: 0L
    }

    fun getExpenseGroupByCategory(start: Long, end: Long): Flow<List<CategorySum>> =
        recordDao.getExpenseGroupByCategory(start, end).flowOn(Dispatchers.IO)

    fun getIncomeGroupByCategory(start: Long, end: Long): Flow<List<CategorySum>> =
        recordDao.getIncomeGroupByCategory(start, end).flowOn(Dispatchers.IO)

    suspend fun insertRecord(record: Record): Long = withContext(Dispatchers.IO) {
        recordDao.insert(record)
    }
    suspend fun updateRecord(record: Record) = withContext(Dispatchers.IO) {
        recordDao.update(record)
    }
    suspend fun deleteRecord(record: Record) = withContext(Dispatchers.IO) {
        recordDao.delete(record)
    }

    /**
     * 删除所有记录
     * 注意：调用前请确保已备份
     */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        recordDao.deleteAllInTransaction()
    }

    /**
     * 删除所有待确认记录
     * 注意：调用前请确保已备份
     */
    suspend fun deleteAllPending() = withContext(Dispatchers.IO) {
        recordDao.deleteAllPending()
    }

    // Categories
    fun getCategoriesByType(type: Int): Flow<List<Category>> = categoryDao.getByType(type).flowOn(Dispatchers.IO)
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAll().flowOn(Dispatchers.IO)
    suspend fun insertCategory(category: Category): Long = withContext(Dispatchers.IO) {
        categoryDao.insert(category)
    }
    suspend fun updateCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.update(category)
    }
    suspend fun deleteCategory(category: Category) = withContext(Dispatchers.IO) {
        categoryDao.delete(category)
    }

    // ── 报销 ──
    fun getReimbursableSumByDateRange(start: Long, end: Long): Flow<Long?> =
        recordDao.getReimbursableSumByDateRange(start, end).flowOn(Dispatchers.IO)
    fun getReimbursableSumAll(): Flow<Long?> =
        recordDao.getReimbursableSumAll().flowOn(Dispatchers.IO)
    fun getReimbursedSumByDateRange(start: Long, end: Long): Flow<Long?> =
        recordDao.getReimbursedSumByDateRange(start, end).flowOn(Dispatchers.IO)
    fun getReimbursableCountByDateRange(start: Long, end: Long): Flow<Int> =
        recordDao.getReimbursableCountByDateRange(start, end).flowOn(Dispatchers.IO)
    suspend fun setReimbursed(id: Long, reimbursed: Boolean) = withContext(Dispatchers.IO) {
        recordDao.setReimbursed(id, reimbursed)
    }

    /** 事务：标记报销并创建收入记录 */
    suspend fun markReimbursedWithIncome(recordId: Long, reimbursed: Boolean, incomeRecord: Record?) =
        withContext(Dispatchers.IO) {
            recordDao.markReimbursedWithIncome(recordId, reimbursed, incomeRecord)
        }

    /** 事务：撤销报销并删除收入记录 */
    suspend fun undoReimbursedWithIncome(recordId: Long, incomeRecordId: Long) =
        withContext(Dispatchers.IO) {
            recordDao.undoReimbursedWithIncome(recordId, incomeRecordId)
        }

    // ── 搜索/筛选 ──

    suspend fun searchRecords(
        minAmount: Long? = null,
        maxAmount: Long? = null,
        categoryId: Long? = null,
        keyword: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): List<Record> = withContext(Dispatchers.IO) {
        recordDao.searchRecords(minAmount, maxAmount, categoryId, keyword, startDate, endDate)
    }

    // ── 连续记账天数 ──

    /**
     * 计算连续记账天数
     * 从今天开始往前数，连续有记录的天数
     * 优化：逐日查询数据库，避免加载所有记录到内存
     */
    suspend fun getStreakDays(): Int = withContext(Dispatchers.IO) {
        val cal = java.util.Calendar.getInstance()
        var streak = 0

        // 从今天开始往前逐日检查
        while (true) {
            // 设置为当天的 00:00:00
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis

            // 设置为次日的 00:00:00
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            val dayEnd = cal.timeInMillis

            // 查询当天是否有记录
            val count = recordDao.getRecordCountByDay(dayStart, dayEnd)
            if (count > 0) {
                streak++
                // 回到当天，再往前一天
                cal.add(java.util.Calendar.DAY_OF_MONTH, -2)
            } else {
                break
            }
        }

        streak
    }
}
