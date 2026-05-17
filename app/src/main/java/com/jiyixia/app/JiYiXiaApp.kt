package com.jiyixia.app

import android.app.Application
import com.jiyixia.app.data.AppDatabase
import com.jiyixia.app.data.PresetCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            if (existing.isEmpty()) {
                PresetCategories.all.forEach { dao.insert(it) }
            }
        }
    }
}
