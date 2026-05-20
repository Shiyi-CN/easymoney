package com.jiyixia.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.repository.RecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

// ── HomeUiState ────────────────────────────────────────────────────────────────
data class HomeUiState(
    val records: List<Record> = emptyList(),
    val categories: List<Category> = emptyList(),
    val pendingCount: Int = 0,
    val monthExpense: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthReimbursable: Double = 0.0,    // 本月待报销金额
    val totalReimbursable: Double = 0.0,    // 全部待报销金额
    val monthReimbursed: Double = 0.0,      // 本月已报销金额
    val reimbursableCount: Int = 0          // 本月待报销笔数
)

// ── StatsUiState ───────────────────────────────────────────────────────────────
data class StatsUiState(
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val expenseByCategory: List<CategorySum> = emptyList(),
    val incomeByCategory: List<CategorySum> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** 当前月份偏移（0 = 本月，-1 = 上月，…） */
    val monthOffset: Int = 0,
    // 报销统计
    val pendingReimbursable: Double = 0.0,   // 待报销
    val reimbursed: Double = 0.0,            // 已报销
    val reimbursableCount: Int = 0           // 待报销笔数
)

// ══════════════════════════════════════════════════════════════════════════════
//  HomeViewModel
// ══════════════════════════════════════════════════════════════════════════════
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: RecordRepository

    init {
        val db = (application as JiYiXiaApp).database
        repo = RecordRepository(db.recordDao(), db.categoryDao())
    }

    private val _selectedType = MutableStateFlow(0)

    // 当月起止
    private fun monthBounds(offset: Int = 0): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    val uiState: StateFlow<HomeUiState> = combine(
        // 前 5 个 Flow 合并
        combine(
            repo.getAllRecords(),
            repo.getAllCategories(),
            repo.getPendingConfirmCount(),
            repo.getSumByType(0, monthBounds().first, monthBounds().second),
            repo.getSumByType(1, monthBounds().first, monthBounds().second)
        ) { records, categories, pendingCount, expense, income ->
            arrayOf<Any?>(records, categories, pendingCount, expense, income)
        },
        // 报销 4 个 Flow 合并
        combine(
            repo.getReimbursableSumByDateRange(monthBounds().first, monthBounds().second),
            repo.getReimbursableSumAll(),
            repo.getReimbursedSumByDateRange(monthBounds().first, monthBounds().second),
            repo.getReimbursableCountByDateRange(monthBounds().first, monthBounds().second)
        ) { monthReim, totalReim, monthReimbursed, reimCount ->
            arrayOf(monthReim, totalReim, monthReimbursed, reimCount)
        }
    ) { arr, reimArr ->
        @Suppress("UNCHECKED_CAST")
        HomeUiState(
            records = arr[0] as List<Record>,
            categories = arr[1] as List<Category>,
            pendingCount = arr[2] as Int,
            monthExpense = (arr[3] as Double?) ?: 0.0,
            monthIncome = (arr[4] as Double?) ?: 0.0,
            monthReimbursable = (reimArr[0] as Double?) ?: 0.0,
            totalReimbursable = (reimArr[1] as Double?) ?: 0.0,
            monthReimbursed = (reimArr[2] as Double?) ?: 0.0,
            reimbursableCount = reimArr[3] as Int
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val selectedType: StateFlow<Int> = _selectedType.asStateFlow()
    val expenseCategories: StateFlow<List<Category>> = repo.getCategoriesByType(0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedType(type: Int) { _selectedType.value = type }

    fun addRecord(amount: Double, categoryId: Long, note: String, type: Int, isPendingConfirm: Boolean = false, confidence: Int = 100, isReimbursable: Boolean = false) {
        viewModelScope.launch {
            repo.insertRecord(
                Record(
                    type = type, amount = amount, categoryId = categoryId,
                    note = note, date = System.currentTimeMillis(),
                    isPendingConfirm = isPendingConfirm, confidence = confidence,
                    isReimbursable = isReimbursable
                )
            )
        }
    }

    fun deleteRecord(record: Record) { viewModelScope.launch { repo.deleteRecord(record) } }

    fun confirmRecord(record: Record) {
        viewModelScope.launch {
            repo.updateRecord(record.copy(isPendingConfirm = false, confidence = 100))
        }
    }

    /** 一键确认所有待确认记录 */
    fun confirmAllRecords() {
        viewModelScope.launch {
            val pending = uiState.value.records.filter { it.isPendingConfirm }
            pending.forEach { repo.updateRecord(it.copy(isPendingConfirm = false, confidence = 100)) }
        }
    }

    /** 标记已报销 / 取消已报销 */
    fun markReimbursed(record: Record) {
        viewModelScope.launch {
            val newState = !record.isReimbursed
            repo.setReimbursed(record.id, newState)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  StatsViewModel
// ══════════════════════════════════════════════════════════════════════════════
class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: RecordRepository

    init {
        val db = (application as JiYiXiaApp).database
        repo = RecordRepository(db.recordDao(), db.categoryDao())
    }

    private val _monthOffset = MutableStateFlow(0)

    private fun boundsFor(offset: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            add(Calendar.MONTH, offset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = _monthOffset.flatMapLatest { offset ->
        val (start, end) = boundsFor(offset)
        combine(
            // 核心 5 个 Flow
            combine(
                repo.getSumByType(0, start, end),
                repo.getSumByType(1, start, end),
                repo.getExpenseGroupByCategory(start, end),
                repo.getIncomeGroupByCategory(start, end),
                repo.getAllCategories()
            ) { expense, income, byExpCat, byIncCat, categories ->
                arrayOf<Any?>(expense, income, byExpCat, byIncCat, categories)
            },
            // 报销 3 个 Flow
            combine(
                repo.getReimbursableSumByDateRange(start, end),
                repo.getReimbursedSumByDateRange(start, end),
                repo.getReimbursableCountByDateRange(start, end)
            ) { pendingReim, reimbursed, count ->
                arrayOf(pendingReim, reimbursed, count)
            }
        ) { arr, reimArr ->
            @Suppress("UNCHECKED_CAST")
            StatsUiState(
                totalExpense = (arr[0] as Double?) ?: 0.0,
                totalIncome = (arr[1] as Double?) ?: 0.0,
                expenseByCategory = arr[2] as List<CategorySum>,
                incomeByCategory = arr[3] as List<CategorySum>,
                categories = arr[4] as List<Category>,
                monthOffset = offset,
                pendingReimbursable = (reimArr[0] as Double?) ?: 0.0,
                reimbursed = (reimArr[1] as Double?) ?: 0.0,
                reimbursableCount = reimArr[2] as Int
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun prevMonth() { _monthOffset.value -= 1 }
    fun nextMonth() { if (_monthOffset.value < 0) _monthOffset.value += 1 }
}
