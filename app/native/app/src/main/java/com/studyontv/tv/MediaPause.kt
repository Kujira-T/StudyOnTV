package com.studyontv.tv

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * 悬浮学习页弹出时，暂停背后正在播放的视频 / 音乐（Issue #3：题目出来了，后面视频还在播放）。
 * 悬浮窗不会让下层应用进入 onPause，所以要主动：
 * 1. 发送“媒体暂停”按键——几乎所有播放器（含电视直播、爱奇艺等）都会响应；
 * 2. 抢占音频焦点——遵守音频焦点规范的播放器会自动暂停，学习页收起后再释放。
 */
object MediaPause {
    private val listener = AudioManager.OnAudioFocusChangeListener { }
    private var holding = false

    @Suppress("DEPRECATION")
    fun pause(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (audio.isMusicActive) {
                val now = SystemClock.uptimeMillis()
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
                audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
            }
            if (!holding) holding = audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) { android.util.Log.w("StudyOnTV", "Pause media failed", e) }
    }

    @Suppress("DEPRECATION")
    fun release(context: Context) {
        if (!holding) return
        holding = false
        try { (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocus(listener) } catch (_: Exception) { }
    }
}
