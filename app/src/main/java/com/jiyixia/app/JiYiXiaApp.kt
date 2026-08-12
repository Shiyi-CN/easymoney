package com.jiyixia.app

import android.app.Application
import android.util.Log
import com.jiyixia.app.data.AppDatabase
import com.jiyixia.app.data.PresetCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.jiyixia.app.util.CrashHandler
import com.jiyixia.app.util.RuleManager
import com.jiyixia.app.util.KeywordMappingManager
import com.jiyixia.app.service.DedupManager
import com.iflytek.cloud.SpeechConstant
import com.iflytek.cloud.SpeechUtility

class JiYiXiaApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        // 初始化崩溃日志捕获
        CrashHandler.getInstance().init(this)
        // 加载识别规则
        RuleManager.load(this)
        // 初始化去重管理器（加载持久化缓存，防止重启后重复记录）
        DedupManager.init(this)
        // 初始化自定义关键词映射（一木记账核心功能：用户自定义关键词→分类）
        KeywordMappingManager.init(this)
        // 初始化讯飞 MSC 语音听写 SDK
        initXfyunMSC()
        initPresetCategories()
    }

    /**
     * 初始化讯飞 MSC SDK（语音听写）
     *
     * 支持在线+离线识别。APPID=682523c8
     */
    private fun initXfyunMSC() {
        try {
            // 参数：appid=xxx
            val param = "${SpeechConstant.APPID}=682523c8"
            SpeechUtility.createUtility(this, param)
            Log.i("JiYiXiaApp", "讯飞MSC SDK初始化成功")
        } catch (e: Exception) {
            Log.e("JiYiXiaApp", "讯飞MSC SDK初始化异常", e)
        }
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
