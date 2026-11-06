package com.studyontv.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject





class UpdateActivity : Activity() {
    private var force = false
    private var sha256 = ""
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        immersive()

        val data = try {
            JSONObject(intent.getStringExtra("payload") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        val latest = data.optString("latest", "")
        val current = data.optString("current", Updater.currentVersion(this))
        val notes = data.optString("notes", "")
            .lineSequence()
            .filter { !it.contains("[force]", true) && it.isNotBlank() }
            .joinToString("\n")
        val url = data.optString("url", "")
        force = data.optBoolean("force", false)
        sha256 = data.optString("sha256", "")

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val palette = TvUi.palette(this)
        root.setBackgroundColor(palette.bg)
        root.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(if (DeviceBridge.isTv(this)) 120 else 24), dp(36), dp(if (DeviceBridge.isTv(this)) 120 else 24), dp(24))

        root.addView(label(getString(R.string.app_name) + if (DeviceBridge.isTv(this)) " · 电视版" else " · 手机 / 平板版", 16f, palette.text2, 4))
        root.addView(label("发现新版本 v$latest", 32f, palette.text, 8))
        root.addView(label("当前 v$current" + if (force) " · 必须更新后才能继续使用" else "", 16f, palette.text2, 14))

        if (notes.isNotEmpty()) {
            root.addView(label("更新内容", 13f, palette.text3, 6, Gravity.START))

            val note = TextView(this)
            note.text = notes
            note.setTextColor(palette.text)
            note.textSize = 15f
            note.setLineSpacing(dp(8).toFloat(), 1.05f)
            note.setPadding(dp(28), dp(20), dp(28), dp(20))
            note.background = solid(palette.surface, 16)

            val scroll = ScrollView(this)
            scroll.addView(note)
            val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
            root.addView(scroll, slp)
        }

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, dp(22)))

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        lateinit var go: android.widget.Button
        go = TvUi.button(this, if (ApkInstaller.alreadyDownloaded(this, 0)) "开始安装" else "立即更新", true) { startUpdate(url, go) }
        go.textSize = 20f
        buttons.addView(go, LinearLayout.LayoutParams(dp(if (DeviceBridge.isTv(this)) 260 else 180), dp(58)))
        if (!force) buttons.addView(TvUi.button(this, "以后再说") { finish() }, LinearLayout.LayoutParams(dp(if (DeviceBridge.isTv(this)) 180 else 130), dp(58)).apply { leftMargin = dp(16) })
        buttons.addView(TvUi.button(this, "手动下载", false) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Pgyer.pageUrl)))
            } catch (e: Exception) {
                status.text = "这台设备没有可用浏览器，请用手机或电脑访问 " + Pgyer.pageUrl
                Diag.error("Update", "cannot open manual download page", e)
            }
        }, LinearLayout.LayoutParams(dp(if (DeviceBridge.isTv(this)) 180 else 130), dp(58)).apply { leftMargin = dp(16) })
        root.addView(buttons)

        status = label("下载后覆盖安装即可，题库和设置不会丢", 12f, palette.text3, 10)
        root.addView(status)

        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(palette.bg); addView(root) })
        if (url.isEmpty()) go.isEnabled = false
        go.requestFocus()
        if (intent.getBooleanExtra("auto", false) && url.isNotEmpty()) {
            go.post { startUpdate(url, go) }
        }
    }

    
    private fun label(text: String, size: Float, color: Int, bottomMarginDp: Int,
                      gravity: Int = Gravity.CENTER_HORIZONTAL): TextView {
        val v = TextView(this)
        v.text = text
        v.setTextColor(color)
        v.textSize = size
        v.gravity = gravity
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(bottomMarginDp)
        v.layoutParams = lp
        return v
    }

    
    private fun startUpdate(url: String, button: TextView) {
        if (!button.isEnabled) return
        if (url.isEmpty()) { button.text = "没有下载地址"; return }

        val ready = ApkInstaller.apkFile(this)
        if (ApkInstaller.alreadyDownloaded(this, 0)) {
            Prefs.setUpdateInProgress(this, true)
            if (ApkInstaller.install(this, ready)) return
            button.text = "请允许安装未知应用"
            return
        }

        Prefs.setUpdateInProgress(this, true)
        button.isEnabled = false
        button.text = "下载中 0%"
        Thread {
            val file = ApkInstaller.download(this, url, sha256, { source -> runOnUiThread { status.text = "下载线路：$source" } }) { percent ->
                runOnUiThread {
                    button.text = if (percent < 0) "下载中…" else if (percent < 100) "下载中 $percent%" else "正在安装…"
                }
            }
            runOnUiThread {
                if (file != null && ApkInstaller.install(this, file)) {
                    button.text = "正在安装…"
                } else {
                    button.isEnabled = true
                    button.text = "下载失败，重试"
                    status.text = (ApkInstaller.lastError.ifEmpty { "下载失败，请检查网络" }) + "。也可以点「手动下载」到蒲公英安装。"
                }
                button.requestFocus()
            }
        }.apply { isDaemon = true }.start()
    }

    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (!force) finish() }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun solid(color: Int, radiusDp: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun gradient(from: Int, to: Int, radiusDp: Int): android.graphics.drawable.Drawable =
        android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(from, to)
        ).apply { cornerRadius = dp(radiusDp).toFloat() }
}
