package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.VoiceCategorizer
import com.jiyixia.app.util.VoiceRecognitionManager
import com.jiyixia.app.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

// ── 分类 Emoji 映射（轻量，不引入图片资源）──────────────────────────────────────
private val categoryEmojiMap = mapOf(
    // 支出分类
    "餐饮" to "🍜", "交通" to "🚇", "购物" to "🛒", "居住" to "🏠",
    "娱乐" to "🎮", "医疗" to "🏥", "教育" to "📚", "通讯" to "📱",
    "社交" to "🤝", "美容" to "💄", "宠物" to "🐱", "办公" to "💼",
    "维修" to "🔧", "捐赠" to "❤️", "其他" to "📋",
    // 收入分类
    "工资" to "💰", "奖金" to "🏆", "理财" to "📈", "兼职" to "💼",
    "红包" to "🧧", "报销" to "🧾", "租金" to "🏠", "退款" to "↩️",
    "中奖" to "🎰"
)
private fun categoryEmoji(name: String?) = categoryEmojiMap[name] ?: "💸"

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

private fun formatAmount(amount: Double, type: Int): String {
    val prefix = if (type == 0) "-" else "+"
    return "$prefix¥${String.format("%.2f", amount)}"
}

private fun daySum(records: List<Record>): String {
    val net = records.sumOf { if (it.type == 0) -it.amount else it.amount }
    return if (net >= 0) "+¥${String.format("%.2f", net)}" else "-¥${String.format("%.2f", -net)}"
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

    // 编辑记录状态
    var editingRecord by remember { mutableStateOf<Record?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 顶部标题栏
            TopAppBar(
                title = { Text("记一下", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                // ── 月度概览卡
                item {
                    MonthOverviewCard(
                        income = uiState.monthIncome,
                        expense = uiState.monthExpense,
                        reimbursable = uiState.monthReimbursable,
                        reimbursed = uiState.monthReimbursed,
                        reimbursableCount = uiState.reimbursableCount,
                        onReimbursableClick = onNavigateToReimbursable
                    )
                }

                // ── 待确认 Banner
                if (uiState.pendingCount > 0) {
                    item {
                        PendingConfirmBanner(
                            count = uiState.pendingCount,
                            onConfirmAll = { vm.confirmAllRecords() }
                        )
                    }
                }

                // ── 按日期分组列表
                if (uiState.records.isEmpty()) {
                    item { EmptyState() }
                } else {
                    val grouped = uiState.records
                        .groupBy { dateSdf.format(Date(it.date)) }
                        .toSortedMap(compareByDescending { it })

                    grouped.forEach { (dateStr, dayRecords) ->
                        // 日期分组头
                        item(key = "header_$dateStr") {
                            DateGroupHeader(
                                label = formatDateGroup(dateStr),
                                sum = daySum(dayRecords)
                            )
                        }
                        // 条目
                        items(dayRecords, key = { it.id }) { record ->
                            RecordItemCard(
                                record = record,
                                categories = uiState.categories,
                                onConfirm = { vm.confirmRecord(record) },
                                onDelete = { vm.deleteRecord(record) },
                                onReimbursed = { vm.markReimbursed(record) },
                                onEdit = { editingRecord = it }
                            )
                        }
                    }
                }
            }
        }

        // ── FAB（圆角矩形）- 导航到快速记账界面
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 82.dp)
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable { onNavigateToQuickRecord() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "记一笔", tint = Color.White, modifier = Modifier.size(26.dp))
        }
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

// ══════════════════════════════════════════════════════════════════════════════
//  月度概览卡
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun MonthOverviewCard(
    income: Double,
    expense: Double,
    reimbursable: Double = 0.0,
    reimbursed: Double = 0.0,
    reimbursableCount: Int = 0,
    onReimbursableClick: () -> Unit = {}
) {
    val balance = income - expense
    val calendar = Calendar.getInstance()
    val label = "${calendar.get(Calendar.YEAR)} 年 ${calendar.get(Calendar.MONTH) + 1} 月 · 本月结余"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B6B4D), Color(0xFF2D8F68))
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        // 装饰圆
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-30).dp)
                .size(120.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.07f))
        )
        Column {
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "¥ ${String.format("%.2f", balance)}",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5f).sp
            )
            Spacer(Modifier.height(14.dp))
            Row {
                Column(modifier = Modifier.weight(1f)) {
                    Text("支出", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("¥${String.format("%.2f", expense)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(modifier = Modifier.width(0.5.dp).height(36.dp).background(Color.White.copy(alpha = 0.25f)))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("收入", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("¥${String.format("%.2f", income)}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // ── 待报销行（有报销金额时显示）
            if (reimbursable > 0 || reimbursed > 0) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .clickable(onClick = onReimbursableClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
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
                                "¥${String.format("%.2f", reimbursable)}",
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
                                    "¥${String.format("%.2f", reimbursed)}",
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
//  日期分组头
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun DateGroupHeader(label: String, sum: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
    onDelete: () -> Unit,
    onReimbursed: () -> Unit = {},
    onEdit: (Record) -> Unit = {}
) {
    val category = categories.find { it.id == record.categoryId }
    val isPending = record.isPendingConfirm
    val isExpense = record.type == 0

    val cardBg = if (isPending) Color(0xFFFFFDE7) else MaterialTheme.colorScheme.surface
    val iconBg = if (isExpense) Color(0xFFFFEBEE) else Color(0xFFE8F5EE)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
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
            Text(categoryEmoji(category?.name), fontSize = 18.sp)
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

        // 删除按钮（长按显示）
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFFEBEE))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Delete, contentDescription = "删除",
                tint = Color(0xFFE53935),
                modifier = Modifier.size(14.dp)
            )
        }

        // 编辑按钮
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE3F2FD))
                .clickable(onClick = { onEdit(record) }),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Edit, contentDescription = "编辑",
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(14.dp)
            )
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

// ══════════════════════════════════════════════════════════════════════════════
//  记账 BottomSheet（含语音输入）
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordSheet(
    categories: List<Category>,
    allCategories: List<Category>,
    selectedType: Int,
    onTypeChange: (Int) -> Unit,
    onConfirm: (amount: Double, categoryId: Long, note: String, type: Int, isReimbursable: Boolean, reimbursementTarget: String) -> Unit,
    onDismiss: () -> Unit
) {

    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var noteText by remember { mutableStateOf("") }
    var currentType by remember { mutableStateOf(selectedType) }
    var isReimbursable by remember { mutableStateOf(false) }
    var reimbursementTarget by remember { mutableStateOf("") }

    // ── 语音识别状态（系统 Intent）──
    var voiceError by remember { mutableStateOf<String?>(null) }

    // 处理语音识别结果
    fun processVoiceText(text: String) {
        val nameToId = allCategories.associate { it.name to it.id }
        val parsed = VoiceCategorizer.parse(
            text = text,
            categoryNameToId = nameToId,
            defaultCategoryId = categories.firstOrNull()?.id ?: 0L
        )
        if (parsed != null) {
            amountText = parsed.amountText
            if (parsed.categoryId > 0) selectedCategoryId = parsed.categoryId
            if (parsed.note.isNotBlank()) noteText = parsed.note
            if (parsed.isExpense && currentType != 0) { currentType = 0; onTypeChange(0) }
            else if (!parsed.isExpense && currentType != 1) { currentType = 1; onTypeChange(1) }
            // 报销标记（使用 VoiceCategorizer 的识别结果）
            isReimbursable = parsed.isReimbursable
            reimbursementTarget = parsed.reimbursementTarget
            voiceError = null
        } else {
            voiceError = "未能识别金额，请手动输入"
        }
    }

    // 系统语音识别 Launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = VoiceRecognitionManager.extractBestText(result.data)
            if (!text.isNullOrBlank()) {
                processVoiceText(text)
            } else {
                voiceError = "未能识别语音，请重试"
            }
        }
    }

    // 启动语音识别
    fun startVoiceRecognition() {
        voiceError = null
        try {
            voiceLauncher.launch(VoiceRecognitionManager.createRecognizerIntent())
        } catch (e: Exception) {
            voiceError = "设备不支持语音识别"
        }
    }

    // ── 按当前类型筛选要显示的分类 ──
    val displayCategories = remember(currentType) {
        if (currentType == 0) {
            allCategories.filter { it.type == 0 }
        } else {
            allCategories.filter { it.type == 1 }
        }
    }

    // 当类型切换时，自动选中第一个分类
    LaunchedEffect(currentType) {
        displayCategories.firstOrNull()?.let { selectedCategoryId = it.id }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "记一笔",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            // ── 收支切换
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
                            .clickable {
                                currentType = type
                                onTypeChange(type)
                                if (type == 1) isReimbursable = false
                            }
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
            Spacer(Modifier.height(18.dp))

            // ── 金额输入区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("¥ ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (amountText.isEmpty()) "0" else amountText,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentType == 0) ExpenseRed else IncomeGreen,
                        letterSpacing = (-1f).sp
                    )
                }
            }

            // ── 语音输入入口 ──
            VoiceInputRow(
                voiceError = voiceError,
                onClick = { startVoiceRecognition() }
            )

            Spacer(Modifier.height(4.dp))
            Divider(color = BorderColor, thickness = 0.5.dp)
            Spacer(Modifier.height(16.dp))

            // ── 隐藏的真实输入框（驱动数值）
            OutlinedTextField(
                value = amountText,
                onValueChange = { v ->
                    if (v.length <= 10 && v.matches(Regex("^\\d*\\.?\\d{0,2}$")))
                        amountText = v
                },
                label = { Text("金额") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (currentType == 0) ExpenseRed else IncomeGreen
                )
            )
            Spacer(Modifier.height(12.dp))

            // ── 分类网格
            Text("选择分类", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            val cols = 4
            val rows = (displayCategories.size + cols - 1) / cols
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(rows) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(cols) { col ->
                            val idx = row * cols + col
                            if (idx < displayCategories.size) {
                                val cat = displayCategories[idx]
                                val isSelected = selectedCategoryId == cat.id
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 52.dp)
                                        .clickable { selectedCategoryId = cat.id }
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Surface2
                                        )
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(categoryEmoji(cat.name), fontSize = 20.sp)
                                        Text(
                                            cat.name,
                                            fontSize = 11.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ── 报销开关 + 报销对象（仅支出时显示）
            if (currentType == 0) {
                ReimbursableToggle(
                    isReimbursable = isReimbursable,
                    onToggle = { isReimbursable = it }
                )
                if (isReimbursable) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reimbursementTarget,
                        onValueChange = { reimbursementTarget = it },
                        placeholder = { Text("报销对象，如「XX公司」「张三」", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1565C0)
                        )
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── 备注
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("添加备注（选填）", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.height(16.dp))

            // ── 确认按钮
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    if (amount > 0) {
                        // 清理备注，移除金额、分类、报销等关键词
                        val cleanedNote = VoiceCategorizer.cleanNote(noteText)
                        onConfirm(amount, selectedCategoryId, cleanedNote, currentType, isReimbursable, reimbursementTarget)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentType == 0) ExpenseRed else IncomeGreen
                )
            ) {
                Text("记 一 下", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  语音输入行
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun VoiceInputRow(
    voiceError: String?,
    onClick: () -> Unit
) {
    val bgColor = if (voiceError != null) WarningOrangeBg else Surface2
    val iconEmoji = if (voiceError != null) "⚠️" else "🎤"
    val titleText = if (voiceError != null) "识别失败" else "语音记账"
    val labelText = voiceError ?: "说\"午餐 38\"自动记账"
    val labelColor = if (voiceError != null) WarningOrange else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (voiceError != null) WarningOrange.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(iconEmoji, fontSize = 16.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                titleText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
            Spacer(Modifier.height(2.dp))
            Text(
                labelText,
                fontSize = 11.sp,
                color = labelColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  报销开关
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ReimbursableToggle(
    isReimbursable: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .clickable { onToggle(!isReimbursable) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧾", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "可报销",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "标记后可统计待报销金额",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        // 自定义开关
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    if (isReimbursable) Color(0xFF1565C0) else Surface2.copy(alpha = 0.5f)
                )
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .offset(
                        x = if (isReimbursable) 24.dp else 4.dp,
                        y = 3.dp
                    )
                    .clip(CircleShape)
                    .background(if (isReimbursable) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    }
}

// ── ViewModel Factory ──────────────────────────────────────────────────────────
class HomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(application) as T
    }
}
