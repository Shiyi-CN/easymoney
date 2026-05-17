package com.jiyixia.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jiyixia.app.JiYiXiaApp
import com.jiyixia.app.data.entity.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var exportMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // 数据管理
        Text("数据管理", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val app = context.applicationContext as JiYiXiaApp
                kotlinx.coroutines.MainScope().launch {
                    exportMsg = exportCsv(app) ?: "导出失败"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("导出 CSV")
        }

        if (exportMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(exportMsg, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 通知设置
        Text("通知", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("通知监听权限设置")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "开启后可自动识别支付宝/微信支付通知",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 关于
        Text("关于", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("记一下 v1.0.0", style = MaterialTheme.typography.bodyMedium)
        Text("轻量记账，支付即记账", style = MaterialTheme.typography.bodySmall)
    }
}

private suspend fun exportCsv(app: JiYiXiaApp): String? = withContext(Dispatchers.IO) {
    try {
        val db = app.database
        val records = db.recordDao().getAll().first()
        val categories = db.categoryDao().getAll().first()

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, "记一下_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.csv")

        FileWriter(file).use { writer ->
            writer.append("日期,分类,金额,类型,备注\n")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            records.forEach { record ->
                val cat = categories.find { it.id == record.categoryId }?.name ?: "未知"
                val type = if (record.type == 0) "支出" else "收入"
                writer.append("${sdf.format(Date(record.date))},$cat,${record.amount},$type,${record.note}\n")
            }
        }
        "已导出到 ${file.absolutePath}"
    } catch (e: Exception) {
        "导出失败：${e.message}"
    }
}
