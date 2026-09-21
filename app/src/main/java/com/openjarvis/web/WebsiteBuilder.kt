package com.openjarvis.web

import android.content.Context
import com.openjarvis.llm.UniversalAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.openjarvis.quality.ProductionGuard

class WebsiteBuilder(private val context: Context) {
    private val llm = UniversalAdapter(context)
    private val root = File(context.filesDir, "websites").also { it.mkdirs() }

    suspend fun build(prompt: String, siteName: String = "jarvis-site"): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safe = siteName.replace(Regex("[^A-Za-z0-9_-]"), "-").lowercase()
            val dir = File(root, safe).apply { mkdirs() }
            val system = """
                You are a production static website generator. Return ONLY JSON: {\"files\":[{\"path\":\"index.html\",\"content\":\"...\"},...]}. 
                Generate complete self-contained HTML/CSS/JS, responsive, accessible, no external dependencies unless explicitly requested.
                Required files: index.html, styles.css, script.js, README.md.
                Never put executable server secrets in frontend code.
            """.trimIndent()
            val raw = llm.complete(system, prompt).getOrThrow().trim().removePrefix("```json").removeSuffix("```").trim()
            val files = JSONObject(raw).getJSONArray("files")
            for (i in 0 until files.length()) {
                val item = files.getJSONObject(i)
                val relative = ProductionGuard.safeSitePath(item.getString("path"))
                val file = File(dir, relative)
                file.parentFile?.mkdirs(); file.writeText(item.optString("content"))
            }
            if (!File(dir, "index.html").exists()) error("Generated site has no index.html")
            val zip = File(root, "$safe.zip")
            ZipOutputStream(zip.outputStream()).use { out ->
                dir.walkTopDown().filter { it.isFile }.forEach { file ->
                    val entryName = file.relativeTo(dir).path.replace(File.separatorChar, '/')
                    out.putNextEntry(ZipEntry(entryName)); file.inputStream().use { it.copyTo(out) }; out.closeEntry()
                }
            }
            zip
        }
    }
}
