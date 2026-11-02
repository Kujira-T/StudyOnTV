package com.studyontv.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL





object ApkInstaller {

    private fun dir(ctx: Context): File = File(ctx.cacheDir, "updates").apply { mkdirs() }

    fun apkFile(ctx: Context): File = File(dir(ctx), "update.apk")

    
    private fun validUpdate(ctx: Context, file: File): Boolean {
        val archive = ctx.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return archive.packageName == ctx.packageName && archive.versionCode > current.versionCode
    }

    fun alreadyDownloaded(ctx: Context, sizeHint: Long): Boolean {
        val f = apkFile(ctx)
        return f.exists() && (sizeHint <= 0 || f.length() == sizeHint) && validUpdate(ctx, f)
    }

    


    fun download(ctx: Context, url: String, onProgress: (Int) -> Unit): File? {
        val target = apkFile(ctx)
        val partial = File(dir(ctx), "update.pending.apk")
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "StudyOnTV-Updater")
            }
            if (conn.responseCode !in 200..299) return null
            val total = conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } > 0) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                    }
                    output.flush()
                }
            }
            if (!validUpdate(ctx, partial)) return null
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) return null
            onProgress(100)
            return target
        } catch (e: Exception) {
            return null
        } finally {
            partial.delete()
            conn?.disconnect()
        }
    }

    
    fun install(ctx: Context, file: File): Boolean {
        return try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ApkProvider.uriFor(ctx, file)
            } else {
                Uri.fromFile(file)
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            GateService.openingSystemPage()
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
