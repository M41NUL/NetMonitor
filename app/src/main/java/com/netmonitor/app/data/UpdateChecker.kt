package com.netmonitor.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

const val UPDATE_INFO_URL =
    "https://netmonitorapps-default-rtdb.asia-southeast1.firebasedatabase.app/netmonitor.json"

data class UpdateInfo(
    val versionName: String,
    val url: String,
    val notes: List<String>,
)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

object UpdateChecker {
    @Volatile
    var promptDismissed = false

    suspend fun check(context: Context): UpdateResult = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(UPDATE_INFO_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                useCaches = false
            }
            val text = try {
                if (conn.responseCode !in 200..299) return@runCatching UpdateResult.Failed
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val j = JSONObject(text)
            val info = UpdateInfo(
                versionName = j.getString("latest_version").trim(),
                url = j.getString("download_link").trim(),
                notes = j.optString("update_message", "")
                    .split("|").map { it.trim() }.filter { it.isNotEmpty() },
            )
            if (isNewer(info.versionName, installedVersionName(context))) UpdateResult.Available(info)
            else UpdateResult.UpToDate
        }.getOrDefault(UpdateResult.Failed)
    }

    private fun isNewer(latest: String, installed: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split(".", "-", "+")
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun installedVersionName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrDefault("0")
}
