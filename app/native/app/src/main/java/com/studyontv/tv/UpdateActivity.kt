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

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0A0A0C.toInt())
        root.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(120), dp(36), dp(120), dp(24))

        root.addView(label(getString(R.string.app_name) + " · TV 版", 16f, 0xFFFFB020.toInt(), 4))
        root.addView(label("发现新版本 v$latest", 30f, Color.WHITE, 8))
        root.addView(label("当前 v$current · 必须更新后才能继续使用", 16f, 0xFFA9B7CE.toInt(), 14))

        if (notes.isNotEmpty()) {
            root.addView(label("更新内容", 13f, 0xFF8E8E96.toInt(), 6, Gravity.START))

            val note = TextView(this)
            note.text = notes
            note.setTextColor(0xFFD6DEEC.toInt())
            note.textSize = 15f
            note.setLineSpacing(dp(8).toFloat(), 1.05f)
            note.setPadding(dp(28), dp(20), dp(28), dp(20))
            note.background = solid(0xFF141416.toInt(), 16)

            val scroll = ScrollView(this)
            scroll.addView(note)
            val slp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
            root.addView(scroll, slp)
        }

        val spacer = View(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, dp(22)))

        val go = TextView(this)
        go.text = if (ApkInstaller.alreadyDownloaded(this, 0)) "开始安装" else "立即更新"
        go.textSize = 20f
        go.gravity = Gravity.CENTER
        go.setTextColor(0xFF14100A.toInt())
        go.background = gradient(0xFFFFC24A.toInt(), 0xFFFF6A1F.toInt(), 30)
        go.setOnClickListener { startUpdate(url, go) }
        root.addView(go, LinearLayout.LayoutParams(dp(320), dp(58)))

        root.addView(label("下载后覆盖安装即可，题库和设置不会丢", 12f, 0xFF6E8BC4.toInt(), 10))

        setContentView(root)
        if (url.isEmpty()) go.isEnabled = false
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
            val file = ApkInstaller.download(this, url) { percent ->
                runOnUiThread {
                    button.text = if (percent in 1..99) "下载中 $percent%" else "正在安装…"
                }
            }
            runOnUiThread {
                if (file != null && ApkInstaller.install(this, file)) {
                    button.text = "正在安装…"
                } else {
                    button.isEnabled = true
                    button.text = "下载失败，重试"
                }
            }
        }.apply { isDaemon = true }.start()
    }

    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

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
