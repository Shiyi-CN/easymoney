package com.jiyixia.app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel

class StatsViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StatsViewModel(application) as T
    }
}
