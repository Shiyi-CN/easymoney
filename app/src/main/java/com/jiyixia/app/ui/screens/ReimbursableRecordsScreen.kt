package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.toAmountString
import com.jiyixia.app.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * 待报销记录详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReimbursableRecordsScreen(
    onNavigateBack: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by vm.uiState.collectAsState()

    // 筛选所有可报销记录（包括已报销和未报销）
    val allReimbursableRecords = uiState.records.filter {
        it.type == 0 && it.isReimbursable
    }.sortedByDescending { it.date }

    // 分组：未报销和已报销
    val pendingRecords = allReimbursableRecords.filter { !it.isReimbursed }
    val reimbursedRecords = allReimbursableRecords.filter { it.isReimbursed }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("报销记录", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (allReimbursableRecords.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧾", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无报销记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // 记录列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 未报销记录
                if (pendingRecords.isNotEmpty()) {
                    item {
                        Text(
                            "待报销 (${pendingRecords.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(pendingRecords) { record ->
                        ReimbursableRecordItem(
                            record = record,
                            categories = uiState.categories,
                            isReimbursed = false,
                            onMarkReimbursed = { vm.markReimbursed(record) }
                        )
                    }
                }

                // 已报销记录
                if (reimbursedRecords.isNotEmpty()) {
                    item {
                        Text(
                            "已报销 (${reimbursedRecords.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(reimbursedRecords) { record ->
                        ReimbursableRecordItem(
                            record = record,
                            categories = uiState.categories,
                            isReimbursed = true,
                            onMarkReimbursed = { vm.markReimbursed(record) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 报销记录条目
 */
@Composable
private fun ReimbursableRecordItem(
    record: Record,
    categories: List<Category>,
    isReimbursed: Boolean,
    onMarkReimbursed: () -> Unit
) {
    val category = categories.find { it.id == record.categoryId }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    // 根据报销状态设置样式
    val cardAlpha = if (isReimbursed) 0.6f else 1.0f
    val amountColor = if (isReimbursed) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseRed
    val badgeColor = if (isReimbursed) Color(0xFF2E7D32) else Color(0xFF1565C0)
    val badgeText = if (isReimbursed) "已报销" else "待报销"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isReimbursed) Color(0xFFE8F5E9) else Color(0xFFE3F2FD)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(categoryEmoji(category?.name), fontSize = 18.sp)
            }

            Spacer(Modifier.width(12.dp))

            // 记录信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category?.name ?: "未知",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = cardAlpha)
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            badgeText,
                            fontSize = 10.sp,
                            color = badgeColor
                        )
                    }
                }
                if (record.note.isNotBlank()) {
                    Text(
                        record.note,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = cardAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (record.reimbursementTarget.isNotBlank()) {
                    Text(
                        "→ ${record.reimbursementTarget}",
                        fontSize = 11.sp,
                        color = Color(0xFF1565C0).copy(alpha = 0.7f * cardAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    dateFormat.format(Date(record.date)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f * cardAlpha)
                )
            }

            // 金额和操作
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    record.amount.toAmountString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Spacer(Modifier.height(4.dp))
                if (!isReimbursed) {
                    Button(
                        onClick = onMarkReimbursed,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32)
                        )
                    ) {
                        Text(
                            "标记已报销",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                } else {
                    // 已报销时显示「撤销报销」按钮
                    OutlinedButton(
                        onClick = onMarkReimbursed,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF1565C0)
                        )
                    ) {
                        Text("撤销报销", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// 分类 Emoji 映射
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

private fun categoryEmoji(name: String?) = categoryEmojiMap[name] ?: "📋"
