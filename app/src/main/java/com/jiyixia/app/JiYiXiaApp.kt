package com.jiyixia.app

import android.app.Application
import com.jiyixia.app.data.AppDatabase
import com.jiyixia.app.data.PresetCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.jiyixia.app.util.CrashHandler
import com.jiyixia.app.util.RuleManager

class JiYiXiaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // 初始化崩溃日志捕获
        CrashHandler.getInstance().init(this)
        // 加载识别规则
        RuleManager.load(this)
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
