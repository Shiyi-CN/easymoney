package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.dao.CategorySum
import com.jiyixia.app.viewmodel.StatsViewModel
import com.jiyixia.app.viewmodel.StatsViewModelFactory

private val ChartColors = listOf(
    Color(0xFFE57373), Color(0xFF64B5F6), Color(0xFF81C784),
    Color(0xFFFFB74D), Color(0xFFBA68C8), Color(0xFF4DB6AC),
    Color(0xFFF06292), Color(0xFF7986CB)
)

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel(factory = StatsViewModelFactory(LocalContext.current.applicationContext as Application))) {
    val uiState by vm.uiState.collectAsState()
    val balance = uiState.totalIncome - uiState.totalExpense
    val calendar = java.util.Calendar.getInstance()
    val monthLabel = "${calendar.get(java.util.Calendar.YEAR)}年${calendar.get(java.util.Calendar.MONTH) + 1}月"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(monthLabel, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // 概览卡片
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryCard("总支出", "¥${String.format("%.2f", uiState.totalExpense)}", Color(0xFFE57373), Modifier.weight(1f))
            SummaryCard("总收入", "¥${String.format("%.2f", uiState.totalIncome)}", Color(0xFF81C784), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        SummaryCard("结余", "¥${String.format("%.2f", balance)}", MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth())

        Spacer(modifier = Modifier.height(24.dp))

        // 支出分类环形图
        Text("支出分类", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.expenseByCategory.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("暂无数据", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 环形图
                DonutChart(
                    data = uiState.expenseByCategory,
                    total = uiState.totalExpense,
                    modifier = Modifier.size(180.dp)
                )
                Spacer(modifier = Modifier.width(24.dp))

                // 图例
                Column {
                    uiState.expenseByCategory.forEachIndexed { index, item ->
                        val cat = uiState.categories.find { it.id == item.categoryId }
                        val percent = if (uiState.totalExpense > 0) (item.amount / uiState.totalExpense * 100).toInt() else 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                modifier = Modifier.size(12.dp),
                                color = ChartColors[index % ChartColors.size],
                                shape = CircleShape
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "${cat?.name ?: "其他"} $percent%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color)
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
private fun DonutChart(data: List<CategorySum>, total: Double, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 28f
        val diameter = size.minDimension
        val radius = (diameter - strokeWidth) / 2
        val center = Offset(diameter / 2, diameter / 2)

        // 背景圆环
        drawCircle(
            color = Color.LightGray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )

        // 数据弧
        var startAngle = -90f
        data.forEachIndexed { index, item ->
            val sweepAngle = if (total > 0) (item.amount / total * 360).toFloat() else 0f
            drawArc(
                color = ChartColors[index % ChartColors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(diameter - strokeWidth, diameter - strokeWidth),
                style = Stroke(width = strokeWidth)
            )
            startAngle += sweepAngle
        }
    }
}
