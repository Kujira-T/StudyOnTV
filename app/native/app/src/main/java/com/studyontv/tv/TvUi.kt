package com.studyontv.tv

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText

/**
 * 原生页面（家长验证、更新、输入框）共用的样式。
 * 电视版用深色主题，并且每个可操作控件都有清晰的遥控器焦点框（以前原生按钮没有焦点态，
 * 用方向键时看不出当前选中了哪个）。
 */
object TvUi {
    fun dark(context: Context) = DeviceBridge.isTv(context)
    fun background(context: Context) = if (dark(context)) 0xFF121212.toInt() else 0xFFF8F9F6.toInt()
    fun foreground(context: Context) = if (dark(context)) 0xFFFFFFFF.toInt() else 0xFF303B31.toInt()
    fun muted(context: Context) = if (dark(context)) 0xFFB3B3B3.toInt() else 0xFF81907F.toInt()
    // 与学习页默认 Midnight 主题一致：中性近黑 + 绿色强调 + 白色焦点框
    const val ACCENT = 0xFF1ED760.toInt()
    const val ACCENT_INK = 0xFF04170B.toInt()
    const val FOCUS = 0xFFFFFFFF.toInt()
    fun accent(context: Context) = if (dark(context)) ACCENT else 0xFFEDAA5A.toInt()

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun shape(context: Context, color: Int, stroke: Int, width: Int, radius: Int = 14) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat(); setStroke(dp(context, width).coerceAtLeast(1), stroke)
    }

    fun button(context: Context, label: String, primary: Boolean = false, action: () -> Unit): Button = Button(context).apply {
        val isDark = dark(context)
        text = label; textSize = if (isDark) 18f else 16f; isAllCaps = false; elevation = 0f; stateListAnimator = null
        minWidth = 0; minimumWidth = 0; minHeight = dp(context, 48); minimumHeight = dp(context, 48)
        setPadding(dp(context, 20), dp(context, 10), dp(context, 20), dp(context, 10))
        isFocusable = true
        setTextColor(if (primary) (if (isDark) ACCENT_INK else 0xFFFFFFFF.toInt()) else foreground(context))
        backgroundTintList = null
        val normal = if (primary) accent(context) else if (isDark) 0xFF1F1F1F.toInt() else 0xFFEDF1E7.toInt()
        val border = if (isDark) 0xFF2E2E2E.toInt() else 0xFFE5EADF.toInt()
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(context, if (primary) (if (isDark) 0xFF3BE477.toInt() else 0xFFF3BB72.toInt()) else if (isDark) 0xFF2C2C2C.toInt() else normal,
                if (isDark) FOCUS else 0xFFC3823D.toInt(), 3))
            addState(intArrayOf(android.R.attr.state_pressed), shape(context, normal, accent(context), 2))
            addState(intArrayOf(), shape(context, normal, if (primary) normal else border, 1))
        }
        onFocusChangeListener = View.OnFocusChangeListener { v, focused ->
            if (isDark) v.animate().scaleX(if (focused) 1.05f else 1f).scaleY(if (focused) 1.05f else 1f).setDuration(120).start()
        }
        setOnClickListener { action() }
    }

    fun field(edit: EditText) {
        val context = edit.context
        val isDark = dark(context)
        val fill = if (isDark) 0xFF1F1F1F.toInt() else 0xFFFCFDF9.toInt()
        val border = if (isDark) 0xFF2E2E2E.toInt() else 0xFFE4EBDD.toInt()
        edit.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(context, fill, if (isDark) FOCUS else 0xFFE5BB87.toInt(), 2, 12))
            addState(intArrayOf(), shape(context, fill, border, 1, 12))
        }
        edit.setTextColor(foreground(context)); edit.setHintTextColor(muted(context))
        edit.textSize = if (isDark) 20f else 17f
        edit.setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
    }
}
