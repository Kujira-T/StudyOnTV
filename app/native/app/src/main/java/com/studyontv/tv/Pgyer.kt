package com.studyontv.tv

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Public download pages only. Account API credentials are never needed by a device. */
object Pgyer {
    private val shortcut get() = if (BuildConfig.FLAVOR == "tv") "StudyOnTVTV" else "StudyOnTVMobile"
    private val appKey get() = if (BuildConfig.FLAVOR == "tv") "5c78c116f305ded2cd7f34edb69a70ec" else "9fa3ea546ca7601e842e3ead0be1679c"
    val pageUrl get() = "https://www.pgyer.com/$shortcut"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 5.0; StudyOnTV) AppleWebKit/537.36 StudyOnTV-Updater"

    data class Page(val version: String, val notes: String, val buildKey: String, val token: String, val timeSign: String, val signature: String)

    fun isPage(url: String): Boolean = try {
        val parsed = URL(url)
        parsed.protocol == "https" && parsed.host in setOf("www.pgyer.com", "pgyer.com") &&
            Regex("/[A-Za-z0-9_-]+/?").matches(parsed.path)
    } catch (_: Exception) { false }

    @Suppress("DEPRECATION")
    internal fun parse(html: String): Page {
        fun variable(name: String): String = Regex("\\b" + name + "\\s*=\\s*['\"]([^'\"]*)['\"]").find(html)?.groupValues?.get(1).orEmpty()
        require(variable("agKey") == appKey) { "蒲公英应用与本设备不符" }
        val build = variable("aKey")
        require(Regex("[0-9a-f]{32}").matches(build)) { "蒲公英尚未开放下载" }
        val version = Regex("(?:版本[：:]|Version\\s*[:：]?)\\s*(?:<[^>]+>\\s*)*v?([0-9]+(?:\\.[0-9A-Za-z]+)+(?:[-+][A-Za-z0-9.-]+)?)", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: error("无法读取蒲公英版本信息")
        val noteHtml = Regex("<div[^>]+class=['\"][^'\"]*\\bupdate-description\\b[^'\"]*['\"][^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1).orEmpty()
        val notes = Html.fromHtml(noteHtml).toString().trim()
        val token = variable("installToken")
        require(token.isNotEmpty()) { "蒲公英下载凭证未就绪" }
        // The public page's download script lowercases the decoded time-sign bytes.
        val timeSign = variable("timeSign").lowercase().chunked(2).mapNotNull { it.toIntOrNull(16) }
            .map { it.toChar().lowercaseChar().code.toString(16).padStart(2, '0') }.joinToString("")
        return Page(version, notes, build, token, timeSign, variable("sig"))
    }

    fun read(url: String = pageUrl): Page {
        require(isPage(url)) { "无效的蒲公英下载页" }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 6000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "zh-CN")
            setRequestProperty("Accept", "text/html")
        }
        return try {
            require(conn.responseCode in 200..299) { "蒲公英 HTTP ${conn.responseCode}" }
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count: Int
                while (input.read(buffer).also { count = it } > 0) {
                    require(out.size() + count <= 2_000_000) { "蒲公英响应过大" }
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            parse(String(bytes, Charsets.UTF_8))
        } finally { conn.disconnect() }
    }

    fun info(current: String): Updater.Info {
        val page = read()
        return Updater.Info(page.version, current,
            page.notes.contains("[force]", true) && Updater.isNewer(page.version, current), page.notes,
            "https://www.pgyer.com/" + page.buildKey, source = "蒲公英")
    }

    /** Resolve a fresh, short-lived public download ticket immediately before downloading. */
    fun downloadUrl(url: String): String {
        val page = read(url)
        fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
        return "https://www.pgyer.com/app/install/" + page.buildKey + "?time=" + System.currentTimeMillis() +
            "&installToken=" + encoded(page.token) + "&timeSign=" + encoded(page.timeSign) +
            if (page.signature.isNotEmpty()) "&sig=" + encoded(page.signature) else ""
    }
}
