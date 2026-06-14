package com.jiyixia.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jiyixia.app.util.CrashHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen() {
    val context = LocalContext.current
    var crashLogs by remember { mutableStateOf(CrashHandler.getCrashLogs(context)) }
    var selectedLog by remember { mutableStateOf<File?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLogContent by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部标题
        Surface(color = MaterialTheme.colorScheme.surface) {
            Text(
                "崩溃日志",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }

        if (crashLogs.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "暂无崩溃记录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "APP 运行稳定，未捕获到崩溃",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            // 统计信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "共 ${crashLogs.size} 条崩溃记录",
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp
                        )
                        Text(
                            "点击查看详情，可分享给开发者",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showClearDialog = true }) {
                        Text("清空", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 日志列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(crashLogs) { logFile ->
                    CrashLogItem(
                        logFile = logFile,
                        onClick = {
                            selectedLog = logFile
                            showLogContent = true
                        }
                    )
                }
            }
        }
    }

    // 日志详情对话框
    if (showLogContent && selectedLog != null) {
        AlertDialog(
            onDismissRequest = { showLogContent = false },
            title = { Text("崩溃详情") },
            text = {
                val content = remember(selectedLog) { selectedLog!!.readText() }
                Box(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(
                        onClick = {
                            // 分享日志
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                selectedLog!!
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享崩溃日志"))
                        }
                    ) {
                        Text("分享")
                    }
                    TextButton(onClick = { showLogContent = false }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空崩溃日志") },
            text = { Text("确定要清空所有崩溃日志吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        CrashHandler.clearCrashLogs(context)
                        crashLogs = emptyList()
                        showClearDialog = false
                    }
                ) {
                    Text("清空", color = MaterialTheme.colorScheme.error)
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

@Composable
private fun CrashLogItem(logFile: File, onClick: () -> Unit) {
    // 从文件名解析时间：crash_20260614_123456.txt
    val timestamp = remember {
        try {
            val name = logFile.nameWithoutExtension // crash_20260614_123456
            val parts = name.split("_")
            if (parts.size >= 3) {
                val dateStr = "${parts[1]}_${parts[2]}"
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).parse(dateStr)?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(it)
                }
            } else null
        } catch (e: Exception) {
            null
        } ?: logFile.name
    }

    // 读取异常类型
    val exceptionType = remember {
        try {
            val content = logFile.readText()
            val stackTraceIndex = content.indexOf("=== 异常堆栈 ===")
            if (stackTraceIndex >= 0) {
                val marker = "=== 异常堆栈 ==="
                val afterMarker = content.substring(stackTraceIndex + marker.length).trim()
                val firstLine = afterMarker.lines().firstOrNull { it.isNotBlank() } ?: ""
                // 提取异常类名：java.lang.RuntimeException: xxx
                firstLine.substringBefore(":").trim().substringAfterLast(".")
            } else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Text("💥", fontSize = 20.sp)
            }

            Spacer(Modifier.width(12.dp))

            // 信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exceptionType,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timestamp,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 箭头
            Text(
                "›",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
