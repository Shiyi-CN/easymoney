package com.jiyixia.app.util

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 语音权限桥接器
 * 解决 Xiaomi/国产 ROM 上 Compose 内 ActivityResultLauncher 不生效的问题
 * 使用传统 ActivityCompat.requestPermissions + onRequestPermissionsResult
 */
object VoicePermissionBridge {
    const val REQUEST_CODE = 1001
    val result = MutableStateFlow<Boolean?>(null)

    fun reset() {
        result.value = null
    }

    fun onResult(granted: Boolean) {
        result.value = granted
    }
}
