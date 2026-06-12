package com.jiyixia.app.repository

import com.jiyixia.app.data.dao.CategoryDao
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.dao.RecordDao
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
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
