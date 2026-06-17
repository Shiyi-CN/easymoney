package com.jiyixia.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.toAmountCents
import com.jiyixia.app.util.toAmountNumber
import com.jiyixia.app.util.UserLearningManager

/**
 * 编辑记录对话框
 */
@Composable
fun EditRecordDialog(
    record: Record,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (Record) -> Unit
) {
    var amountText by remember { mutableStateOf(record.amount.toAmountNumber()) }
    var selectedCategoryId by remember { mutableLongStateOf(record.categoryId) }
    var noteText by remember { mutableStateOf(record.note) }
    var isReimbursable by remember { mutableStateOf(record.isReimbursable) }
    var reimbursementTarget by remember { mutableStateOf(record.reimbursementTarget) }
    var isCategoryExpanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableIntStateOf(record.type) }  // 0=支出, 1=收入

    // 报销对象历史记录
    val context = LocalContext.current
    val reimburseTargets = remember { UserLearningManager.getReimburseTargets(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),  // 增加高度确保类型选择可见
                verticalArrangement = Arrangement.spacedBy(8.dp)  // 减少间距
            ) {
                // 类型选择（支出/收入）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "支出", 1 to "收入").forEach { (type, label) ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        if (type == 0) ExpenseRed.copy(alpha = 0.1f)
                                        else IncomeGreen.copy(alpha = 0.1f)
                                    } else Surface2
                                )
                                .clickable {
                                    selectedType = type
                                    // 切换类型时重置分类选择
                                    selectedCategoryId = categories.firstOrNull { it.type == type }?.id ?: selectedCategoryId
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) {
                                    if (type == 0) ExpenseRed else IncomeGreen
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 金额输入（压缩上下宽度）
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("金额") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                // 分类选择（可折叠，带滚动条）
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface2)
                            .clickable { isCategoryExpanded = !isCategoryExpanded }
                            .padding(horizontal = 12.dp, vertical = 8.dp),  // 恢复内边距
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("选择分类", fontSize = 14.sp)  // 恢复字体大小
                        Text(
                            if (isCategoryExpanded) "▲" else "▼",
                            fontSize = 12.sp,  // 恢复字体大小
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isCategoryExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))  // 恢复间距
                        val cols = 4
                        // 根据选中的类型过滤分类
                        val filteredCategories = categories.filter { it.type == selectedType }

                        // 使用 LazyVerticalGrid 添加滚动功能
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            verticalArrangement = Arrangement.spacedBy(4.dp),  // 恢复间距
                            horizontalArrangement = Arrangement.spacedBy(4.dp),  // 恢复间距
                            modifier = Modifier.heightIn(max = 120.dp)  // 限制高度
                        ) {
                            items(filteredCategories.size) { idx ->
                                val cat = filteredCategories[idx]
                                val isSelected = selectedCategoryId == cat.id
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)  // 恢复高度
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else Surface2
                                        )
                                        .clickable { selectedCategoryId = cat.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        cat.name,
                                        fontSize = 10.sp,  // 恢复字体大小
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 备注输入（压缩上下宽度）
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                // 报销开关
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface2)
                        .clickable { isReimbursable = !isReimbursable }
                        .padding(horizontal = 12.dp, vertical = 8.dp),  // 恢复内边距
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("可报销", fontSize = 14.sp)  // 恢复字体大小
                    Switch(
                        checked = isReimbursable,
                        onCheckedChange = { isReimbursable = it }
                    )
                }

                // 报销对象输入（仅在可报销时显示）
                if (isReimbursable) {
                    OutlinedTextField(
                        value = reimbursementTarget,
                        onValueChange = { reimbursementTarget = it },
                        label = { Text("报销对象") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                    )

                    // 报销对象历史选择（最多显示5个）
                    if (reimburseTargets.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            reimburseTargets.take(5).forEach { target ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Surface2)
                                        .clickable { reimbursementTarget = target }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(target, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amountCents = amountText.toAmountCents()
                    if (amountCents > 0) {
                        val finalReimbursable = if (selectedType == 1) false else isReimbursable
                        val finalTarget = if (selectedType == 1) "" else if (isReimbursable) reimbursementTarget else ""
                        val updatedRecord = record.copy(
                            type = selectedType,
                            amount = amountCents,
                            categoryId = selectedCategoryId,
                            note = noteText,
                            isReimbursable = finalReimbursable,
                            reimbursementTarget = finalTarget
                        )
                        // 保存报销对象到历史记录
                        if (finalReimbursable && finalTarget.isNotBlank()) {
                            UserLearningManager.saveReimburseTarget(context, finalTarget)
                        }
                        onSave(updatedRecord)
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
