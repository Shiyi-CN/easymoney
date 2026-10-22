package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.jiyixia.app.data.RecordMode
import com.jiyixia.app.data.ThemePreferences
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.CategoryEmoji
import com.jiyixia.app.domain.usecase.SmartParseUseCase
import com.jiyixia.app.util.KeywordMappingManager
import com.jiyixia.app.domain.usecase.InputValidationUseCase
import com.jiyixia.app.domain.usecase.ValidationResult
import com.jiyixia.app.ui.components.XfyunVoiceDialog
import com.jiyixia.app.util.toCents
import com.jiyixia.app.viewmodel.HomeViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    initialVoiceText: String = "",
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

    // 补记：手动选择的记账日期（null = 今天）；语音里的"昨天/前天"由 dateOffset 自动换算
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 语音记账：讯飞离线语音识别对话框
    val amountFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showVoiceDialog by remember { mutableStateOf(false) }

    // 启动语音记账
    val startVoiceRecognition: () -> Unit = {
        showVoiceDialog = true
    }

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

    // 统一的自动保存函数
    fun autoSave() {
        // 取消之前的保存任务
        saveJob?.cancel()
        validationError = null

        if (amountText.isBlank()) return

        // 使用 SmartParseUseCase 统一解析逻辑
        val nameToId = displayCategories.associate { it.name to it.id }
        val defaultCategoryId = displayCategories.firstOrNull()?.id ?: 0L
        val parsed = SmartParseUseCase.parse(
            text = amountText,
            categoryNameToId = nameToId,
            defaultCategoryId = defaultCategoryId,
            keywordMappings = KeywordMappingManager.getAll()
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
                    defaultCategoryId = defaultCategoryId,
                    keywordMappings = KeywordMappingManager.getAll()
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
                            reimbursementTarget = currentParsed.reimbursementTarget,
                            // 补记：手动选日期优先，其次语音"昨天/前天"偏移
                            date = resolveRecordDate(selectedDate, currentParsed.dateOffset)
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

    // 语音输入触发的保存标志（等分类加载完成后再执行自动保存）
    var voiceSaveRequested by remember { mutableStateOf(false) }

    // 自动保存逻辑（仅极速模式生效）
    LaunchedEffect(amountText, isQuickMode) {
        // 确认模式下不自动保存
        if (!isQuickMode) return@LaunchedEffect
        // 语音输入触发的保存由专属 LaunchedEffect 处理，避免重复
        if (voiceSaveRequested) return@LaunchedEffect
        autoSave()
    }

    // 从 HomeScreen 长按 FAB 带入的语音识别结果
    LaunchedEffect(initialVoiceText) {
        if (initialVoiceText.isNotBlank()) {
            amountText = initialVoiceText
            voiceSaveRequested = true
        }
    }

    // 当语音保存请求已发起且分类列表就绪时，执行自动保存
    LaunchedEffect(voiceSaveRequested, displayCategories) {
        if (voiceSaveRequested && displayCategories.isNotEmpty()) {
            voiceSaveRequested = false
            autoSave()
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
                onTypeChange = { currentType = it },
                onVoiceInput = { startVoiceRecognition() },
                focusRequester = amountFocusRequester
            )

            // 补记日期入口：默认今天，点击可选过去日期补记漏掉的账
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface2)
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📅", fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedDate?.let { "补记：${dispSdf.format(Date(it))}" } ?: "记账日期：今天",
                    fontSize = 13.sp,
                    color = if (selectedDate != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedDate != null) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(Modifier.weight(1f))
                if (selectedDate != null) {
                    Text(
                        "恢复今天",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { selectedDate = null }
                    )
                } else {
                    Text("点击选择 ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // 补记日期选择弹窗（只能选今天及以前——补记过去的账）
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDate ?: System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            // DatePicker 返回 UTC 0 点毫秒，需转成本地日期 + 当前时分，否则时区下会偏一天
                            datePickerState.selectedDateMillis?.let { utcMillis ->
                                val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                    timeInMillis = utcMillis
                                }
                                val now = Calendar.getInstance()
                                selectedDate = Calendar.getInstance().apply {
                                    set(
                                        utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH),
                                        utcCal.get(Calendar.DAY_OF_MONTH),
                                        now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE),
                                        now.get(Calendar.SECOND)
                                    )
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            }
                            showDatePicker = false
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                    }
                ) {
                    DatePicker(state = datePickerState, title = null, headline = null)
                }
            }

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
                                defaultCategoryId = defaultCategoryId,
                                keywordMappings = KeywordMappingManager.getAll()
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
                                        reimbursementTarget = parsed.reimbursementTarget,
                                        // 补记：手动选日期优先，其次语音"昨天/前天"偏移
                                        date = resolveRecordDate(selectedDate, parsed.dateOffset)
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

    // 讯飞离线语音识别对话框
    if (showVoiceDialog) {
        XfyunVoiceDialog(
            onResult = { text ->
                showVoiceDialog = false
                amountText = text
            },
            onDismiss = { showVoiceDialog = false }
        )
    }
}

/** 补记日期显示格式（如 "6月1日"） */
private val dispSdf = SimpleDateFormat("M月d日", Locale.getDefault())

/**
 * 解析最终记账日期
 * @param selectedDate 手动选择的补记日期（已含当前时分）
 * @param dateOffset 语音解析出的偏移（-1=昨天等）；手动选择优先
 * @return null = 记当前时间
 */
private fun resolveRecordDate(selectedDate: Long?, dateOffset: Int): Long? {
    if (selectedDate != null) return selectedDate
    if (dateOffset == 0) return null
    return Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dateOffset) }.timeInMillis
}

/**
 * 金额输入区域
 */
@Composable
private fun AmountInputSection(
    amountText: String,
    onAmountChange: (String) -> Unit,
    currentType: Int,
    onTypeChange: (Int) -> Unit,
    onVoiceInput: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() }
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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),  // 改为文本键盘
            textStyle = LocalTextStyle.current.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            ),
            trailingIcon = {
                IconButton(onClick = onVoiceInput) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "语音记账",
                        tint = if (currentType == 0) ExpenseRed else IncomeGreen
                    )
                }
            },
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
