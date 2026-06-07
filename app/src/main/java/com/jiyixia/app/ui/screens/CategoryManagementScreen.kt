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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.viewmodel.HomeViewModel

/**
 * 分类管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    vm: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by vm.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    // 按类型分组
    val expenseCategories = uiState.categories.filter { it.type == 0 }
    val incomeCategories = uiState.categories.filter { it.type == 1 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("分类管理", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加分类")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 支出分类
            item {
                Text(
                    "支出分类 (${expenseCategories.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(expenseCategories) { category ->
                CategoryItem(
                    category = category,
                    onEdit = { editingCategory = it },
                    onDelete = { vm.deleteCategory(it) }
                )
            }

            // 收入分类
            item {
                Text(
                    "收入分类 (${incomeCategories.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(incomeCategories) { category ->
                CategoryItem(
                    category = category,
                    onEdit = { editingCategory = it },
                    onDelete = { vm.deleteCategory(it) }
                )
            }
        }
    }

    // 添加分类对话框
    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { category ->
                vm.addCategory(category)
                showAddDialog = false
            }
        )
    }

    // 编辑分类对话框
    editingCategory?.let { category ->
        EditCategoryDialog(
            category = category,
            onDismiss = { editingCategory = null },
            onSave = { updatedCategory ->
                vm.updateCategory(updatedCategory)
                editingCategory = null
            }
        )
    }
}

/**
 * 分类条目
 */
@Composable
private fun CategoryItem(
    category: Category,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(category) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (category.type == 0) Color(0xFFFFEBEE) else Color(0xFFE8F5EE)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    categoryEmoji(category.name),
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // 分类信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (category.isPreset) "预设分类" else "自定义分类",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 删除按钮（仅自定义分类可删除）
            if (!category.isPreset) {
                IconButton(
                    onClick = { onDelete(category) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 添加分类对话框
 */
@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(0) }  // 0=支出，1=收入

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加分类", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 分类名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 分类类型
                Text("分类类型", fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "支出", 1 to "收入").forEach { (type, label) ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        if (type == 0) ExpenseRed else IncomeGreen
                                    } else {
                                        Surface2
                                    }
                                )
                                .clickable { selectedType = type }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val category = Category(
                            name = name,
                            icon = guessIcon(name),
                            type = selectedType,
                            isPreset = false
                        )
                        onSave(category)
                    }
                }
            ) {
                Text("添加", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 编辑分类对话框
 */
@Composable
private fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf(category.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑分类", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 分类名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 分类类型（不可修改）
                Text(
                    "类型：${if (category.type == 0) "支出" else "收入"}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        val updatedCategory = category.copy(name = name)
                        onSave(updatedCategory)
                    }
                }
            ) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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

/**
 * 根据分类名称猜测图标
 */
private fun guessIcon(name: String): String {
    return when {
        name.contains("餐") || name.contains("吃") || name.contains("饭") -> "Restaurant"
        name.contains("车") || name.contains("交通") || name.contains("出行") -> "DirectionsCar"
        name.contains("购") || name.contains("买") || name.contains("商") -> "ShoppingBag"
        name.contains("住") || name.contains("房") || name.contains("家") -> "Home"
        name.contains("玩") || name.contains("乐") || name.contains("游") -> "SportsEsports"
        name.contains("医") || name.contains("病") || name.contains("药") -> "LocalHospital"
        name.contains("学") || name.contains("书") || name.contains("教") -> "School"
        name.contains("话") || name.contains("网") || name.contains("通") -> "Phone"
        name.contains("人") || name.contains("社") || name.contains("友") -> "People"
        name.contains("美") || name.contains("容") || name.contains("妆") -> "Face"
        name.contains("宠") || name.contains("猫") || name.contains("狗") -> "Pets"
        name.contains("工") || name.contains("办") || name.contains("公") -> "Work"
        name.contains("修") || name.contains("理") || name.contains("维") -> "Build"
        name.contains("捐") || name.contains("善") || name.contains("爱") -> "Favorite"
        name.contains("工") || name.contains("薪") || name.contains("资") -> "AccountBalance"
        name.contains("奖") || name.contains("金") || name.contains("励") -> "EmojiEvents"
        name.contains("理") || name.contains("投") || name.contains("基") -> "TrendingUp"
        name.contains("兼") || name.contains("副") || name.contains("外") -> "Work"
        name.contains("红") || name.contains("包") || name.contains("礼") -> "CardGiftcard"
        name.contains("报") || name.contains("销") || name.contains("账") -> "Receipt"
        name.contains("租") || name.contains("金") || name.contains("赁") -> "Home"
        name.contains("退") || name.contains("款") || name.contains("回") -> "Undo"
        name.contains("中") || name.contains("奖") || name.contains("彩") -> "Star"
        else -> "MoreHoriz"
    }
}
