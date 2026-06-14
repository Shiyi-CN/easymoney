package com.jiyixia.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "首页", Icons.Default.Home)
    data object Stats : Screen("stats", "统计", Icons.Default.BarChart)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
    data object QuickRecord : Screen("quick_record", "快速记账", Icons.Default.Home)
    data object ReimbursableRecords : Screen("reimbursable_records", "待报销记录", Icons.Default.Home)
    data object CategoryManagement : Screen("category_management", "分类管理", Icons.Default.Home)
    data object CrashLog : Screen("crash_log", "崩溃日志", Icons.Default.Home)
    data object YearStats : Screen("year_stats", "年度总览", Icons.Default.Home)
}

val screens = listOf(Screen.Home, Screen.Stats, Screen.Settings)
