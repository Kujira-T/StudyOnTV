package com.studyontv.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import android.webkit.WebView

class ParentActivity : Activity() {
    private var verified = false
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceRuntime.ensure(this)
        form(!AdminAuth.configured(this))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun form(setup: Boolean, changing: Boolean = false) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(40), dp(24), dp(32))
            setBackgroundColor(0xFFF8F9F6.toInt())
        }
        fun text(value: String, size: Float) = TextView(this).apply {
            text = value; textSize = size; setTextColor(0xFF303B31.toInt()); setPadding(0, dp(10), 0, dp(10))
        }
        content.addView(text(if (setup) "设置管理密码" else "家长验证", 28f))
        content.addView(text(if (setup) "请设置 6–64 位密码。再次连接、打开管理面板和 API 都需要验证。" else "请输入管理密码", 16f))
        val secret = EditText(this).apply {
            hint = if (changing) "新密码" else "管理密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(0xFF303B31.toInt()); setHintTextColor(0xFF999999.toInt())
            isSingleLine = true
            learningField()
        }
        content.addView(secret)
        val confirm = EditText(this).apply {
            hint = "再次输入密码"; inputType = secret.inputType; isSingleLine = true
            setTextColor(0xFF303B31.toInt()); setHintTextColor(0xFF999999.toInt())
            learningField()
        }
        if (setup) content.addView(confirm)
        val error = text("", 14f)
        error.setTextColor(0xFFFFB020.toInt())
        content.addView(error)
        val submit = Button(this).apply {
            text = if (setup) "保存密码" else "验证并打开连接页"
            backgroundTintList = null
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFFFB020.toInt(), 0xFFFF6A1F.toInt())).apply { cornerRadius = dp(12).toFloat() }
            setTextColor(0xFF111111.toInt())
        }
        content.addView(submit, LinearLayout.LayoutParams(-1, dp(56)))
        submit.setOnClickListener {
            val value = secret.text.toString()
            if (setup) {
                if (value.length !in 6..64 || value != confirm.text.toString()) {
                    error.text = "密码需要 6–64 位，两次输入须一致"; return@setOnClickListener
                }
                AdminAuth.setPassword(this, value)
                if (!Prefs.onboarded(this)) {
                    startActivity(Intent(this, OnboardingActivity::class.java)); finish(); return@setOnClickListener
                }
                verified = true
                connection()
            } else {
                submit.isEnabled = false
                Thread {
                    val ok = AdminAuth.verify(this, value)
                    runOnUiThread {
                        submit.isEnabled = true
                        if (ok) { verified = true; connection() }
                        else error.text = "密码错误，连续错误 5 次后需等待一分钟"
                    }
                }.start()
            }
        }
        if (setup) content.addView(text("点击标题 5 次，或连续短按遥控器 ↑ 5 次，可重新打开此入口。", 16f))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(content) })
    }

    private fun connection() {
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF8F9F6.toInt()) }
        val controls = LinearLayout(this)
        fun button(label: String, action: () -> Unit) {
            controls.addView(Button(this).apply { text = label; learningStyle(); setOnClickListener { action() } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        button("权限设置") { startActivity(Intent(this, OnboardingActivity::class.java)) }
        button("修改密码") { form(true, true) }
        button("返回答题") { startActivity(Intent(this, MainActivity::class.java)); finish() }
        content.addView(controls)
        web = WebShell(this).create(Prefs.localBase(this) + "#/pair").also {
            it.addJavascriptInterface(DeviceBridge(this, true), "SOTVNative")
            content.addView(it, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(content)
    }

    override fun onStop() {
        super.onStop()
        if (verified) {
            verified = false
            web?.destroy(); web = null
            form(false)
        }
    }
}
