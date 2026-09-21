package com.openjarvis.apps

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Downloads APKs and hands installation to Android's package installer. It never silently installs apps. */
class AppInstaller(private val context: Context) {
    private val client = OkHttpClient()

    suspend fun downloadApk(url: String, name: String = "download.apk"): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://")) { "Only HTTPS APK URLs are allowed" }
            val file = File(context.cacheDir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            val body = response.body ?: error("Empty download")
            body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file
        }
    }

    fun requestInstall(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
