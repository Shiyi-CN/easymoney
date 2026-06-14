package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.data.ThemePreferences
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.CategoryEmoji
import com.jiyixia.app.util.toAmountString
import com.jiyixia.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.*

// ── 格式化 ─────────────────────────────────────────────────────────────────────
private val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
private val dispSdf = SimpleDateFormat("M月d日", Locale.getDefault())

private fun formatDateGroup(dateStr: String): String {
    return try {
        val d = dateSdf.parse(dateStr) ?: return dateStr
        val today = dateSdf.format(Date())
        val yesterday = dateSdf.format(Date(System.currentTimeMillis() - 86_400_000))
        when (dateStr) {
            today -> "今天  ${dispSdf.format(d)}"
            yesterday -> "昨天  ${dispSdf.format(d)}"
            else -> dispSdf.format(d)
        }
    } catch (e: Exception) { dateStr }
}

// ── 设备品牌检测（用于语音引擎兼容引导）──────────────────────────────────

private fun formatAmount(amount: Long, type: Int): String {
    val prefix = if (type == 0) "-" else "+"
    return "$prefix${amount.toAmountString()}"
}

private fun daySum(records: List<Record>): String {
    val net = records.sumOf { if (it.type == 0) -it.amount else it.amount }
    return if (net >= 0) "+${net.toAmountString()}" else "-${(-net).toAmountString()}"
}

// ── FAB 状态枚举 ─────────────────────────────────────────────────────────────
private enum class FabState {
    NORMAL,   // 正常状态：52dp，100% 透明度
    FADED     // 半透明状态：52dp，40% 透明度
}

// ══════════════════════════════════════════════════════════════════════════════
//  HomeScreen
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToQuickRecord: () -> Unit = {},
    onNavigateToReimbursable: () -> Unit = {},
    vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current

    // 编辑记录状态
    var editingRecord by remember { mutableStateOf<Record?>(null) }

    // Snackbar支持
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 错误信息订阅
    val errorMessage by vm.errorMessage.collectAsState()
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearError()
        }
    }

    // 撤销删除的逻辑
    var pendingDeleteRecord by remember { mutableStateOf<Record?>(null) }

    // 左滑删除模式（0=直接删除, 1=显示按钮）
    val swipeDeleteMode by ThemePreferences.getSwipeDeleteMode(context).collectAsState(initial = 0)

    // FAB 状态管理
    var fabState by remember { mutableStateOf(FabState.NORMAL) }
    var fabOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // 屏幕尺寸（用于边界计算）
    var screenWidth by remember { mutableFloatStateOf(0f) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    // 记账完成后重置 FAB 状态
    LaunchedEffect(uiState.records.size) {
        if (fabState != FabState.NORMAL) {
            fabState = FabState.NORMAL
        }
    }

    // FAB 状态定时器
    LaunchedEffect(fabState, isDragging) {
        if (isDragging) return@LaunchedEffect

        when (fabState) {
            FabState.NORMAL -> {
                delay(5000)  // 5秒后变半透明
                fabState = FabState.FADED
            }
            FabState.FADED -> {
                // 不自动恢复，需要用户交互
            }
        }
    }

    // FAB 尺寸（像素）
    val fabSizePx = with(density) { 28.dp.toPx() }

    // 吸附到最近边缘
    fun snapToEdge(currentOffset: Offset): Offset {
        val distances = mapOf(
            "left" to currentOffset.x,
            "right" to screenWidth - currentOffset.x - fabSizePx,
            "bottom" to screenHeight - currentOffset.y - fabSizePx - 80f
        )

        val nearest = distances.minByOrNull { it.value }?.key ?: "right"

        return when (nearest) {
            "left" -> Offset(4f, currentOffset.y.coerceIn(4f, screenHeight - fabSizePx - 80f))
            "right" -> Offset(
                (screenWidth - fabSizePx - 4f).coerceAtLeast(4f),
                currentOffset.y.coerceIn(4f, screenHeight - fabSizePx - 80f)
            )
            "bottom" -> Offset(
                currentOffset.x.coerceIn(4f, screenWidth - fabSizePx - 4f),
                screenHeight - fabSizePx - 84f
            )
            else -> currentOffset
        }
    }

    // 限制在屏幕边界内
    fun constrainToScreen(offset: Offset): Offset {
        return Offset(
            offset.x.coerceIn(4f, screenWidth - fabSizePx - 4f),
            offset.y.coerceIn(4f, screenHeight - fabSizePx - 80f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                screenWidth = coordinates.size.width.toFloat()
                screenHeight = coordinates.size.height.toFloat()
                // 屏幕尺寸确定后，初始化 FAB 位置
                if (fabOffset == Offset.Zero) {
                    fabOffset = Offset(
                        screenWidth - with(density) { 28.dp.toPx() } - with(density) { 18.dp.toPx() },
                        screenHeight - with(density) { 28.dp.toPx() } - with(density) { 82.dp.toPx() }
                    )
                }
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 月度概览卡 - 顶格显示
            MonthOverviewCard(
                income = uiState.monthIncome,
                expense = uiState.monthExpense,
                reimbursable = uiState.monthReimbursable,
                reimbursed = uiState.monthReimbursed,
                reimbursableCount = uiState.reimbursableCount,
                onReimbursableClick = onNavigateToReimbursable
            )

            // ── 待确认 Banner - 固定在顶部
            if (uiState.pendingCount > 0) {
                PendingConfirmBanner(
                    count = uiState.pendingCount,
                    onConfirmAll = { vm.confirmAllRecords() }
                )
            }

            // ── 折叠状态（在 LazyColumn 外部，Composable 上下文中）
            val grouped = uiState.records
                .groupBy { dateSdf.format(Date(it.date)) }
                .toSortedMap(compareByDescending { it })

            // 默认展开今天
            val todayStr = dateSdf.format(Date())
            var expandedDates by remember { mutableStateOf(setOf(todayStr)) }

            // ── 记录列表 - 可滚动
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (uiState.records.isEmpty()) {
                    item { EmptyState() }
                } else {
                    grouped.forEach { (dateStr, dayRecords) ->
                        val isExpanded = expandedDates.contains(dateStr)

                        // 日期分组头（可点击展开/折叠）
                        item(key = "header_$dateStr") {
                            DateGroupHeader(
                                label = formatDateGroup(dateStr),
                                sum = daySum(dayRecords),
                                isExpanded = isExpanded,
                                onClick = {
                                    expandedDates = if (isExpanded) {
                                        expandedDates - dateStr
                                    } else {
                                        expandedDates + dateStr
                                    }
                                }
                            )
                        }

                        // 条目（展开时显示）
                        if (isExpanded) {
                            items(dayRecords, key = { it.id }) { record ->
                                SwipeToDismissRecordItem(
                                    record = record,
                                    categories = uiState.categories,
                                    onConfirm = { vm.confirmRecord(record) },
                                    onDelete = {
                                        pendingDeleteRecord = record
                                        vm.deleteRecord(record)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "已删除记录",
                                                actionLabel = "撤销",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                pendingDeleteRecord?.let { vm.restoreRecord(it) }
                                            }
                                            pendingDeleteRecord = null
                                        }
                                    },
                                    onReimbursable = { vm.markReimbursed(record) },
                                    onClick = { editingRecord = record },
                                    swipeDeleteMode = swipeDeleteMode
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Snackbar（浮动在 FAB 上方）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(horizontal = 32.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // ── 可拖动 FAB ──────────────────────────────────────────────────────────────
        val fabAlpha by animateFloatAsState(
            targetValue = if (fabState == FabState.NORMAL) 1f else 0.4f,
            animationSpec = tween(300),
            label = "fab_alpha"
        )

        val fabSize = 28.dp  // 缩小一半
        val fabIconSize = 14.dp

        // FAB 位置（如果没有拖动过，初始化到右下角）
        val defaultOffset = Offset(
            screenWidth - with(density) { fabSize.toPx() } - with(density) { 18.dp.toPx() },
            screenHeight - with(density) { fabSize.toPx() } - with(density) { 82.dp.toPx() }
        )
        val currentOffset = if (fabOffset == Offset.Zero) defaultOffset else fabOffset

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt())
                }
                .size(fabSize)
                .alpha(fabAlpha)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            fabState = FabState.NORMAL
                        },
                        onDragEnd = {
                            isDragging = false
                            fabOffset = snapToEdge(fabOffset)
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            fabOffset = Offset(
                                fabOffset.x + dragAmount.x,
                                fabOffset.y + dragAmount.y
                            )
                            fabOffset = constrainToScreen(fabOffset)
                        }
                    )
                }
                .clickable {
                    fabState = FabState.NORMAL
                    onNavigateToQuickRecord()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "记一笔",
                tint = Color.White,
                modifier = Modifier.size(fabIconSize)
            )
        }

        // 编辑记录对话框
        editingRecord?.let { record ->
            EditRecordDialog(
                record = record,
                categories = uiState.categories,
                onDismiss = { editingRecord = null },
                onSave = { updatedRecord ->
                    vm.updateRecord(updatedRecord)
                    editingRecord = null
                }
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  月度概览卡
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun MonthOverviewCard(
    income: Long,
    expense: Long,
    reimbursable: Long = 0L,
    reimbursed: Long = 0L,
    reimbursableCount: Int = 0,
    onReimbursableClick: () -> Unit = {}
) {
    val balance = income - expense
    val calendar = Calendar.getInstance()
    val label = "${calendar.get(Calendar.YEAR)} 年 ${calendar.get(Calendar.MONTH) + 1} 月 · 本月结余"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B6B4D), Color(0xFF2D8F68))
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 装饰圆
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-30).dp)
                .size(100.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.07f))
        )
        Column {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("记一下", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                balance.toAmountString(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5f).sp
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text("支出", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(expense.toAmountString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(modifier = Modifier.width(0.5.dp).height(32.dp).background(Color.White.copy(alpha = 0.25f)))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("收入", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(income.toAmountString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── 待报销行（有报销金额时显示）
            if (reimbursable > 0 || reimbursed > 0) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onReimbursableClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column {
                        // 待报销
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧾", fontSize = 13.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (reimbursableCount > 0) "待报销 $reimbursableCount 笔" else "待报销",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                reimbursable.toAmountString(),
                                color = Color(0xFFFFD54F),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // 已报销（有金额时显示）
                        if (reimbursed > 0) {
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("✅", fontSize = 13.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "本月已报销",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    reimbursed.toAmountString(),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  待确认 Banner（脉冲动画）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun PendingConfirmBanner(count: Int, onConfirmAll: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(WarningOrangeBg)
            .clickable(onClick = onConfirmAll)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(WarningOrange.copy(alpha = alpha))
        )
        Spacer(Modifier.width(8.dp))
        Text("$count 笔通知记录待确认", color = WarningOrange, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("全部确认 →", color = WarningOrange.copy(alpha = 0.7f), fontSize = 12.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  日期分组头（可点击展开/折叠）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun DateGroupHeader(
    label: String,
    sum: String,
    isExpanded: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 展开/折叠箭头
            Text(
                if (isExpanded) "▼" else "▶",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(sum, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  记录条目卡片
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun RecordItemCard(
    record: Record,
    categories: List<Category>,
    onConfirm: () -> Unit,
    onReimbursed: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val category = categories.find { it.id == record.categoryId }
    val isPending = record.isPendingConfirm
    val isExpense = record.type == 0
    val isReimbursed = record.note.startsWith("[已报销]")

    val cardBg = when {
        isPending -> Color(0xFFFFFDE7)  // 待确认：浅黄色
        isReimbursed -> Color(0xFFE8F5E9)  // 报销到账：浅绿色
        else -> MaterialTheme.colorScheme.surface  // 普通：白色
    }
    val iconBg = if (isExpense) Color(0xFFFFEBEE) else Color(0xFFE8F5EE)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 分类图标（圆角方形）
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(CategoryEmoji.get(category?.name), fontSize = 18.sp)
        }

        Spacer(Modifier.width(12.dp))

        // 中部信息
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.name ?: "未知",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isPending) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF57F17).copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text("待确认", color = Color(0xFFF57F17), fontSize = 10.sp)
                    }
                }
                if (isExpense && record.isReimbursable) {
                    Spacer(Modifier.width(6.dp))
                    val isReimbursed = record.isReimbursed
                    val badgeBg = if (isReimbursed)
                        Color(0xFF2E7D32).copy(alpha = 0.10f)
                    else
                        Color(0xFF1565C0).copy(alpha = 0.12f)
                    val badgeColor = if (isReimbursed)
                        Color(0xFF2E7D32)
                    else
                        Color(0xFF1565C0)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(badgeBg)
                            .clickable(onClick = onReimbursed)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            if (isReimbursed) "已报销" else "报销",
                            color = badgeColor,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            if (record.note.isNotBlank()) {
                Text(
                    record.note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 报销对象显示
            if (isExpense && record.isReimbursable && record.reimbursementTarget.isNotBlank()) {
                Text(
                    "→ ${record.reimbursementTarget}",
                    fontSize = 11.sp,
                    color = Color(0xFF1565C0).copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 金额
        Text(
            formatAmount(record.amount, record.type),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isExpense) ExpenseRed else IncomeGreen
        )

        // 待确认确认按钮
        if (isPending) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check, contentDescription = "确认",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  左滑删除记录条目（卡片右侧收缩，露出红色删除图标）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SwipeToDismissRecordItem(
    record: Record,
    categories: List<Category>,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onReimbursable: () -> Unit = {},
    onClick: () -> Unit = {},
    swipeDeleteMode: Int = 0 // 0=直接删除, 1=显示按钮
) {
    val density = LocalDensity.current
    val deleteWidthPx = with(density) { 80.dp.toPx() }  // 最大收缩宽度
    val threshold = with(density) { 50.dp.toPx() }      // 触发阈值
    val minDragPx = with(density) { 10.dp.toPx() }      // 最小拖拽距离，避免误触

    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentSwipeMode by rememberUpdatedState(swipeDeleteMode)
    val currentOnDelete by rememberUpdatedState(onDelete)

    // 收缩量（0 ~ deleteWidthPx）
    val shrinkPx = (-offsetX.value).coerceIn(0f, deleteWidthPx)
    val isExpanded = offsetX.value <= -threshold

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：记录卡片（宽度随滑动收缩）
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(currentSwipeMode) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (currentSwipeMode == 0) {
                                    if (offsetX.value < -threshold) {
                                        currentOnDelete()
                                        offsetX.snapTo(0f)
                                    } else {
                                        offsetX.animateTo(0f, tween(200))
                                    }
                                } else {
                                    if (offsetX.value < -threshold) {
                                        offsetX.animateTo(-deleteWidthPx, tween(200))
                                    } else {
                                        offsetX.animateTo(0f, tween(200))
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-deleteWidthPx, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            RecordItemCard(
                record = record,
                categories = categories,
                onConfirm = onConfirm,
                onReimbursed = onReimbursable,
                onClick = {
                    if (isExpanded) {
                        scope.launch { offsetX.animateTo(0f, tween(200)) }
                    } else {
                        onClick()
                    }
                }
            )
        }

        // 右侧：红色删除图标（与记录内容垂直居中对齐）
        if (shrinkPx > 0f) {
            Box(
                modifier = Modifier
                    .width(with(density) { shrinkPx.toDp() }),
                contentAlignment = Alignment.Center
            ) {
                if (currentSwipeMode == 1) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "确认删除",
                        tint = Color(0xFFE53935),
                        modifier = Modifier
                            .size(28.dp)
                            .clickable {
                                scope.launch { offsetX.animateTo(0f, tween(200)) }
                                currentOnDelete()
                            }
                    )
                } else {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  空状态
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🪴", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("还没有记录，记一笔吧", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    }
}

// ── ViewModel Factory ──────────────────────────────────────────────────────────
class HomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(application) as T
    }
}
