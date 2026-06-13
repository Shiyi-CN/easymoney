package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.toAmountString
import com.jiyixia.app.viewmodel.StatsViewModel
import com.jiyixia.app.viewmodel.StatsViewModelFactory
import java.util.*

// ── 图表配色 ───────────────────────────────────────────────────────────────────
private val ChartColors = listOf(
    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784),
    Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC),
    Color(0xFFF06292), Color(0xFF7986CB)
)

// ── Tab 枚举 ──────────────────────────────────────────────────────────────────
private enum class StatsTab(val label: String) { Expense("支出分析"), Income("收入分析"), Trend("月度趋势") }

// ══════════════════════════════════════════════════════════════════════════════
//  StatsScreen
// ══════════════════════════════════════════════════════════════════════════════
@Composable
fun StatsScreen(
    onNavigateToReimbursableRecords: () -> Unit = {},
    vm: StatsViewModel = viewModel(
        factory = StatsViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by vm.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(StatsTab.Expense) }

    // 月份标签
    val monthLabel = run {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, uiState.monthOffset) }
        "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部：月份选择 + Tab ──────────────────────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // 月份切换行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MonthArrowButton("<") { vm.prevMonth() }
                    Text(
                        monthLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally)
                    )
                    MonthArrowButton(">") {
                        if (uiState.monthOffset < 0) vm.nextMonth()
                    }
                }

                // Tab 行
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatsTab.entries.forEach { tab ->
                        val isActive = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    tab.label,
                                    fontSize = 13.sp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                                )
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.6f)
                                        .height(if (isActive) 2.dp else 0.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else BorderColor
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 内容区 ────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // 三卡汇总
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val balance = uiState.totalIncome - uiState.totalExpense
                MiniSummaryCard("总支出", fmtAmount(uiState.totalExpense), ExpenseRed, Modifier.weight(1f))
                MiniSummaryCard("总收入", fmtAmount(uiState.totalIncome), IncomeGreen, Modifier.weight(1f))
                MiniSummaryCard("结余", fmtAmount(balance), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }

            // ── 报销统计卡
            if (uiState.pendingReimbursable > 0 || uiState.reimbursed > 0) {
                ReimbursableStatsCard(
                    pending = uiState.pendingReimbursable,
                    reimbursed = uiState.reimbursed,
                    count = uiState.reimbursableCount,
                    totalExpense = uiState.totalExpense,
                    onClick = onNavigateToReimbursableRecords
                )
            }

            when (selectedTab) {
                StatsTab.Expense -> CategoryTab(
                    data = uiState.expenseByCategory,
                    total = uiState.totalExpense,
                    categories = uiState.categories,
                    amountColor = ExpenseRed,
                    title = "支出分类占比",
                    rankTitle = "支出排行"
                )
                StatsTab.Income -> CategoryTab(
                    data = uiState.incomeByCategory,
                    total = uiState.totalIncome,
                    categories = uiState.categories,
                    amountColor = IncomeGreen,
                    title = "收入分类占比",
                    rankTitle = "收入排行"
                )
                StatsTab.Trend -> TrendPlaceholder()
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── 辅助：金额格式 ─────────────────────────────────────────────────────────────
private fun fmtAmount(v: Long) = v.toAmountString()

// ── 月份切换按钮 ───────────────────────────────────────────────────────────────
@Composable
private fun MonthArrowButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── 三卡摘要 ───────────────────────────────────────────────────────────────────
@Composable
private fun MiniSummaryCard(title: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  报销统计卡
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun ReimbursableStatsCard(
    pending: Long,
    reimbursed: Long,
    count: Int,
    totalExpense: Long,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF1565C0).copy(alpha = 0.08f), Color(0xFF2E7D32).copy(alpha = 0.08f))
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧾", fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                "报销统计",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(10.dp))

        Row {
            // 待报销
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "待报销",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    fmtAmount(pending),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                if (count > 0) {
                    Text(
                        "$count 笔",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 分隔线
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .height(40.dp)
                    .background(BorderColor)
            )

            Spacer(Modifier.width(14.dp))

            // 已报销
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "已报销",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    fmtAmount(reimbursed),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                if (totalExpense > 0) {
                    val pct = ((pending.toFloat() / totalExpense) * 100).toInt()
                    Text(
                        "占支出 ${pct}%",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 进度条：已报销 vs 待报销 vs 剩余
        if (pending + reimbursed > 0) {
            Spacer(Modifier.height(10.dp))
            val reimPct = if (totalExpense > 0) ((pending + reimbursed) / totalExpense).toFloat().coerceIn(0f, 1f) else 0f
            val pendingPct = if (totalExpense > 0 && reimPct > 0) (pending / (pending + reimbursed)).toFloat().coerceIn(0f, 1f) else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Surface2)
            ) {
                // 报销相关部分
                Box(
                    modifier = Modifier
                        .fillMaxWidth(reimPct)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF2E7D32))
                            )
                        )
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  分类 Tab（支出 / 收入 共用）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun CategoryTab(
    data: List<CategorySum>,
    total: Long,
    categories: List<com.jiyixia.app.data.entity.Category>,
    amountColor: Color,
    title: String,
    rankTitle: String
) {
    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // 环形图 + 图例
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 环形图
        Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            DonutChart(data = data, total = total, modifier = Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("总计", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(fmtAmount(total), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.width(16.dp))

        // 图例
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            data.take(5).forEachIndexed { index, item ->
                val cat = categories.find { it.id == item.categoryId }
                val pct = if (total > 0) (item.amount.toFloat() / total * 100).toInt() else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp))
                            .background(ChartColors[index % ChartColors.size])
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(cat?.name ?: "其他", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp))
                    Text(fmtAmount(item.amount), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    // 排行榜
    Text(rankTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    Column(
        modifier = Modifier.padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        data.take(6).forEachIndexed { index, item ->
            val cat = categories.find { it.id == item.categoryId }
            val catName = cat?.name ?: "其他"
            val pct = if (total > 0) item.amount.toFloat() / total else 0f
            val emoji = mapOf(
                "餐饮" to "🍜", "交通" to "🚇", "购物" to "🛒", "娱乐" to "🎮",
                "居住" to "🏠", "医疗" to "🏥", "教育" to "📚", "其他" to "📋",
                "工资" to "💰", "奖金" to "🏆", "理财" to "📈", "兼职" to "💼", "红包" to "🧧"
            )[catName] ?: "📋"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$emoji $catName", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(fmtAmount(item.amount), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = amountColor)
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(Surface2)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(pct).fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp)).background(amountColor)
                    )
                }
            }
        }
    }
}

// ── 月度趋势占位 ────────────────────────────────────────────────────────────────
@Composable
private fun TrendPlaceholder() {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📊", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text("月度趋势图即将推出", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  环形图（Canvas）
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun DonutChart(data: List<CategorySum>, total: Long, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 20f
        val diameter = size.minDimension
        val radius = (diameter - strokeWidth) / 2
        val center = Offset(diameter / 2, diameter / 2)

        drawCircle(
            color = Color.LightGray.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        var startAngle = -90f
        data.forEachIndexed { index, item ->
            val sweep = if (total > 0) (item.amount.toFloat() / total * 360) else 0f
            drawArc(
                color = ChartColors[index % ChartColors.size],
                startAngle = startAngle,
                sweepAngle = sweep - 1f,   // 1° 间隙
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter - strokeWidth, diameter - strokeWidth),
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweep
        }
    }
}
