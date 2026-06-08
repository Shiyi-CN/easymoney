package com.jiyixia.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Category
import com.jiyixia.app.data.entity.Record
import com.jiyixia.app.service.BubbleService
import com.jiyixia.app.ui.theme.ExpenseRed
import com.jiyixia.app.ui.theme.IncomeGreen
import com.jiyixia.app.ui.theme.JiYiXiaTheme
import com.jiyixia.app.ui.theme.Surface2
import com.jiyixia.app.ui.theme.WarningOrange
import com.jiyixia.app.util.VoiceCategorizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BubbleInputActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContent {
            JiYiXiaTheme {
                BubbleInputScreen(
                    onDismiss = {
                        // 确保 BubbleService 恢复显示
                        if (!BubbleService.isRunning) {
                            BubbleService.start(this)
                        }
                        finish()
                    }
                )
            }
        }
    }
}

private val categoryEmojiMap = mapOf(
    "餐饮" to "🍜", "交通" to "🚇", "购物" to "🛒", "娱乐" to "🎮",
    "居住" to "🏠", "医疗" to "🏥", "教育" to "📚", "其他" to "📋",
    "工资" to "💰", "奖金" to "🏆", "理财" to "📈", "兼职" to "💼",
    "红包" to "🧧"
)

@Composable
private fun BubbleInputScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JiYiXiaApp
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var allCategories by remember { mutableStateOf<List<Category>>(emptyList()) }

    // 解析结果
    var parsedAmount by remember { mutableStateOf("") }
    var parsedCategory by remember { mutableStateOf<Category?>(null) }
    var parsedIsExpense by remember { mutableStateOf(true) }
    var parsedIsReimbursable by remember { mutableStateOf(false) }
    var parsedReimbursementTarget by remember { mutableStateOf("") }
    var parsedNote by remember { mutableStateOf("") }
    var isParsed by remember { mutableStateOf(false) }

    // 加载分类
    LaunchedEffect(Unit) {
        val db = app.database
        allCategories = db.categoryDao().getAll().first()
    }

    // 实时解析输入
    LaunchedEffect(inputText) {
        if (inputText.isBlank()) {
            isParsed = false
            return@LaunchedEffect
        }

        val nameToId = allCategories.associate { it.name to it.id }
        val defaultCategoryId = allCategories.firstOrNull()?.id ?: 0L
        val parsed = VoiceCategorizer.parse(
            text = inputText,
            categoryNameToId = nameToId,
            defaultCategoryId = defaultCategoryId
        )

        if (parsed != null) {
            parsedAmount = parsed.amountText
            parsedCategory = allCategories.find { it.id == parsed.categoryId }
            parsedIsExpense = parsed.isExpense
            parsedNote = parsed.note
            parsedIsReimbursable = parsed.isReimbursable
            parsedReimbursementTarget = parsed.reimbursementTarget
            isParsed = true
        } else {
            isParsed = false
        }
    }

    // 保存记录
    fun saveRecord() {
        val amount = parsedAmount.toDoubleOrNull() ?: return
        val category = parsedCategory ?: return

        scope.launch {
            val db = app.database
            db.recordDao().insert(
                Record(
                    type = if (parsedIsExpense) 0 else 1,
                    amount = amount,
                    categoryId = category.id,
                    note = parsedNote,
                    date = System.currentTimeMillis(),
                    isReimbursable = parsedIsReimbursable,
                    reimbursementTarget = parsedReimbursementTarget
                )
            )
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { /* 阻止点击穿透 */ }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                "快速记账",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "输入如「午餐 38」「打车 25 腾讯报销」",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            // 输入框
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("午餐 38", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(16.dp))

            // 解析结果预览
            if (isParsed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface2)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 分类图标
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (parsedIsExpense) Color(0xFFFFEBEE) else Color(0xFFE8F5EE)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            categoryEmojiMap[parsedCategory?.name] ?: "📋",
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                parsedCategory?.name ?: "未知",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (parsedIsReimbursable) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1565C0).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (parsedReimbursementTarget.isNotBlank())
                                            "报销→${parsedReimbursementTarget}"
                                        else "可报销",
                                        fontSize = 10.sp,
                                        color = Color(0xFF1565C0)
                                    )
                                }
                            }
                        }
                        if (parsedNote.isNotBlank()) {
                            Text(
                                parsedNote,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 金额
                    Text(
                        "${if (parsedIsExpense) "-" else "+"}¥${parsedAmount}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (parsedIsExpense) ExpenseRed else IncomeGreen
                    )
                }
                Spacer(Modifier.height(20.dp))
            } else if (inputText.isNotBlank()) {
                // 未识别提示
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningOrange.copy(alpha = 0.1f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "未能识别，请输入金额和分类，如「午餐 38」",
                        fontSize = 13.sp,
                        color = WarningOrange
                    )
                }
                Spacer(Modifier.height(20.dp))
            } else {
                Spacer(Modifier.height(68.dp))
            }

            // 确认按钮
            Button(
                onClick = { saveRecord() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = isParsed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (parsedIsExpense) ExpenseRed else IncomeGreen
                )
            ) {
                Text("记一下", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))

            // 取消
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}