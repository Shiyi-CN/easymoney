package com.jiyixia.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.jiyixia.app.BuildConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GitHub Release 更新检查器
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_API_URL = "https://api.github.com/repos/Syp1012/jiyixia/releases/latest"

    data class UpdateInfo(
        val versionName: String,
        val versionCode: Int,
        val changelog: String,
        val downloadUrl: String,
        val publishedAt: String
    )

    /**
     * 检查是否有新版本（挂起函数，在 IO 线程执行）
     * @return UpdateInfo 如果有新版本，null 如果已是最新
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val tagName = json.getString("tag_name") // e.g., "v1.3.2"
                val body = json.getString("body")
                val publishedAt = json.getString("published_at")

                // 解析版本号
                val remoteVersionName = tagName.removePrefix("v")
                val remoteVersionCode = parseVersionCode(remoteVersionName)

                // 获取 APK 下载链接
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                // 比较版本
                if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                    UpdateInfo(
                        versionName = remoteVersionName,
                        versionCode = remoteVersionCode,
                        changelog = body,
                        downloadUrl = downloadUrl,
                        publishedAt = publishedAt
                    )
                } else {
                    null // 已是最新
                }
            } else {
                Log.e(TAG, "GitHub API 返回 ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败", e)
            null
        }
    }

    /**
     * 解析版本号为整数（如 "1.3.2" -> 10302）
     */
    private fun parseVersionCode(versionName: String): Int {
        return try {
            val parts = versionName.split(".")
            val major = parts.getOrElse(0) { "0" }.toInt()
            val minor = parts.getOrElse(1) { "0" }.toInt()
            val patch = parts.getOrElse(2) { "0" }.toInt()
            major * 10000 + minor * 100 + patch
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 打开浏览器下载 APK
     */
    fun openDownloadPage(context: Context, downloadUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
        context.startActivity(intent)
    }
}
