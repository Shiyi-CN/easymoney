package com.jiyixia.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

// DataStore 扩展
val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_settings")

object ThemePreferences {
    private val THEME_MODE_KEY = intPreferencesKey("theme_mode")

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
}