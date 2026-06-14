package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.RecordMode
import com.jiyixia.app.data.ThemePreferences
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.CategoryEmoji
import com.jiyixia.app.domain.usecase.SmartParseUseCase
import com.jiyixia.app.domain.usecase.InputValidationUseCase
import com.jiyixia.app.domain.usecase.ValidationResult
import com.jiyixia.app.util.toCents
import com.jiyixia.app.viewmodel.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 导入 HomeViewModelFactory
import com.jiyixia.app.ui.screens.HomeViewModelFactory

/**
 * 快速记账界面（全屏）
 *
 * 设计理念：
 * 1. 2步完成记账：输入金额 → 选择分类
 * 2. 自动保存：输入金额后延迟自动保存
 * 3. 记完即退：保存后自动返回
 * 4. 智能默认：记住上次使用的分类
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRecordScreen(
    onNavigateBack: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by vm.uiState.collectAsState()
    val selectedType by vm.selectedType.collectAsState()

    // 读取记账模式设置
    val context = LocalContext.current
    val recordMode by ThemePreferences.getRecordMode(context).collectAsState(initial = RecordMode.CONFIRM)
    val isQuickMode = recordMode == RecordMode.QUICK

    // 记账状态
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(0L) }
    var currentType by remember { mutableStateOf(selectedType) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // 协程作用域（修复：使用 Job 来取消之前的协程）
    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Snackbar支持
    val snackbarHostState = remember { SnackbarHostState() }

    // 错误信息订阅
    val errorMessage by vm.errorMessage.collectAsState()
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    // 按当前类型筛选分类（修复：使用 uiState.categories 作为 key）
    val displayCategories = remember(currentType, uiState.categories) {
        if (currentType == 0) {
            uiState.categories.filter { it.type == 0 }
        } else {
            uiState.categories.filter { it.type == 1 }
        }
    }

    // 智能默认：记住上次使用的分类（只在进入页面时执行一次）
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.records, displayCategories) {
        // 只在首次进入页面时执行，避免手动切换时被覆盖
        if (hasInitialized) return@LaunchedEffect
        if (displayCategories.isEmpty()) return@LaunchedEffect

        // 优先级1：使用上次记录的分类（但不自动切换收入/支出类型）
        val lastRecord = uiState.records.firstOrNull()
        if (lastRecord != null && displayCategories.any { it.id == lastRecord.categoryId }) {
            selectedCategoryId = lastRecord.categoryId
            // 不设置 currentType，保持默认的「支出」
            hasInitialized = true
            return@LaunchedEffect
        }

        // 优先级2：使用最常用的分类
        val categoryFrequency = uiState.records
            .groupBy { it.categoryId }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        if (categoryFrequency.isNotEmpty()) {
            val mostUsedCategoryId = categoryFrequency.first().first
            if (displayCategories.any { it.id == mostUsedCategoryId }) {
                selectedCategoryId = mostUsedCategoryId
                hasInitialized = true
                return@LaunchedEffect
            }
        }

        // 优先级3：使用第一个分类
        selectedCategoryId = displayCategories.first().id
        hasInitialized = true
    }

    // 自动保存逻辑（仅极速模式生效）
    LaunchedEffect(amountText, isQuickMode) {
        // 确认模式下不自动保存
        if (!isQuickMode) return@LaunchedEffect

        // 取消之前的保存任务
        saveJob?.cancel()
        validationError = null

        // 等待一小段时间，确保状态更新
        delay(100)

        if (amountText.isNotBlank()) {
            // 使用 SmartParseUseCase 统一解析逻辑
            val nameToId = displayCategories.associate { it.name to it.id }
            val defaultCategoryId = displayCategories.firstOrNull()?.id ?: 0L
            val parsed = SmartParseUseCase.parse(
                text = amountText,
                categoryNameToId = nameToId,
                defaultCategoryId = defaultCategoryId
            )

            if (parsed != null && parsed.amount > 0) {
                // 启动新的保存任务
                saveJob = scope.launch {
                    // 延迟500ms后自动保存（给用户修正时间）
                    delay(500)

                    // 再次解析，确保用户没有修改
                    val currentParsed = SmartParseUseCase.parse(
                        text = amountText,
                        categoryNameToId = nameToId,
                        defaultCategoryId = defaultCategoryId
                    )

                    if (currentParsed != null && currentParsed.amount > 0) {
                        // 输入验证
                        val amountCents = currentParsed.amount.toCents()
                        val amountValidation = InputValidationUseCase.validateAmount(amountCents)
                        val noteValidation = InputValidationUseCase.validateNote(currentParsed.note)

                        when {
                            amountValidation is ValidationResult.Error -> {
                                validationError = amountValidation.message
                                return@launch
                            }
                            noteValidation is ValidationResult.Error -> {
                                validationError = noteValidation.message
                                return@launch
                            }
                        }

                        isSaving = true
                        val currentCategory = displayCategories.find { it.id == currentParsed.categoryId }
                            ?: displayCategories.find { it.id == selectedCategoryId }

                        if (currentCategory != null) {
                            val recordType = if (currentParsed.isExpense) 0 else 1
                            vm.addRecord(
                                amount = amountCents,
                                categoryId = currentCategory.id,
                                note = currentParsed.note,
                                type = recordType,
                                isReimbursable = currentParsed.isReimbursable,
                                reimbursementTarget = currentParsed.reimbursementTarget
                            )

                            // 记录成功后，更新当前类型（下一笔自动跟上）
                            currentType = recordType

                            // 显示成功提示
                            showSuccess = true
                            delay(1000)

                            // 返回上一页
                            onNavigateBack()
                        }
                        isSaving = false
                    }
                }
            } else if (parsed != null && parsed.amount <= 0) {
                // 解析成功但金额为0或负数
                validationError = "请输入有效金额"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("快速记账", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 金额输入区域
            AmountInputSection(
                amountText = amountText,
                onAmountChange = { amountText = it },
                currentType = currentType,
                onTypeChange = { currentType = it }
            )

            Spacer(Modifier.height(24.dp))

            // 分类选择区域
            CategorySelectionSection(
                categories = displayCategories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelect = { selectedCategoryId = it }
            )

            Spacer(Modifier.weight(1f))

            // 验证错误提示
            if (validationError != null) {
                Text(
                    "⚠️ $validationError",
                    color = ExpenseRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // 状态提示
            if (isSaving) {
                Text(
                    "保存中...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (showSuccess) {
                Text(
                    "✓ 已保存",
                    color = IncomeGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 确认模式下显示保存按钮
            if (!isQuickMode) {
                Button(
                    onClick = {
                        // 手动保存逻辑
                        scope.launch {
                            val nameToId = displayCategories.associate { it.name to it.id }
                            val defaultCategoryId = displayCategories.firstOrNull()?.id ?: 0L
                            val parsed = SmartParseUseCase.parse(
                                text = amountText,
                                categoryNameToId = nameToId,
                                defaultCategoryId = defaultCategoryId
                            )

                            if (parsed != null && parsed.amount > 0) {
                                val amountCents = parsed.amount.toCents()
                                val amountValidation = InputValidationUseCase.validateAmount(amountCents)
                                val noteValidation = InputValidationUseCase.validateNote(parsed.note)

                                when {
                                    amountValidation is ValidationResult.Error -> {
                                        validationError = amountValidation.message
                                        return@launch
                                    }
                                    noteValidation is ValidationResult.Error -> {
                                        validationError = noteValidation.message
                                        return@launch
                                    }
                                }

                                isSaving = true
                                val currentCategory = displayCategories.find { it.id == parsed.categoryId }
                                    ?: displayCategories.find { it.id == selectedCategoryId }

                                if (currentCategory != null) {
                                    val recordType = if (parsed.isExpense) 0 else 1
                                    vm.addRecord(
                                        amount = amountCents,
                                        categoryId = currentCategory.id,
                                        note = parsed.note,
                                        type = recordType,
                                        isReimbursable = parsed.isReimbursable,
                                        reimbursementTarget = parsed.reimbursementTarget
                                    )

                                    // 记录成功后，更新当前类型（下一笔自动跟上）
                                    currentType = recordType

                                    showSuccess = true
                                    delay(1000)
                                    onNavigateBack()
                                }
                                isSaving = false
                            } else {
                                validationError = "请输入有效金额"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = amountText.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentType == 0) ExpenseRed else IncomeGreen
                    )
                ) {
                    Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 金额输入区域
 */
@Composable
private fun AmountInputSection(
    amountText: String,
    onAmountChange: (String) -> Unit,
    currentType: Int,
    onTypeChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // 收支切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Surface2)
                .padding(3.dp)
        ) {
            listOf(0 to "支出", 1 to "收入").forEach { (type, label) ->
                val isSelected = currentType == type
                val bg = when {
                    !isSelected -> Color.Transparent
                    type == 0 -> ExpenseRed
                    else -> IncomeGreen
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTypeChange(type) }
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 金额输入（允许输入中文和数字，用于文本输入自动分类）
        OutlinedTextField(
            value = amountText,
            onValueChange = { v ->
                if (v.length <= 20)  // 允许输入更长的文本
                    onAmountChange(v)
            },
            label = { Text("金额或描述（如：早餐10元）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),  // 改为文本键盘
            textStyle = LocalTextStyle.current.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (currentType == 0) ExpenseRed else IncomeGreen
            )
        )
    }
}

/**
 * 分类选择区域（支持滚动）
 */
@Composable
private fun CategorySelectionSection(
    categories: List<Category>,
    selectedCategoryId: Long,
    onCategorySelect: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "选择分类",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(10.dp))

        // 分类网格（支持滚动）
        val cols = 4
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 200.dp)  // 限制最大高度
        ) {
            items(categories.size) { idx ->
                val cat = categories[idx]
                val isSelected = selectedCategoryId == cat.id
                Box(
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .clickable { onCategorySelect(cat.id) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else Surface2
                        )
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(CategoryEmoji.get(cat.name), fontSize = 20.sp)
                        Text(
                            cat.name,
                            fontSize = 11.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
