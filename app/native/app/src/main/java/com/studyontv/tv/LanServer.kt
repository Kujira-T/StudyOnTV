package com.studyontv.tv

import android.content.Context
import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

















class LanServer(
    private val context: Context,
    private val store: LocalStore,
    private val assets: AssetManager,
    private val port: Int = 8099
) {
    private var socket: ServerSocket? = null
    // 浏览器会并发 6 个连接加载面板，线程太少会排队；静态文件已缓存压缩，CPU 开销很小
    private val pool = Executors.newFixedThreadPool(if (BuildConfig.FLAVOR == "tv") 4 else 6)
    @Volatile private var running = false
    var boundPort: Int = port
        private set

    fun start() {
        if (running) return
        boundPort = tryBind(port) ?: tryBind(8090) ?: return
        running = true
        Thread {
            while (running) {
                try {
                    val client = socket!!.accept()
                    pool.execute { handle(client) }
                } catch (e: Exception) {
                    if (running) Thread.sleep(50)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        try { socket?.close() } catch (e: Exception) { }
    }

    private fun tryBind(p: Int): Int? = try {
        socket = ServerSocket(p)
        p
    } catch (e: Exception) {
        null
    }

    
    private fun handle(client: Socket) {
        client.use { c ->
            c.soTimeout = 8000
            try {
                val input = BufferedInputStream(c.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val method = parts[0].uppercase()
                val path = parts[1].substringBefore('?')
                val query = parts[1].substringAfter('?', "")

                val headers = HashMap<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val idx = line.indexOf(':')
                    if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                val length = headers["content-length"]?.toIntOrNull() ?: 0
                if (length !in 0..4000000) return reply(c.getOutputStream(), 413, "text/plain", "Request too large".toByteArray())
                val body = if (length > 0) readBody(input, length) else ""

                val out = c.getOutputStream()
                if (method == "OPTIONS") return reply(out, 204, null, null)
                val local = c.inetAddress?.isLoopbackAddress ?: false
                route(method, path, query, headers, body, out, local)
            } catch (e: Exception) {
                
            }
        }
    }

    private fun route(method: String, path: String, query: String, headers: Map<String, String>, body: String, out: OutputStream, local: Boolean) {
        val q = if (query.isEmpty()) emptyMap() else query.split("&").associate {
            val kv = it.split("=", limit = 2)
            kv[0] to java.net.URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
        }
        val deviceAccess = local && headers["x-device-token"] == AdminAuth.deviceToken
        val token = headers["authorization"]?.removePrefix("Bearer ") ?: headers["x-admin-token"]
        if (path == "/api/auth" && method == "POST") {
            val value = try { JSONObject(body).optString("passphrase") } catch (e: Exception) { "" }
            val session = AdminAuth.login(context, value)
            return if (session == null) json(out, 401, err("管理密码错误，连续错误 5 次后需等待一分钟"))
                else json(out, 200, JSONObject().put("token", session).put("expiresIn", 43200))
        }
        if (path.startsWith("/api/") && path != "/api/hello") {
            val adminKey = headers["x-admin-key"]
            val authorized = deviceAccess || AdminAuth.valid(token) || (adminKey != null && AdminAuth.verify(context, adminKey))
            if (!authorized) return json(out, 401, err("unauthorized"))
        }

        try {
        when {
            path == "/api/hello" && method == "GET" -> {
                val info = JSONObject()
                    .put("app", "StudyOnTV")
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("name", Prefs.deviceName(context))
                    .put("ip", localIp() ?: "")
                    .put("port", boundPort)
                    .put("questions", store.countQuestions())
                    .put("needsToken", true)
                    .put("passwordRequired", true)
                return json(out, 200, info)
            }
            path == "/api/state" && method == "GET" -> {
                DeviceRuntime.learning.flushUsage()
                return json(out, 200, JSONObject()
                    .put("config", store.config())
                    .put("today", store.todayStats())
                    .put("questions", store.countQuestions())
                        .put("roundsDoneToday", store.roundsDoneToday())
                        .put("repetition", store.repetitionStats())
                        .put("learning", DeviceRuntime.learning.status()))
            }
            path == "/api/config" && method == "PUT" -> {
                val input = try { JSONObject(body) } catch (e: Exception) { return json(out, 200, err("bad_json")) }
                return json(out, 200, JSONObject().put("config", store.saveConfig(input)))
            }
            path == "/api/questions" && method == "GET" -> {
                val items = store.questions(q["subject"], q["level"], q["limit"]?.toIntOrNull() ?: 500)
                return json(out, 200, JSONObject().put("items", items))
            }
            path == "/api/questions" && method == "POST" -> {
                val payload = try { JSONObject(body) } catch (e: Exception) { return json(out, 200, err("bad_json")) }
                if (payload.has("items")) {
                    val added = store.importQuestions(payload.getJSONArray("items"))
                    return json(out, 200, JSONObject().put("ok", true).put("added", added))
                }
                return try {
                    val id = store.upsertQuestion(payload)
                    store.logEvent("question_added", JSONObject().put("id", id))
                    json(out, 200, JSONObject().put("ok", true).put("id", id))
                } catch (e: IllegalArgumentException) {
                    json(out, 200, JSONObject().put("error", "bad_question").put("message", e.message ?: ""))
                }
            }
            path == "/api/questions/delete" && method == "POST" -> {
                val ids = JSONObject(body).getJSONArray("ids")
                require(ids.length() in 1..5000) { "请选择要删除的题目" }
                return json(out, 200, JSONObject().put("ok", true).put("deleted", store.deleteQuestions((0 until ids.length()).map { ids.getString(it) })))
            }
            path.startsWith("/api/questions/") && method == "DELETE" -> {
                val id = path.removePrefix("/api/questions/")
                return json(out, 200, JSONObject().put("ok", store.deleteQuestion(id)))
            }
            path == "/api/history" && method == "GET" -> {
                DeviceRuntime.learning.flushUsage()
                val days = Regex("days=(\\d+)").find(query)?.groupValues?.get(1)?.toIntOrNull() ?: 7
                return json(out, 200, JSONObject().put("items", store.historyDays(days)))
            }
            path == "/api/events" && method == "GET" -> {
                return json(out, 200, JSONObject().put("items", store.recentEvents(q["limit"]?.toIntOrNull() ?: 50)))
            }
            path == "/api/grading" && method == "GET" -> return json(out, 200, GradingService.settings(store).put("providers", GradingService.providers()))
            path == "/api/grading/test" && method == "POST" -> return json(out, 200, GradingService.test(store, JSONObject(body.ifEmpty { "{}" })))
            path == "/api/grading/models" && method == "POST" -> return json(out, 200, GradingService.models(store, JSONObject(body.ifEmpty { "{}" })))
            path == "/api/update" && method == "GET" -> return json(out, 200, Updater.status(context))
            path == "/api/update" && method == "PUT" -> return json(out, 200, Updater.saveSettings(context, JSONObject(body)))
            path == "/api/update/check" && method == "POST" -> {
                // 检查可能要依次尝试多个来源（最长约 1 分钟），放到后台，面板轮询 GET /api/update
                Updater.startCheck(context)
                return json(out, 200, Updater.status(context))
            }
            path == "/api/password" && method == "POST" -> {
                val input = JSONObject(body)
                if (!AdminAuth.verify(context, input.optString("current"))) return json(out, 403, err("wrong_password").put("message", "当前密码不正确，连续错误 5 次后需等待一分钟"))
                AdminAuth.setPassword(context, input.optString("password"))
                return json(out, 200, JSONObject().put("ok", true))
            }
            path == "/api/device" && method == "GET" -> return json(out, 200, JSONObject().put("name", Prefs.deviceName(context))
                .put("version", BuildConfig.VERSION_NAME).put("flavor", BuildConfig.FLAVOR).put("ip", localIp() ?: "").put("port", boundPort))
            path == "/api/device" && method == "PUT" -> {
                val name = JSONObject(body).optString("name").trim()
                require(name.length in 1..32) { "设备名称需要 1–32 个字" }
                Prefs.setDeviceName(context, name)
                return json(out, 200, JSONObject().put("ok", true).put("name", name))
            }
            path == "/api/grading" && method == "PUT" -> return json(out, 200, GradingService.save(store, JSONObject(body)))
            path == "/api/reviews" && method == "GET" -> return json(out, 200, JSONObject().put("items", store.reviews(q["state"] ?: "pending")))
            path.startsWith("/api/reviews/") && method == "POST" -> {
                val id = path.removePrefix("/api/reviews/").substringBefore('/')
                if (path.endsWith("/retry")) {
                    require(store.review(id) != null) { "题目不存在" }
                    GradingService.enqueue(store, DeviceRuntime.learning, id)
                    return json(out, 200, JSONObject().put("ok", true))
                }
                val decision = JSONObject(body)
                return json(out, 200, DeviceRuntime.learning.grade(id, decision.getDouble("score"), decision.optString("feedback"), "parent_or_agent"))
            }
            path == "/api/quiz/start" && method == "POST" -> {
                if (!deviceAccess) return json(out, 403, err("device_only"))
                return json(out, 200, DeviceRuntime.learning.start())
            }
            path == "/api/quiz/review" && method == "POST" -> {
                if (!deviceAccess) return json(out, 403, err("device_only"))
                return json(out, 200, DeviceRuntime.learning.acknowledgeReview(JSONObject(body).getString("reviewId")))
            }
            path == "/api/quiz/answer" && method == "POST" -> {
                if (!deviceAccess) return json(out, 403, err("device_only"))
                val result = DeviceRuntime.learning.answer(JSONObject(body))
                if (result.optBoolean("pending")) GradingService.enqueue(store, DeviceRuntime.learning, result.getString("reviewId"))
                return json(out, 200, result)
            }
            path == "/api/event" && method == "POST" -> {
                val payload = try { JSONObject(body) } catch (e: Exception) { JSONObject() }
                val type = payload.optString("type")
                if (type != "study") return json(out, 403, err("event_managed_by_device"))
                store.logEvent(type, payload)
                return json(out, 200, JSONObject().put("ok", true))
            }
        }
        } catch (e: Exception) {
            return json(out, 400, JSONObject().put("error", "bad_request").put("message", e.message ?: "请求格式错误"))
        }

        
        if (method != "GET") return json(out, 405, err("method_not_allowed"))
        serveStatic(path, out, headers["accept-encoding"]?.contains("gzip") == true, headers["if-none-match"])
    }



    
    /** gzip 后的静态文件缓存（资源随 APK 固定不变，压缩一次即可） */
    private val gzipCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    // 包含安装时间：同版本号的重新安装 / 调试构建也会让浏览器拿到新页面
    private val etag = "\"" + BuildConfig.VERSION_CODE + "-" + (try { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime } catch (_: Exception) { 0L }) + "\""

    private fun serveStatic(path: String, out: OutputStream, gzip: Boolean, ifNoneMatch: String?) {
        val (assetPath, contentType) = when {
            path == "/" || path == "/index.html" -> "web/index.html" to "text/html; charset=utf-8"
            path.startsWith("/admin/assets/") -> "admin" + path.removePrefix("/admin") to mime(path)
            path.startsWith("/admin") -> "admin/index.html" to "text/html; charset=utf-8"
            path.startsWith("/assets/") -> "web" + path to mime(path)
            else -> "web/index.html" to "text/html; charset=utf-8"
        }
        if (assetPath.contains("..")) return reply(out, 404, "text/plain; charset=utf-8", "404".toByteArray())
        // 带哈希的打包文件和字体可以长期缓存；页面本身每次校验（ETag = 版本号，升级后自动失效）
        val hashed = Regex("-[A-Za-z0-9_-]{8}\\.(js|css)$").containsMatchIn(assetPath) || assetPath.contains("/vendor/")
        val cache = if (hashed) "public, max-age=31536000, immutable" else "no-cache"
        if (ifNoneMatch == etag) return reply(out, 304, null, null, mapOf("ETag" to etag, "Cache-Control" to cache))
        try {
            val compressible = contentType.startsWith("text/") || contentType.contains("javascript") || contentType.contains("json") || contentType.contains("svg")
            val extra = mutableMapOf("ETag" to etag, "Cache-Control" to cache)
            val bytes = if (gzip && compressible) {
                extra["Content-Encoding"] = "gzip"; extra["Vary"] = "Accept-Encoding"
                gzipCache.getOrPut(assetPath) {
                    val raw = assets.open(assetPath).use { it.readBytes() }
                    ByteArrayOutputStream().also { buf -> java.util.zip.GZIPOutputStream(buf).use { it.write(raw) } }.toByteArray()
                }
            } else assets.open(assetPath).use { it.readBytes() }
            reply(out, 200, contentType, bytes, extra)
        } catch (e: Exception) {
            reply(out, 404, "text/plain; charset=utf-8", "404".toByteArray())
        }
    }

    private fun mime(path: String): String = when {
        path.endsWith(".css") -> "text/css; charset=utf-8"
        path.endsWith(".js") -> "application/javascript; charset=utf-8"
        path.endsWith(".json") -> "application/json; charset=utf-8"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".jpg") -> "image/jpeg"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".woff2") -> "font/woff2"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".ttf") -> "font/ttf"
        path.endsWith(".txt") -> "text/plain; charset=utf-8"
        else -> "application/octet-stream"
    }

    
    private fun json(out: OutputStream, code: Int, obj: JSONObject) =
        reply(out, code, "application/json; charset=utf-8", obj.toString().toByteArray())

    private fun err(code: String) = JSONObject().put("error", code)

    private fun reply(out: OutputStream, code: Int, contentType: String?, bytes: ByteArray?, extra: Map<String, String> = emptyMap()) {
        val body = bytes ?: ByteArray(0)
        val head = StringBuilder()
        head.append("HTTP/1.1 ").append(code).append(" ").append(reason(code)).append("\r\n")
        if (contentType != null) head.append("Content-Type: ").append(contentType).append("\r\n")
        head.append("Content-Length: ").append(body.size).append("\r\n")
        head.append("Connection: close\r\n")
        if (!extra.containsKey("Cache-Control")) head.append("Cache-Control: no-store\r\n")
        extra.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
        head.append("Access-Control-Allow-Origin: *\r\n")
        head.append("Access-Control-Allow-Methods: GET,POST,PUT,DELETE,OPTIONS\r\n")
        head.append("Access-Control-Allow-Headers: Content-Type,X-Admin-Token,Authorization,X-Admin-Key,X-Device-Token\r\n")
        head.append("Access-Control-Allow-Private-Network: true\r\n")
        head.append("\r\n")
        out.write(head.toString().toByteArray())
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 204 -> "No Content"; 304 -> "Not Modified"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "OK"
    }

    
    private fun readLine(input: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) {
                val bytes = buf.toByteArray()
                return String(bytes, 0, if (bytes.isNotEmpty() && bytes[bytes.size - 1] == '\r'.code.toByte()) bytes.size - 1 else bytes.size, Charsets.UTF_8)
            }
            buf.write(b)
            prev = b
        }
    }

    private fun readBody(input: BufferedInputStream, length: Int): String {
        val buf = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buf, read, length - read)
            if (n <= 0) break
            read += n
        }
        return String(buf, 0, read, Charsets.UTF_8)
    }

    
    fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }
}
