package com.jiyixia.app

import android.app.Application
import com.jiyixia.app.data.AppDatabase
import com.jiyixia.app.data.PresetCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class JiYiXiaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        initPresetCategories()
    }

    private fun initPresetCategories() {
        CoroutineScope(Dispatchers.IO).launch {
            val dao = database.categoryDao()
            val existing = dao.getAll().first()

            // 安全更新：只添加不存在的分类，不删除旧分类
            val existingNames = existing.map { it.name }.toSet()

            PresetCategories.all.forEach { preset ->
                if (preset.name !in existingNames) {
                    dao.insert(preset)
                }
            }
        }
    }
}
