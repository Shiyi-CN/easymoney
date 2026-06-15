package com.jiyixia.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.domain.usecase.InputValidationUseCase
import com.jiyixia.app.repository.RecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

// ── HomeUiState ────────────────────────────────────────────────────────────────
data class HomeUiState(
    val records: List<Record> = emptyList(),
    val categories: List<Category> = emptyList(),
    val pendingCount: Int = 0,
    val monthExpense: Long = 0L,
    val monthIncome: Long = 0L,
    val monthReimbursable: Long = 0L,    // 本月待报销金额
    val totalReimbursable: Long = 0L,    // 全部待报销金额
    val monthReimbursed: Long = 0L,      // 本月已报销金额
    val reimbursableCount: Int = 0,      // 本月待报销笔数
    val streakDays: Int = 0,             // 连续记账天数
    val isReimbursing: Boolean = false   // 报销操作进行中
)

// ── StatsUiState ───────────────────────────────────────────────────────────────
data class StatsUiState(
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val expenseByCategory: List<CategorySum> = emptyList(),
    val incomeByCategory: List<CategorySum> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** 当前月份偏移（0 = 本月，-1 = 上月，…） */
    val monthOffset: Int = 0,
    // 报销统计
    val pendingReimbursable: Long = 0L,   // 待报销
    val reimbursed: Long = 0L,            // 已报销
    val reimbursableCount: Int = 0        // 待报销笔数
)

// ── 内部数据类，用于combine类型安全 ──────────────────────────────────────────────
private data class HomeFlows(
    val records: List<Record>,
    val categories: List<Category>,
    val pendingCount: Int,
    val monthExpense: Long?,
    val monthIncome: Long?
)

private data class HomeReimbursementFlows(
    val monthReimbursable: Long?,
    val totalReimbursable: Long?,
    val monthReimbursed: Long?,
    val reimbursableCount: Int
)

private data class StatsFlows(
    val totalExpense: Long?,
    val totalIncome: Long?,
    val expenseByCategory: List<CategorySum>,
    val incomeByCategory: List<CategorySum>,
    val categories: List<Category>
)

private data class StatsReimbursementFlows(
    val pendingReimbursable: Long?,
    val reimbursed: Long?,
    val reimbursableCount: Int
)

// ══════════════════════════════════════════════════════════════════════════════
//  HomeViewModel
// ══════════════════════════════════════════════════════════════════════════════
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo: RecordRepository

    private val _selectedType = MutableStateFlow(0)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _streakDays = MutableStateFlow(0)
    private val _isReimbursing = MutableStateFlow(false)  // 报销操作进行中

    init {
        val db = (application as JiYiXiaApp).database
        repo = RecordRepository(db.recordDao(), db.categoryDao())
        // 计算连续记账天数
        viewModelScope.launch {
            _streakDays.value = repo.getStreakDays()
        }
    }

    /** 错误信息流，UI层可以订阅显示Snackbar */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 清除错误信息 */
    fun clearError() {
        _errorMessage.value = null
    }

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
            HomeFlows(records, categories, pendingCount, expense, income)
        },
        // 报销 4 个 Flow 合并
        combine(
            repo.getReimbursableSumByDateRange(monthBounds().first, monthBounds().second),
            repo.getReimbursableSumAll(),
            repo.getReimbursedSumByDateRange(monthBounds().first, monthBounds().second),
            repo.getReimbursableCountByDateRange(monthBounds().first, monthBounds().second)
        ) { monthReim, totalReim, monthReimbursed, reimCount ->
            HomeReimbursementFlows(monthReim, totalReim, monthReimbursed, reimCount)
        },
        // 连续天数 + 报销操作状态
        combine(_streakDays, _isReimbursing) { streak, isReimbursing ->
            Pair(streak, isReimbursing)
        }
    ) { flows, reimFlows, (streakDays, isReimbursing) ->
        HomeUiState(
            records = flows.records,
            categories = flows.categories,
            pendingCount = flows.pendingCount,
            monthExpense = flows.monthExpense ?: 0L,
            monthIncome = flows.monthIncome ?: 0L,
            monthReimbursable = reimFlows.monthReimbursable ?: 0L,
            totalReimbursable = reimFlows.totalReimbursable ?: 0L,
            monthReimbursed = reimFlows.monthReimbursed ?: 0L,
            reimbursableCount = reimFlows.reimbursableCount,
            streakDays = streakDays,
            isReimbursing = isReimbursing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val selectedType: StateFlow<Int> = _selectedType.asStateFlow()
    val expenseCategories: StateFlow<List<Category>> = repo.getCategoriesByType(0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSelectedType(type: Int) { _selectedType.value = type }

    fun addRecord(amount: Long, categoryId: Long, note: String, type: Int, isPendingConfirm: Boolean = false, confidence: Int = 100, isReimbursable: Boolean = false, reimbursementTarget: String = "") {
        // 输入验证
        if (!InputValidationUseCase.isRecordValid(amount, categoryId, note)) {
            _errorMessage.value = "输入数据无效，请检查金额、分类和备注"
            return
        }

        viewModelScope.launch {
            try {
                repo.insertRecord(
                    Record(
                        type = type, amount = amount, categoryId = categoryId,
                        note = note, date = System.currentTimeMillis(),
                        isPendingConfirm = isPendingConfirm, confidence = confidence,
                        isReimbursable = isReimbursable,
                        reimbursementTarget = reimbursementTarget
                    )
                )
            } catch (e: Exception) {
                _errorMessage.value = "添加记录失败：${e.message}"
            }
        }
    }

    fun deleteRecord(record: Record) {
        viewModelScope.launch {
            try {
                repo.deleteRecord(record)
            } catch (e: Exception) {
                _errorMessage.value = "删除记录失败：${e.message}"
            }
        }
    }

    fun restoreRecord(record: Record) {
        viewModelScope.launch {
            try {
                repo.insertRecord(record)
            } catch (e: Exception) {
                _errorMessage.value = "恢复记录失败：${e.message}"
            }
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            try {
                repo.updateRecord(record)
            } catch (e: Exception) {
                _errorMessage.value = "更新记录失败：${e.message}"
            }
        }
    }

    fun confirmRecord(record: Record) {
        viewModelScope.launch {
            try {
                repo.updateRecord(record.copy(isPendingConfirm = false, confidence = 100))
            } catch (e: Exception) {
                _errorMessage.value = "确认记录失败：${e.message}"
            }
        }
    }

    /** 一键确认所有待确认记录 */
    fun confirmAllRecords() {
        viewModelScope.launch {
            try {
                val pending = uiState.value.records.filter { it.isPendingConfirm }
                pending.forEach { repo.updateRecord(it.copy(isPendingConfirm = false, confidence = 100)) }
            } catch (e: Exception) {
                _errorMessage.value = "确认记录失败：${e.message}"
            }
        }
    }

    /** 标记已报销 / 取消已报销 */
    fun markReimbursed(record: Record) {
        // 防止重复操作
        if (_isReimbursing.value) return

        viewModelScope.launch {
            _isReimbursing.value = true
            try {
                val wasReimbursed = record.isReimbursed
                val newState = !wasReimbursed

                // 从"未报销"变为"已报销"时，创建收入记录
                if (!wasReimbursed && newState) {
                    // 检查是否已有对应的报销到账记录（防止重复）
                    val existingRecords = uiState.value.records
                    val hasIncomeRecord = existingRecords.any {
                        it.reimbursementSourceId == record.id
                    }

                    val incomeRecord = if (!hasIncomeRecord) {
                        val categoryName = uiState.value.categories
                            .find { it.id == record.categoryId }?.name ?: "报销"
                        Record(
                            type = 1,  // 收入
                            amount = record.amount,
                            categoryId = record.categoryId,
                            note = "[已报销] ${record.note.ifBlank { categoryName }}",
                            date = System.currentTimeMillis(),
                            isReimbursable = false,
                            reimbursementTarget = "",
                            reimbursementSourceId = record.id
                        )
                    } else null

                    // 使用事务：标记报销 + 创建收入记录
                    repo.markReimbursedWithIncome(record.id, newState, incomeRecord)
                }
                // 从"已报销"变为"未报销"时，删除对应的收入记录
                else if (wasReimbursed && !newState) {
                    val existingRecords = uiState.value.records
                    val incomeRecord = existingRecords.find {
                        it.reimbursementSourceId == record.id
                    }

                    if (incomeRecord != null) {
                        // 使用事务：撤销报销 + 删除收入记录
                        repo.undoReimbursedWithIncome(record.id, incomeRecord.id)
                    } else {
                        // 找不到收入记录，只更新报销状态
                        repo.setReimbursed(record.id, false)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "标记报销失败：${e.message}"
            } finally {
                _isReimbursing.value = false
            }
        }
    }

    // ── 分类管理 ──

    fun addCategory(category: Category) {
        viewModelScope.launch {
            try {
                repo.insertCategory(category)
            } catch (e: Exception) {
                _errorMessage.value = "添加分类失败：${e.message}"
            }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            try {
                repo.updateCategory(category)
            } catch (e: Exception) {
                _errorMessage.value = "更新分类失败：${e.message}"
            }
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            try {
                repo.deleteCategory(category)
            } catch (e: Exception) {
                _errorMessage.value = "删除分类失败：${e.message}"
            }
        }
    }

    // ── 搜索/筛选 ──

    private val _searchResults = MutableStateFlow<List<Record>>(emptyList())
    val searchResults: StateFlow<List<Record>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchRecords(
        minAmount: Long? = null,
        maxAmount: Long? = null,
        categoryId: Long? = null,
        keyword: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ) {
        _isSearching.value = true
        viewModelScope.launch {
            try {
                val results = repo.searchRecords(minAmount, maxAmount, categoryId, keyword, startDate, endDate)
                _searchResults.value = results
            } catch (e: Exception) {
                _errorMessage.value = "搜索失败：${e.message}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _isSearching.value = false
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
                StatsFlows(expense, income, byExpCat, byIncCat, categories)
            },
            // 报销 3 个 Flow
            combine(
                repo.getReimbursableSumByDateRange(start, end),
                repo.getReimbursedSumByDateRange(start, end),
                repo.getReimbursableCountByDateRange(start, end)
            ) { pendingReim, reimbursed, count ->
                StatsReimbursementFlows(pendingReim, reimbursed, count)
            }
        ) { flows, reimFlows ->
            StatsUiState(
                totalExpense = flows.totalExpense ?: 0L,
                totalIncome = flows.totalIncome ?: 0L,
                expenseByCategory = flows.expenseByCategory,
                incomeByCategory = flows.incomeByCategory,
                categories = flows.categories,
                monthOffset = offset,
                pendingReimbursable = reimFlows.pendingReimbursable ?: 0L,
                reimbursed = reimFlows.reimbursed ?: 0L,
                reimbursableCount = reimFlows.reimbursableCount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsUiState())

    fun prevMonth() { _monthOffset.value -= 1 }
    fun nextMonth() { if (_monthOffset.value < 0) _monthOffset.value += 1 }

    // ── 近 6 个月趋势数据 ──

    data class MonthTrendData(
        val month: String,  // "6月"
        val expense: Long,
        val income: Long
    )

    private val _trendData = MutableStateFlow<List<MonthTrendData>>(emptyList())
    val trendData: StateFlow<List<MonthTrendData>> = _trendData.asStateFlow()

    init {
        loadTrendData()
    }

    private fun loadTrendData() {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("M月", java.util.Locale.getDefault())
            val cal = java.util.Calendar.getInstance()
            val result = mutableListOf<MonthTrendData>()

            // 从当前月往前数 6 个月
            for (i in 5 downTo 0) {
                val tempCal = java.util.Calendar.getInstance()
                tempCal.add(java.util.Calendar.MONTH, -i)
                val month = sdf.format(tempCal.time)

                val (start, end) = boundsFor(-i)
                val expense = repo.getSumByTypeOnce(0, start, end)
                val income = repo.getSumByTypeOnce(1, start, end)

                result.add(MonthTrendData(month, expense, income))
            }

            _trendData.value = result
        }
    }
}
