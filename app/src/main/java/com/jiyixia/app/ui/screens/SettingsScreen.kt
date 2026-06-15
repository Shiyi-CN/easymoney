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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.jiyixia.app.data.RecordMode
import com.jiyixia.app.data.ThemeMode
import com.jiyixia.app.data.ThemePreferences
import com.jiyixia.app.repository.RecordRepository
import com.jiyixia.app.service.BubbleService
import com.jiyixia.app.service.PaymentAccessibilityService
import com.jiyixia.app.service.PaymentDetector
import com.jiyixia.app.service.PaymentNotificationListener
import com.jiyixia.app.ui.theme.*
import com.jiyixia.app.util.BackupUtil
import com.jiyixia.app.util.CrashHandler
import com.jiyixia.app.util.UpdateChecker
import com.jiyixia.app.util.toAmountNumber
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

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val cn = ComponentName(context, PaymentAccessibilityService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return flat.contains(cn.flattenToString())
}

private fun isXiaomi(): Boolean =
    Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
    Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
    Build.BRAND.equals("Redmi", ignoreCase = true)

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
    }
    return name
}

// ══════════════════════════════════════════════════════════════════════════════
//  SettingsScreen
// ══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCategoryManagement: () -> Unit = {},
    onNavigateToCrashLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportMsg by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showConfidenceDialog by remember { mutableStateOf(false) }
    val confidenceThreshold by ThemePreferences.getConfidenceThreshold(context).collectAsState(initial = 80)

    // 更新检查状态
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showNoUpdateToast by remember { mutableStateOf(false) }

    // 备份/恢复状态
    var backupMsg by remember { mutableStateOf("") }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var restoreMsg by remember { mutableStateOf("") }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val listenerEnabled = isNotificationListenerEnabled(context)
    val serviceConnected = PaymentNotificationListener.isServiceConnected
    val xiaomi = isXiaomi()

    // 主题模式状态
    val themeMode by ThemePreferences.getThemeMode(context).collectAsState(initial = ThemeMode.FOLLOW_SYSTEM)

    // 通知权限状态（Android 13+）
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶部标题
        Surface(
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                "设置",
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {

        // ═══ 自动记账 ═══════════════════════════════════════════════════════════
        SectionTitle("自动记账")
        SettingsCard {
            // 通知监听 Toggle
            SettingsRow(
                iconBg = Color(0xFFE8F5E9), icon = "🔔",
                title = "通知监听",
                desc = when {
                    !listenerEnabled -> "未开启，请点击右侧开关授权"
                    !serviceConnected -> "已授权，服务未连接（重启App试试）"
                    !hasNotificationPermission -> "通知权限未授予，可能收不到推送"
                    else -> "正在监听支付宝 / 微信支付通知"
                },
                titleColor = if (listenerEnabled && serviceConnected && hasNotificationPermission)
                    MaterialTheme.colorScheme.onSurface else WarningOrange,
                trailing = {
                    Switch(
                        checked = listenerEnabled && serviceConnected,
                        onCheckedChange = {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            context.startActivity(intent)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            )
            SettingsDivider()

            // 屏幕检测 Toggle（AccessibilityService）
            val a11yEnabled = isAccessibilityServiceEnabled(context)
            val a11yRunning = PaymentAccessibilityService.isServiceEnabled
            SettingsRow(
                iconBg = Color(0xFFF3E5F5), icon = "📱",
                title = "屏幕检测",
                desc = when {
                    !a11yEnabled -> "未开启，可检测银行等无通知的支付页面"
                    !a11yRunning -> "已授权，服务未启动（重启App试试）"
                    else -> "正在检测支付页面（补充通知监听）"
                },
                titleColor = if (a11yEnabled && a11yRunning)
                    MaterialTheme.colorScheme.onSurface else Color(0xFF7B1FA2),
                trailing = {
                    Switch(
                        checked = a11yEnabled && a11yRunning,
                        onCheckedChange = {
                            // 跳转到无障碍设置页面
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            )
            SettingsDivider()

            // 悬浮气泡 Toggle
            var bubbleEnabled by remember { mutableStateOf(BubbleService.isRunning) }
            SettingsRow(
                iconBg = Color(0xFFE3F2FD), icon = "💬",
                title = "悬浮气泡",
                desc = if (bubbleEnabled) "已开启，点击气泡快速记账" else "开启后随时快速记账，无需打开App",
                trailing = {
                    Switch(
                        checked = bubbleEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                // 检查悬浮窗权限
                                if (Settings.canDrawOverlays(context)) {
                                    BubbleService.start(context)
                                    bubbleEnabled = true
                                } else {
                                    // 跳转到悬浮窗权限设置
                                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                }
                            } else {
                                BubbleService.stop(context)
                                bubbleEnabled = false
                            }
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

            // Android 13+ 通知权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                SettingsRow(
                    iconBg = Color(0xFFFFF3E0), icon = "📢",
                    title = "通知权限",
                    desc = "Android 13+ 需要单独授予通知权限",
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(ExpenseRed)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("需开启", color = Color.White, fontSize = 10.sp)
                        }
                    },
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
                SettingsDivider()
            }

            // 记账模式
            val recordMode by ThemePreferences.getRecordMode(context).collectAsState(initial = RecordMode.CONFIRM)
            SettingsRow(
                iconBg = Color(0xFFE3F2FD), icon = "⚡",
                title = "记账模式",
                desc = if (recordMode == RecordMode.QUICK) "极速模式：输入后500ms自动保存" else "确认模式：手动点击按钮保存",
                trailing = {
                    Switch(
                        checked = recordMode == RecordMode.QUICK,
                        onCheckedChange = { isQuick ->
                            scope.launch {
                                ThemePreferences.setRecordMode(
                                    context,
                                    if (isQuick) RecordMode.QUICK else RecordMode.CONFIRM
                                )
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            )
            SettingsDivider()

            // 自动确认规则
            SettingsRow(
                iconBg = Color(0xFFE8F5E9), icon = "🤖",
                title = "自动确认规则",
                desc = "置信度 ≥ ${confidenceThreshold}% 时自动确认",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) },
                onClick = { showConfidenceDialog = true }
            )
            SettingsDivider()

            // 左滑删除模式
            val swipeDeleteMode by ThemePreferences.getSwipeDeleteMode(context).collectAsState(initial = 0)
            SettingsRow(
                iconBg = Color(0xFFFFF3E0), icon = "👆",
                title = "左滑删除模式",
                desc = if (swipeDeleteMode == 0) "左滑直接删除（可撤销）" else "左滑显示删除按钮，点击确认",
                trailing = {
                    Switch(
                        checked = swipeDeleteMode == 1,
                        onCheckedChange = { showButton ->
                            scope.launch {
                                ThemePreferences.setSwipeDeleteMode(context, if (showButton) 1 else 0)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
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
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) },
                onClick = onNavigateToCategoryManagement
            )
            SettingsDivider()

            // 备份数据
            SettingsRow(
                iconBg = Color(0xFFE8F5E9), icon = "💾",
                title = "备份数据",
                desc = "备份数据库到本地文件（可选加密）",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) },
                onClick = {
                    showBackupPasswordDialog = true
                }
            )
            if (backupMsg.isNotEmpty()) {
                Text(
                    backupMsg,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
            SettingsDivider()

            // 恢复数据
            val restoreLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    // 检查是否是加密文件
                    val fileName = getFileName(context, it)
                    if (fileName?.endsWith(".enc") == true) {
                        pendingRestoreUri = it
                        showRestorePasswordDialog = true
                    } else {
                        scope.launch {
                            val result = BackupUtil.restore(context, it)
                            restoreMsg = result.fold(
                                onSuccess = { "恢复成功，请重启应用" },
                                onFailure = { "恢复失败：${it.message}" }
                            )
                        }
                    }
                }
            }
            SettingsRow(
                iconBg = Color(0xFFFFF3E0), icon = "📥",
                title = "恢复数据",
                desc = "从备份文件恢复数据",
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp) },
                onClick = {
                    restoreLauncher.launch("*/*")
                }
            )
            if (restoreMsg.isNotEmpty()) {
                Text(
                    restoreMsg,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
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

            // 截屏保护
            val screenshotProtection by ThemePreferences.getScreenshotProtection(context).collectAsState(initial = false)
            SettingsRow(
                iconBg = Color(0xFFE8F5E9), icon = "🔒",
                title = "截屏保护",
                desc = if (screenshotProtection) "已开启，防止截屏录屏泄露数据" else "开启后禁止截屏录屏",
                trailing = {
                    Switch(
                        checked = screenshotProtection,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                ThemePreferences.setScreenshotProtection(context, enabled)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
            )
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
            // 深色模式选择
            SettingsRow(
                iconBg = Color(0xFFF1F8E9), icon = "🌙",
                title = "深色模式",
                desc = when (themeMode) {
                    ThemeMode.FOLLOW_SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色模式"
                    ThemeMode.DARK -> "深色模式"
                },
                trailing = {
                    // 三选一按钮
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(2.dp)
                    ) {
                        listOf(
                            ThemeMode.FOLLOW_SYSTEM to "系统",
                            ThemeMode.LIGHT to "浅色",
                            ThemeMode.DARK to "深色"
                        ).forEach { (mode, label) ->
                            val isSelected = themeMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        scope.launch {
                                            ThemePreferences.setThemeMode(context, mode)
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
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
        val detections = PaymentDetector.detectionLogs
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
                desc = if (isCheckingUpdate) "正在检查更新..." else "记一下 v${BuildConfig.VERSION_NAME} · 点击检查更新",
                onClick = {
                    if (!isCheckingUpdate) {
                        isCheckingUpdate = true
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate()
                            isCheckingUpdate = false
                            if (info != null) {
                                updateInfo = info
                                showUpdateDialog = true
                            } else {
                                showNoUpdateToast = true
                            }
                        }
                    }
                }
            )
            SettingsDivider()
            val crashLogCount = remember { CrashHandler.getCrashLogs(context).size }
            SettingsRow(
                iconBg = Color(0xFFFFEBEE), icon = "💥",
                title = "崩溃日志",
                desc = if (crashLogCount > 0) "${crashLogCount} 条崩溃记录" else "暂无崩溃记录",
                onClick = onNavigateToCrashLog
            )
        }

        Spacer(Modifier.height(24.dp))
    }
    } // Scaffold

    // 清空数据确认弹窗
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有数据？") },
            text = { Text("此操作将自动备份后永久删除所有记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    val db = (context.applicationContext as JiYiXiaApp).database
                    val repo = RecordRepository(db.recordDao(), db.categoryDao())
                    scope.launch {
                        // 1. 先备份
                        val backupResult = BackupUtil.backup(context)
                        if (backupResult.isFailure) {
                            showClearDialog = false
                            return@launch
                        }

                        // 2. 备份成功后删除
                        repo.deleteAll()
                        showClearDialog = false
                    }
                }) {
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

    // 置信度阈值设置弹窗
    if (showConfidenceDialog) {
        var sliderValue: Float by remember { mutableStateOf(confidenceThreshold.toFloat()) }
        AlertDialog(
            onDismissRequest = { showConfidenceDialog = false },
            title = { Text("自动确认阈值") },
            text = {
                Column {
                    Text("置信度 ≥ ${sliderValue.toInt()}% 时自动确认记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("当前阈值：${sliderValue.toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 50f..100f,
                        steps = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("50%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("100%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        ThemePreferences.setConfidenceThreshold(context, sliderValue.toInt())
                    }
                    showConfidenceDialog = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfidenceDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 更新对话框
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本") },
            text = {
                Column {
                    Text("新版本：v${updateInfo!!.versionName}", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text("更新日志：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            updateInfo!!.changelog,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    UpdateChecker.openDownloadPage(context, updateInfo!!.downloadUrl)
                    showUpdateDialog = false
                }) {
                    Text("下载更新")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("稍后再说")
                }
            }
        )
    }

    // 已是最新版本 Toast
    if (showNoUpdateToast) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            showNoUpdateToast = false
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier.padding(bottom = 80.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface
            ) {
                Text(
                    "已是最新版本 ✓",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    fontSize = 14.sp
                )
            }
        }
    }

    // 备份密码输入对话框
    if (showBackupPasswordDialog) {
        var passwordInput by remember { mutableStateOf("") }
        var useEncryption by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showBackupPasswordDialog = false },
            title = { Text("备份数据") },
            text = {
                Column {
                    Text("是否对备份文件加密？", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useEncryption,
                            onCheckedChange = { useEncryption = it }
                        )
                        Text("加密备份（推荐）")
                    }
                    if (useEncryption) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("设置密码") },
                            placeholder = { Text("输入备份密码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "⚠️ 请牢记密码，恢复时需要输入",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupPasswordDialog = false
                        scope.launch {
                            val password = if (useEncryption && passwordInput.isNotEmpty()) passwordInput else null
                            val result = BackupUtil.backup(context, password)
                            backupMsg = result.fold(
                                onSuccess = { "已备份到 $it" },
                                onFailure = { "备份失败：${it.message}" }
                            )
                        }
                    },
                    enabled = !useEncryption || passwordInput.isNotEmpty()
                ) {
                    Text("开始备份")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 恢复密码输入对话框
    if (showRestorePasswordDialog && pendingRestoreUri != null) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRestorePasswordDialog = false },
            title = { Text("输入解密密码") },
            text = {
                Column {
                    Text("此备份文件已加密，请输入密码", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("密码") },
                        placeholder = { Text("输入备份密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestorePasswordDialog = false
                        scope.launch {
                            val result = BackupUtil.restore(context, pendingRestoreUri!!, passwordInput)
                            restoreMsg = result.fold(
                                onSuccess = { "恢复成功，请重启应用" },
                                onFailure = { "恢复失败：${it.message}" }
                            )
                            pendingRestoreUri = null
                        }
                    },
                    enabled = passwordInput.isNotEmpty()
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRestorePasswordDialog = false
                    pendingRestoreUri = null
                }) {
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
    HorizontalDivider(
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
                appendLine("${sdf.format(Date(r.date))},$cat,${r.amount.toAmountNumber()},$type,${r.note}")
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
