package com.studyontv.tv

import android.content.Context
import android.webkit.WebView

object InputSupport {
    val active: Boolean get() = false
    fun open(context: Context, web: WebView, voice: Boolean) = Unit
}
