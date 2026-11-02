package com.studyontv.tv

import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.EditText

fun Button.learningStyle(primary: Boolean = false) {
    val density = resources.displayMetrics.density
    backgroundTintList = null
    background = GradientDrawable().apply {
        setColor(if (primary) 0xFFDBA363.toInt() else 0xFFEDF1E7.toInt())
        cornerRadius = 14 * density
    }
    setTextColor(if (primary) 0xFFFFFFFF.toInt() else 0xFF65735D.toInt())
    textSize = 16f
    isAllCaps = false
    elevation = 0f
    setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
}

fun EditText.learningField() {
    val density = resources.displayMetrics.density
    background = GradientDrawable().apply {
        setColor(0xFFFCFDF9.toInt()); setStroke((density).toInt().coerceAtLeast(1), 0xFFE4EBDD.toInt())
        cornerRadius = 12 * density
    }
    setTextColor(0xFF303B31.toInt()); setHintTextColor(0xFF8B9684.toInt())
    textSize = 17f
    setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
}
