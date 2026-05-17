package com.jiyixia.app.ui.screens

import android.app.Application
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import androidx.lifecycle.ViewModelProvider
import com.jiyixia.app.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(LocalContext.current.applicationContext as Application))) {
    val uiState by vm.uiState.collectAsState()
    val selectedType by vm.selectedType.collectAsState()
    val expenseCategories by vm.expenseCategories.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<Record?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // 待确认提示
            if (uiState.pendingCount > 0) {
                PendingConfirmBanner(count = uiState.pendingCount)
            }

            // 记录列表
            if (uiState.records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("还没有记录，记一笔吧", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val grouped = uiState.records.groupBy { record ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(record.date))
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (dateStr, records) ->
                        item {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(records, key = { it.id }) { record ->
                            RecordItem(
                                record = record,
                                categories = uiState.categories,
                                onConfirm = { vm.confirmRecord(record) },
                                onDelete = { vm.deleteRecord(record) },
                                onClick = { showEditDialog = record }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddRecordSheet(
            categories = expenseCategories,
            selectedType = selectedType,
            onTypeChange = { vm.setSelectedType(it) },
            onConfirm = { amount, categoryId, note, type ->
                vm.addRecord(amount, categoryId, note, type)
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false }
        )
    }
}

@Composable
private fun PendingConfirmBanner(count: Int) {
    Surface(
        color = Color(0xFFFFF3E0),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⚠️ $count 笔记录待确认",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE65100)
            )
        }
    }
}

@Composable
private fun RecordItem(
    record: Record,
    categories: List<Category>,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val category = categories.find { it.id == record.categoryId }
    val isPending = record.isPendingConfirm

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = if (record.type == 0) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        category?.name?.take(1) ?: "?",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        category?.name ?: "未知",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    if (isPending) {
                        Text(
                            " 待确认",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF57F17)
                        )
                    }
                }
                if (record.note.isNotBlank()) {
                    Text(
                        record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Text(
                text = "${if (record.type == 0) "-" else "+"}¥${String.format("%.2f", record.amount)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (record.type == 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            if (isPending) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onConfirm, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Check, "确认", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordSheet(
    categories: List<Category>,
    selectedType: Int,
    onTypeChange: (Int) -> Unit,
    onConfirm: (amount: Double, categoryId: Long, note: String, type: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: 0L) }
    var noteText by remember { mutableStateOf("") }
    var currentType by remember { mutableStateOf(selectedType) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 32.dp)
        ) {
            // 收支切换
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilterChip(
                    selected = currentType == 0,
                    onClick = { currentType = 0; onTypeChange(0) },
                    label = { Text("支出") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = currentType == 1,
                    onClick = { currentType = 1; onTypeChange(1) },
                    label = { Text("收入") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 金额输入
            OutlinedTextField(
                value = amountText,
                onValueChange = { if (it.length <= 10) amountText = it },
                label = { Text("金额") },
                prefix = { Text("¥") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 分类选择
            Text("分类", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 备注
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("备注（选填）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 确认按钮
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: return@Button
                    onConfirm(amount, selectedCategoryId, noteText, currentType)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountText.toDoubleOrNull() != null && amountText.toDouble() > 0
            ) {
                Text("记一下")
            }
        }
    }
}

class HomeViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(application) as T
    }
}
