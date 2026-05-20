package com.jiyixia.app.ui.screens

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.jiyixia.app.BuildConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.service.PaymentNotificationListener
import com.jiyixia.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

// ── 设备检测 ───────────────────────────────────────────────────────────────────
private fun isNotificationListenerEnabled(context: Context): Boolean {
    val cn = ComponentName(context, PaymentNotificationListener::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.contains(cn.flattenToString())
}

private fun isXiaomi(): Boolean =
    Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
    Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
    Build.BRAND.equals("Redmi", ignoreCase = true)

// ══════════════════════════════════════════════════════════════════════════════
//  SettingsScreen
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMsg by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }

    val listenerEnabled = isNotificationListenerEnabled(context)
    val xiaomi = isXiaomi()

    TopAppBar(
        title = { Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 64.dp, bottom = 32.dp) // 给 TopAppBar 留空
    ) {

        // ═══ 自动记账 ═══════════════════════════════════════════════════════════
        SectionTitle("自动记账")
        SettingsCard {
            // 通知监听 Toggle
            SettingsRow(
                iconBg = Color(0xFFE8F5E9), icon = "🔔",
                title = "通知监听",
                desc = "自动捕获支付宝 / 微信支付通知",
                trailing = {
                    Switch(
                        checked = listenerEnabled,
                        onCheckedChange = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            )
            SettingsDivider()

            // 小米兼容模式
            if (xiaomi) {
                SettingsRow(
                    iconBg = Color(0xFFFFF3E0), icon = "⚠️",
                    title = "小米兼容模式",
                    desc = "HyperOS 权限授权引导",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ExpenseRed)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("需配置", color = Color.White, fontSize = 10.sp)
                        }
                    },
                    onClick = {
                        // 跳转电池优化设置
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )
                SettingsDivider()

                // 引导卡
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3E0))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text("小米 / Redmi 额外设置步骤", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = WarningOrange)
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "① 开启通知监听权限（点击上方）",
                        "② 关闭电池优化 → 无限制",
                        "③ 设置 → 应用 → 记一下 → 自启动 → 开",
                        "④ 省电策略 → 无限制，允许后台弹出"
                    ).forEach { step ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("·  ", color = WarningOrange, fontSize = 12.sp)
                            Text(step, fontSize = 12.sp, color = Color(0xFF5D3D00))
                        }
                    }
                }
                SettingsDivider()
            } else {
                // 非小米：跳到通知设置
                SettingsRow(
                    iconBg = Color(0xFFFFF3E0), icon = "⚙️",
                    title = "开启通知权限",
                    desc = "前往系统设置授权",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
                SettingsDivider()
            }

            // 自动确认规则
            SettingsRow(
                iconBg = Color(0xFFE8F5E9), icon = "🤖",
                title = "自动确认规则",
                desc = "满足条件自动归类，无需手动",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ═══ 数据管理 ════════════════════════════════════════════════════════════
        SectionTitle("数据管理")
        SettingsCard {
            SettingsRow(
                iconBg = Color(0xFFE3F2FD), icon = "📂",
                title = "分类管理",
                desc = "自定义支出与收入分类",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
            )
            SettingsDivider()

            SettingsRow(
                iconBg = Color(0xFFF3E5F5), icon = "📊",
                title = "导出 CSV",
                desc = "导出账单到表格文件",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) },
                onClick = {
                    val app = context.applicationContext as JiYiXiaApp
                    scope.launch {
                        exportMsg = exportCsv(app) ?: "导出失败"
                    }
                }
            )
            if (exportMsg.isNotEmpty()) {
                Text(
                    exportMsg,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            SettingsDivider()

            SettingsRow(
                iconBg = Color(0xFFFBE9E7), icon = "🗑️",
                title = "清空数据",
                desc = "删除所有记录，不可恢复",
                titleColor = ExpenseRed,
                trailing = { Text("›", color = ExpenseRed, fontSize = 16.sp) },
                onClick = { showClearDialog = true }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ═══ 界面 ════════════════════════════════════════════════════════════════
        SectionTitle("界面")
        SettingsCard {
            SettingsRow(
                iconBg = Color(0xFFF1F8E9), icon = "🌙",
                title = "深色模式",
                desc = "跟随系统",
                trailing = {
                    Switch(checked = false, onCheckedChange = {})
                }
            )
            SettingsDivider()

            SettingsRow(
                iconBg = Color(0xFFE8EAF6), icon = "🧩",
                title = "桌面小组件",
                desc = "Widget 快速记账入口",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) }
            )
        }

        Spacer(Modifier.height(8.dp))

        // ═══ 识别日志 ════════════════════════════════════════════════════════════
        val detections = PaymentNotificationListener.detections
        if (detections.isNotEmpty()) {
            SectionTitle("识别日志")
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    detections.forEach { log ->
                        Text(log, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ═══ 关于 ═════════════════════════════════════════════════════════════════
        SectionTitle("关于")
        SettingsCard {
            SettingsRow(
                iconBg = Color(0xFFE8F5EE), icon = "ℹ️",
                title = "版本",
                desc = "记一下 v${BuildConfig.VERSION_NAME} · 轻量记账，支付即记账"
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    // 清空数据确认弹窗
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有数据？") },
            text = { Text("此操作将永久删除所有记录，无法恢复。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("删除", color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  组件
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface),
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    Divider(
        modifier = Modifier.padding(start = 60.dp),
        color = BorderColor,
        thickness = 0.5.dp
    )
}

@Composable
private fun SettingsRow(
    iconBg: Color,
    icon: String,
    title: String,
    desc: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val mod = if (onClick != null)
        Modifier.clickable(onClick = onClick)
    else
        Modifier

    Row(
        modifier = mod
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))

        // 文字
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = titleColor)
            if (desc != null) {
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
            }
        }

        if (trailing != null) trailing()
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  CSV 导出
// ══════════════════════════════════════════════════════════════════════════════
private suspend fun exportCsv(app: JiYiXiaApp): String? = withContext(Dispatchers.IO) {
    try {
        val db = app.database
        val records = db.recordDao().getAll().first()
        val categories = db.categoryDao().getAll().first()

        val fileName = "记一下_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv"
        val csvContent = buildString {
            appendLine("日期,分类,金额,类型,备注")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            records.forEach { r ->
                val cat = categories.find { it.id == r.categoryId }?.name ?: "未知"
                val type = if (r.type == 0) "支出" else "收入"
                appendLine("${sdf.format(Date(r.date))},$cat,${r.amount},$type,${r.note}")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: return@withContext "导出失败：无法创建文件"
            app.contentResolver.openOutputStream(uri)?.use { it.write(csvContent.toByteArray(Charsets.UTF_8)) }
            cv.clear(); cv.put(MediaStore.Downloads.IS_PENDING, 0)
            app.contentResolver.update(uri, cv, null, null)
            "已导出到 Downloads/$fileName"
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val f = File(dir, fileName)
            FileWriter(f).use { it.write(csvContent) }
            "已导出到 ${f.absolutePath}"
        }
    } catch (e: Exception) {
        "导出失败：${e.message}"
    }
}
