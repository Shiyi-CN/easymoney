package com.jiyixia.app.repository

import com.jiyixia.app.data.dao.CategoryDao
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.dao.RecordDao
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import kotlinx.coroutines.flow.Flow

class RecordRepository(
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao
) {
    // Records
    fun getAllRecords(): Flow<List<Record>> = recordDao.getAll()
    fun getRecordsByDateRange(start: Long, end: Long): Flow<List<Record>> =
        recordDao.getByDateRange(start, end)

    fun getPendingConfirmRecords(): Flow<List<Record>> = recordDao.getPendingConfirm()
    fun getPendingConfirmCount(): Flow<Int> = recordDao.getPendingConfirmCount()
    fun getSumByType(type: Int, start: Long, end: Long): Flow<Double?> =
        recordDao.getSumByTypeAndDateRange(type, start, end)

    fun getExpenseGroupByCategory(start: Long, end: Long): Flow<List<CategorySum>> =
        recordDao.getExpenseGroupByCategory(start, end)

    suspend fun insertRecord(record: Record): Long = recordDao.insert(record)
    suspend fun updateRecord(record: Record) = recordDao.update(record)
    suspend fun deleteRecord(record: Record) = recordDao.delete(record)

    // Categories
    fun getCategoriesByType(type: Int): Flow<List<Category>> = categoryDao.getByType(type)
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAll()
    suspend fun insertCategory(category: Category): Long = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)
}
