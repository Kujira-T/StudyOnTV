package com.studyontv.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class OnboardingActivity : Activity() {
    private data class PermissionRow(val status: TextView, val enabled: () -> Boolean)
    private val rows = mutableListOf<PermissionRow>()
    private lateinit var modeTitle: TextView
    private lateinit var modeDescription: TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshStatus = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1000) }
    }
    private val tv get() = DeviceBridge.isTv(this)
    private val foreground get() = if (tv) 0xFFF3F4F6.toInt() else 0xFF2E3931.toInt()
    private val muted get() = if (tv) 0xFF9DA5AF.toInt() else 0xFF81907F.toInt()
    private val border get() = if (tv) 0xFF2D323B.toInt() else 0xFFE5EADF.toInt()
    private val accent get() = 0xFFF0AD64.toInt()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, stroke: Int = color, radius: Int = 18, width: Int = 1) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(width).coerceAtLeast(1), stroke)
    }
    private fun text(value: String, size: Float, color: Int = foreground, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 15f; isAllCaps = false; elevation = 0f; stateListAnimator = null
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(dp(16), 0, dp(16), 0)
        setTextColor(if (primary) 0xFF352819.toInt() else this@OnboardingActivity.foreground)
        backgroundTintList = null
        val normal = if (primary) accent else if (tv) 0xFF20242B.toInt() else 0xFFF7F9F3.toInt()
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(normal, if (tv) 0xFFFFD397.toInt() else 0xFFC3823D.toInt(), 13, 2))
            addState(intArrayOf(android.R.attr.state_pressed), shape(if (primary) 0xFFE29A4D.toInt() else normal, accent, 13, 2))
            addState(intArrayOf(), shape(normal, if (primary) normal else border, 13))
        }
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AdminAuth.configured(this)) {
            startActivity(Intent(this, ParentActivity::class.java)); finish(); return
        }
        DeviceRuntime.ensure(this)
        if (tv) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(if (tv) 20 else 28), dp(24), dp(24))
            setBackgroundColor(if (tv) 0xFF0D0F13.toInt() else 0xFFF7F9F4.toInt())
        }
        val width = minOf(resources.displayMetrics.widthPixels - dp(48), dp(960))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(content, LinearLayout.LayoutParams(width, -2))
        content.addView(text("设置使用时间管理", if (tv) 28f else 26f, bold = true))
        content.addView(text("权限按需开启，稍后随时可以调整。", 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val mode = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(12))
            background = shape(if (tv) 0xFF241E17.toInt() else 0xFFFFF2DF.toInt())
        }
        modeTitle = text("", 15f, if (tv) 0xFFF0C391.toInt() else 0xFF936D36.toInt(), true)
        modeDescription = text("", 13f, muted)
        mode.addView(modeTitle)
        mode.addView(modeDescription, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        content.addView(mode, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18); bottomMargin = dp(16) })

        val cards = listOf(
            permission("悬浮窗", "到时间后显示学习页", android.R.drawable.ic_menu_view, { GateOverlay.canDraw(this) }) {
                openSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true)
            },
            permission("使用情况访问", "更准确地计算应用使用时间", android.R.drawable.ic_menu_recent_history, { Foreground.hasUsageAccess(this) }) {
                openSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            },
            permission("无障碍服务", "帮助及时识别应用切换", android.R.drawable.ic_menu_manage, { WatchAccessibilityService.connected }) {
                openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            },
            permission("后台运行", "减少系统清理，适合电视常驻", android.R.drawable.ic_lock_idle_alarm, null) {
                openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, true)
            }
        )
        val columns = if (resources.displayMetrics.widthPixels / resources.displayMetrics.density >= 720) 2 else 1
        cards.chunked(columns).forEach { group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            group.forEachIndexed { index, card ->
                row.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply { if (index > 0) leftMargin = dp(12) })
            }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val next = button("进入家长设置", true) { complete(true) }
        actions.addView(next, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button("开始学习") { complete(false) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(12) })
        content.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setContentView(ScrollView(this).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false; addView(outer)
        })
        if (tv) next.requestFocus()
        refresh()
    }

    private fun permission(title: String, description: String, icon: Int, enabled: (() -> Boolean)?, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = shape(if (tv) 0xFF171B21.toInt() else 0xFFFFFFFF.toInt(), border)
        }
        card.addView(ImageView(this).apply {
            setImageResource(icon); setColorFilter(if (tv) accent else 0xFFB58A51.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(26), dp(26)).apply { rightMargin = dp(14) })
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(title, 16f, bold = true))
        copy.addView(text(description, 12f, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        val status = text(if (enabled == null) "按设备设置" else "", 12f, muted)
        copy.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        if (enabled != null) rows.add(PermissionRow(status, enabled))
        card.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(button("设置", action = action), LinearLayout.LayoutParams(dp(68), dp(40)).apply { leftMargin = dp(8) })
        return card
    }

    private fun openSettings(action: String, packageSpecific: Boolean = false) {
        GateService.openingSystemPage()
        val options = mutableListOf(Intent(action).apply { if (packageSpecific) data = Uri.parse("package:$packageName") })
        if (packageSpecific) options.add(Intent(action))
        options.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        options.add(Intent(Settings.ACTION_SETTINGS))
        for (intent in options) try { startActivity(intent); return } catch (_: Exception) { }
        Toast.makeText(this, "请在系统设置中调整权限", Toast.LENGTH_SHORT).show()
    }

    private fun complete(manage: Boolean) {
        Prefs.setOnboarded(this, true)
        try { GateService.start(this) } catch (_: RuntimeException) {
            Toast.makeText(this, "后台管理暂未启动，您仍可继续学习", Toast.LENGTH_SHORT).show()
        }
        startActivity(Intent(this, if (manage) ParentActivity::class.java else MainActivity::class.java))
        finish()
    }

    private fun refresh() {
        if (!::modeTitle.isInitialized) return
        rows.forEach {
            val enabled = it.enabled()
            it.status.text = if (enabled) "已开启" else "未开启"
            it.status.setTextColor(if (enabled) (if (tv) 0xFFA6C892.toInt() else 0xFF6B8D57.toInt()) else muted)
        }
        val overlay = GateOverlay.canDraw(this)
        val usage = Foreground.hasUsageAccess(this)
        val info = when {
            WatchAccessibilityService.connected -> "使用管理已就绪" to "可识别应用切换，并在到时间时显示学习页。"
            overlay && usage -> "使用管理已就绪" to "已开启悬浮窗和使用情况访问。"
            overlay -> "已启用简易管理" to "离开学习页后按亮屏时间计时。"
            usage -> "已可记录使用时间" to "开启悬浮窗后，可在到时间时显示学习页。"
            else -> "可以先开始学习" to "这些权限都可以稍后开启。"
        }
        modeTitle.text = info.first; modeDescription.text = info.second
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshStatus)
        handler.post(refreshStatus)
    }
    override fun onPause() {
        handler.removeCallbacks(refreshStatus)
        super.onPause()
    }
}
