package com.jiyixia.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiyixia.app.ui.theme.ExpenseRed
import com.jiyixia.app.ui.theme.IncomeGreen
import com.jiyixia.app.util.CategoryEmoji
import com.jiyixia.app.util.toAmountString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 年度统计数据
 */
data class YearStatsData(
    val year: Int,
    val monthlyData: List<MonthData>,
    val totalExpense: Long,
    val totalIncome: Long,
    val topCategories: List<CategoryExpense>
)

data class MonthData(
    val month: String,
    val expense: Long,
    val income: Long
)

data class CategoryExpense(
    val name: String,
    val emoji: String,
    val amount: Long
)

/**
 * 年度总览页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearStatsScreen() {
    val context = LocalContext.current
    var yearData by remember { mutableStateOf<YearStatsData?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 加载年度数据
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext as com.jiyixia.app.JiYiXiaApp
            val db = app.database
            val recordDao = db.recordDao()
            val categoryDao = db.categoryDao()

            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val sdf = java.text.SimpleDateFormat("M月", Locale.getDefault())

            val monthlyData = mutableListOf<MonthData>()
            var totalExpense = 0L
            var totalIncome = 0L

            // 获取 12 个月的数据
            for (month in 0 until 12) {
                cal.set(currentYear, month, 1, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis

                val expense = recordDao.getSumByTypeAndDateRange(0, start, end).first() ?: 0L
                val income = recordDao.getSumByTypeAndDateRange(1, start, end).first() ?: 0L

                monthlyData.add(MonthData("${month + 1}月", expense, income))
                totalExpense += expense
                totalIncome += income
            }

            // 获取年度 Top 分类
            cal.set(currentYear, 0, 1, 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val yearStart = cal.timeInMillis
            cal.set(currentYear, 11, 31, 23, 59, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val yearEnd = cal.timeInMillis

            val categories = categoryDao.getAll().first()
            val expenseByCategory = recordDao.getExpenseGroupByCategory(yearStart, yearEnd).first()

            val topCategories = expenseByCategory.take(5).map { sum ->
                val cat = categories.find { it.id == sum.categoryId }
                CategoryExpense(
                    name = cat?.name ?: "其他",
                    emoji = CategoryEmoji.get(cat?.name ?: "其他"),
                    amount = sum.amount
                )
            }

            yearData = YearStatsData(
                year = currentYear,
                monthlyData = monthlyData,
                totalExpense = totalExpense,
                totalIncome = totalIncome,
                topCategories = topCategories
            )
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题
        Surface(color = MaterialTheme.colorScheme.surface) {
            Text(
                "年度总览",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (yearData != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                // 年度总结卡片
                YearSummaryCard(yearData!!)

                Spacer(Modifier.height(16.dp))

                // 12 个月柱状图
                YearlyBarChart(yearData!!.monthlyData)

                Spacer(Modifier.height(16.dp))

                // Top 分类
                if (yearData!!.topCategories.isNotEmpty()) {
                    TopCategoriesCard(yearData!!.topCategories, yearData!!.totalExpense)
                }
            }
        }
    }
}

@Composable
private fun YearSummaryCard(data: YearStatsData) {
    val balance = data.totalIncome - data.totalExpense

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "${data.year} 年度总结",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("总支出", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(
                        data.totalExpense.toAmountString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ExpenseRed
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("总收入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(
                        data.totalIncome.toAmountString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = IncomeGreen
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("结余", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(
                        balance.toAmountString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (balance >= 0) IncomeGreen else ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
private fun YearlyBarChart(monthlyData: List<MonthData>) {
    val maxValue = monthlyData.maxOf { maxOf(it.expense, it.income) }.toFloat().coerceAtLeast(1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "月度收支",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            // 图例
            Row(modifier = Modifier.padding(bottom = 8.dp)) {
                Box(modifier = Modifier.size(12.dp).background(ExpenseRed, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text("支出", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Box(modifier = Modifier.size(12.dp).background(IncomeGreen, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text("收入", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 柱状图
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                monthlyData.forEach { month ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.height(130.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height((month.expense / maxValue * 120).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(ExpenseRed)
                            )
                            Spacer(Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .width(12.dp)
                                    .height((month.income / maxValue * 120).dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(IncomeGreen)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            month.month,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopCategoriesCard(categories: List<CategoryExpense>, totalExpense: Long) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "支出 Top 5",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            categories.forEach { cat ->
                val percentage = if (totalExpense > 0) (cat.amount * 100 / totalExpense) else 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(cat.emoji, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        cat.name,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        cat.amount.toAmountString(),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${percentage}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (cat != categories.last()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}
