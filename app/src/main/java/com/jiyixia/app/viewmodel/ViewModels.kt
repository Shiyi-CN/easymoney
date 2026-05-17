package com.jiyixia.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.repository.RecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class HomeUiState(
    val records: List<Record> = emptyList(),
    val categories: List<Category> = emptyList(),
    val pendingCount: Int = 0
)

data class StatsUiState(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val expenseByCategory: List<CategorySum> = emptyList(),
    val categories: List<Category> = emptyList()
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: RecordRepository

    init {
        val db = (application as JiYiXiaApp).database
        repo = RecordRepository(db.recordDao(), db.categoryDao())
    }

    private val _selectedType = MutableStateFlow(0) // 0=支出
    val uiState: StateFlow<HomeUiState> = combine(
        repo.getAllRecords(),
        repo.getAllCategories(),
        repo.getPendingConfirmCount()
    ) { records, categories, pendingCount ->
        HomeUiState(
            records = records,
            categories = categories,
            pendingCount = pendingCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val selectedType: StateFlow<Int> = _selectedType.asStateFlow()
    val expenseCategories: StateFlow<List<Category>> = repo.getCategoriesByType(0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedType(type: Int) { _selectedType.value = type }

    fun addRecord(amount: Double, categoryId: Long, note: String, type: Int, isPendingConfirm: Boolean = false, confidence: Int = 100) {
        viewModelScope.launch {
            repo.insertRecord(
                Record(
                    type = type,
                    amount = amount,
                    categoryId = categoryId,
                    note = note,
                    date = System.currentTimeMillis(),
                    isPendingConfirm = isPendingConfirm,
                    confidence = confidence
                )
            )
        }
    }

    fun deleteRecord(record: Record) {
        viewModelScope.launch { repo.deleteRecord(record) }
    }

    fun confirmRecord(record: Record) {
        viewModelScope.launch {
            repo.updateRecord(record.copy(isPendingConfirm = false, confidence = 100))
        }
    }
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: RecordRepository

    init {
        val db = (application as JiYiXiaApp).database
        repo = RecordRepository(db.recordDao(), db.categoryDao())
    }

    private val calendar = Calendar.getInstance()

    private val monthStart: Long
        get() = calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val monthEnd: Long
        get() = calendar.apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

    val uiState: StateFlow<StatsUiState> = combine(
        repo.getSumByType(0, monthStart, monthEnd),
        repo.getSumByType(1, monthStart, monthEnd),
        repo.getExpenseGroupByCategory(monthStart, monthEnd),
        repo.getAllCategories()
    ) { expense, income, byCategory, categories ->
        StatsUiState(
            totalExpense = expense ?: 0.0,
            totalIncome = income ?: 0.0,
            expenseByCategory = byCategory,
            categories = categories
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())
}
