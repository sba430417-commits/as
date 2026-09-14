package com.example.passvault.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.example.passvault.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val RELEASES_URL = "https://api.github.com/repos/sba430417-commits/as/releases/latest"
    private const val MAIN_COMMIT_URL = "https://api.github.com/repos/sba430417-commits/as/commits/main"
    private const val PREFS = "app_updates"
    private const val DOWNLOAD_ID = "download_id"

    data class Release(val versionName: String, val apkUrl: String, val notes: String)

    suspend fun latestRelease(): Release? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "YourAccount-Android")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val json = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val tag = root.optString("tag_name").removePrefix("v").trim()
            val assets = root.optJSONArray("assets") ?: return@withContext null
            val asset = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").lowercase().endsWith(".apk") }
                ?: return@withContext null
            Release(tag, asset.optString("browser_download_url"), root.optString("body"))
        } catch (_: Exception) { null }
    }

    fun isNewer(release: Release): Boolean = compareVersions(release.versionName, BuildConfig.VERSION_NAME) > 0

    suspend fun latestMainCommit(): String? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(MAIN_COMMIT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "YourAccount-Android")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optString("sha").ifBlank { null }
        } catch (_: Exception) { null }
    }

    fun isCommitOutdated(latestCommit: String?): Boolean =
        !latestCommit.isNullOrBlank() && BuildConfig.BUILD_COMMIT != "local-build" &&
            !latestCommit.equals(BuildConfig.BUILD_COMMIT, ignoreCase = true)

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrNull(i) ?: 0) - (b.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    fun startDownload(context: Context, release: Release): Long {
        val request = DownloadManager.Request(Uri.parse(release.apkUrl)).apply {
            setTitle("تحديث Your Account")
            setDescription("جاري تنزيل الإصدار ${release.versionName}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
            setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, "your-account-${release.versionName}.apk")
        }
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(DOWNLOAD_ID, id).apply()
        return id
    }

    fun savedDownloadId(context: Context): Long = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(DOWNLOAD_ID, -1L)
    fun clearSavedDownload(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(DOWNLOAD_ID).apply()
}
