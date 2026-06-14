package com.jiyixia.app.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jiyixia.app.data.ThemeMode
import com.jiyixia.app.data.ThemePreferences
import com.jiyixia.app.ui.navigation.Screen
import com.jiyixia.app.ui.navigation.screens
import com.jiyixia.app.ui.screens.HomeScreen
import com.jiyixia.app.ui.screens.QuickRecordScreen
import com.jiyixia.app.ui.screens.ReimbursableRecordsScreen
import com.jiyixia.app.ui.screens.CategoryManagementScreen
import com.jiyixia.app.ui.screens.CrashLogScreen
import com.jiyixia.app.ui.screens.SettingsScreen
import com.jiyixia.app.ui.screens.StatsScreen
import com.jiyixia.app.ui.screens.YearStatsScreen
import com.jiyixia.app.ui.theme.JiYiXiaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeMode by ThemePreferences.getThemeMode(context).collectAsState(initial = ThemeMode.FOLLOW_SYSTEM)
            val screenshotProtection by ThemePreferences.getScreenshotProtection(context).collectAsState(initial = false)

            // 截屏保护
            LaunchedEffect(screenshotProtection) {
                if (screenshotProtection) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            JiYiXiaTheme(themeMode = themeMode) {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                // 回到首页时，清除所有二级页面
                                popUpTo(Screen.Home.route) {
                                    saveState = false  // 不保存二级页面状态
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToQuickRecord = {
                        navController.navigate(Screen.QuickRecord.route)
                    },
                    onNavigateToReimbursable = {
                        navController.navigate(Screen.ReimbursableRecords.route)
                    }
                )
            }
            composable(Screen.Stats.route) {
                StatsScreen(
                    onNavigateToReimbursableRecords = {
                        navController.navigate(Screen.ReimbursableRecords.route)
                    },
                    onNavigateToYearStats = {
                        navController.navigate(Screen.YearStats.route)
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToCategoryManagement = {
                        navController.navigate(Screen.CategoryManagement.route)
                    },
                    onNavigateToCrashLog = {
                        navController.navigate(Screen.CrashLog.route)
                    }
                )
            }
            composable(Screen.QuickRecord.route) {
                QuickRecordScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.ReimbursableRecords.route) {
                ReimbursableRecordsScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.CategoryManagement.route) {
                CategoryManagementScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.CrashLog.route) {
                CrashLogScreen()
            }
            composable(Screen.YearStats.route) {
                YearStatsScreen()
            }
        }
    }
}
