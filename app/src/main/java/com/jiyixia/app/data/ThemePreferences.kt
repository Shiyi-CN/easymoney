package com.jiyixia.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 主题模式枚举
enum class ThemeMode(val value: Int) {
    FOLLOW_SYSTEM(0),  // 跟随系统
    LIGHT(1),          // 浅色模式
    DARK(2)            // 深色模式
}

// 记账模式枚举
enum class RecordMode(val value: Int) {
    CONFIRM(0),        // 确认模式（手动点按钮保存）
    QUICK(1)           // 极速模式（500ms自动保存）
}

// DataStore 扩展
val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

object ThemePreferences {
    private val THEME_MODE_KEY = intPreferencesKey("theme_mode")
    private val RECORD_MODE_KEY = intPreferencesKey("record_mode")
    private val SCREENSHOT_PROTECTION_KEY = booleanPreferencesKey("screenshot_protection")
    private val CONFIDENCE_THRESHOLD_KEY = intPreferencesKey("confidence_threshold")
    private val SWIPE_DELETE_MODE_KEY = intPreferencesKey("swipe_delete_mode") // 0=直接删除, 1=显示按钮

    // 获取主题模式
    fun getThemeMode(context: Context): Flow<ThemeMode> {
        return context.themeDataStore.data.map { preferences ->
            val value = preferences[THEME_MODE_KEY] ?: ThemeMode.FOLLOW_SYSTEM.value
            ThemeMode.entries.find { it.value == value } ?: ThemeMode.FOLLOW_SYSTEM
        }
    }

    // 保存主题模式
    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        context.themeDataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.value
        }
    }

    // 获取记账模式
    fun getRecordMode(context: Context): Flow<RecordMode> {
        return context.themeDataStore.data.map { preferences ->
            val value = preferences[RECORD_MODE_KEY] ?: RecordMode.CONFIRM.value
            RecordMode.entries.find { it.value == value } ?: RecordMode.CONFIRM
        }
    }

    // 保存记账模式
    suspend fun setRecordMode(context: Context, mode: RecordMode) {
        context.themeDataStore.edit { preferences ->
            preferences[RECORD_MODE_KEY] = mode.value
        }
    }

    // 获取截屏保护状态
    fun getScreenshotProtection(context: Context): Flow<Boolean> {
        return context.themeDataStore.data.map { preferences ->
            preferences[SCREENSHOT_PROTECTION_KEY] ?: false
        }
    }

    // 保存截屏保护状态
    suspend fun setScreenshotProtection(context: Context, enabled: Boolean) {
        context.themeDataStore.edit { preferences ->
            preferences[SCREENSHOT_PROTECTION_KEY] = enabled
        }
    }

    // 获取置信度阈值
    fun getConfidenceThreshold(context: Context): Flow<Int> {
        return context.themeDataStore.data.map { preferences ->
            preferences[CONFIDENCE_THRESHOLD_KEY] ?: 80
        }
    }

    // 保存置信度阈值
    suspend fun setConfidenceThreshold(context: Context, threshold: Int) {
        context.themeDataStore.edit { preferences ->
            preferences[CONFIDENCE_THRESHOLD_KEY] = threshold
        }
    }

    // 获取左滑删除模式（0=直接删除, 1=显示按钮）
    fun getSwipeDeleteMode(context: Context): Flow<Int> {
        return context.themeDataStore.data.map { preferences ->
            preferences[SWIPE_DELETE_MODE_KEY] ?: 0
        }
    }

    // 保存左滑删除模式
    suspend fun setSwipeDeleteMode(context: Context, mode: Int) {
        context.themeDataStore.edit { preferences ->
            preferences[SWIPE_DELETE_MODE_KEY] = mode
        }
    }
}