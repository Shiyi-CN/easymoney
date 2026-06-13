package com.jiyixia.app.repository

import android.content.Context
import com.jiyixia.app.data.dao.CategoryDao
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.dao.RecordDao
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.util.BackupUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
     * 安全删除所有记录：先自动备份，再删除
     * @param context Android Context，用于备份
     * @return 备份结果，成功后自动删除
     */
    suspend fun safeDeleteAll(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 先备份
            val backupResult = BackupUtil.backup(context)
            if (backupResult.isFailure) {
                return@withContext backupResult
            }

            // 2. 备份成功后，删除所有记录
            // 由于 DAO 层已移除 deleteAll()，这里直接执行 SQL
            // 注意：这里需要使用 RecordDao 的原始 SQL 执行能力
            // 但为了保持 DAO 层的简洁性，我们使用事务方式
            recordDao.deleteAllInTransaction()

            Result.success(backupResult.getOrNull() ?: "备份成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 安全删除所有待确认记录：先自动备份，再删除
     * @param context Android Context，用于备份
     * @return 备份结果，成功后自动删除
     */
    suspend fun safeDeleteAllPending(context: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupResult = BackupUtil.backup(context)
            if (backupResult.isFailure) {
                return@withContext backupResult
            }
            recordDao.deleteAllPending()
            Result.success(backupResult.getOrNull() ?: "备份成功")
        } catch (e: Exception) {
            Result.failure(e)
        }
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
}
