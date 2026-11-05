package com.studyontv.tv

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

/**
 * 悬浮学习页弹出时，让背后的视频 / 直播停下来（Issue #3、#4：题目出来了，视频声音还在）。
 * 悬浮窗不会让下层应用进入 onPause，所以分三步：
 * 1. 发送“媒体暂停”按键，并抢占音频焦点——大多数播放器会暂停；
 * 2. 学习页显示期间每秒检查一次，仍有声音就每 3 秒重发暂停（部分直播 / 视频应用会忽略第一次）；
 * 3. 重发两次后仍在播放，就把媒体音量静音，学习页收起时恢复原状。
 */
object MediaPause {
    private val listener = AudioManager.OnAudioFocusChangeListener { }
    private var holding = false
    private var muted = false
    private var attempts = 0
    private var lastKey = 0L

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun sendPause(audio: AudioManager) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0))
        lastKey = SystemClock.elapsedRealtime()
    }

    @Suppress("DEPRECATION")
    fun pause(context: Context) {
        val audio = audio(context) ?: return
        try {
            attempts = 0
            if (audio.isMusicActive) { sendPause(audio); attempts = 1 }
            if (!holding) holding = audio.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (e: Exception) { android.util.Log.w("StudyOnTV", "Pause media failed", e) }
    }

    /** 学习页显示期间由 GateService 每秒调用 */
    @Suppress("DEPRECATION")
    fun enforce(context: Context) {
        val audio = audio(context) ?: return
        try {
            if (!audio.isMusicActive || muted) return
            if (SystemClock.elapsedRealtime() - lastKey < 3000) return
            if (attempts < 2) { sendPause(audio); attempts++; return }
            if (Build.VERSION.SDK_INT >= 23) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            else audio.setStreamMute(AudioManager.STREAM_MUSIC, true)
            muted = true
            context.getSharedPreferences("sotv", Context.MODE_PRIVATE).edit().putBoolean("media_muted", true).apply()
            android.util.Log.i("StudyOnTV", "Background media ignored pause; muted until the learning page closes")
        } catch (e: Exception) { android.util.Log.w("StudyOnTV", "Enforce pause failed", e) }
    }

    /** 服务被系统杀掉时可能还处于静音：重新启动后先恢复音量 */
    @Suppress("DEPRECATION")
    fun recover(context: Context) {
        val prefs = context.getSharedPreferences("sotv", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("media_muted", false)) return
        try {
            val audio = audio(context) ?: return
            if (Build.VERSION.SDK_INT >= 23) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            else audio.setStreamMute(AudioManager.STREAM_MUSIC, false)
        } catch (_: Exception) { }
        prefs.edit().remove("media_muted").apply()
    }

    @Suppress("DEPRECATION")
    fun release(context: Context) {
        val audio = audio(context) ?: return
        try {
            if (muted) {
                if (Build.VERSION.SDK_INT >= 23) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                else audio.setStreamMute(AudioManager.STREAM_MUSIC, false)
                muted = false
                context.getSharedPreferences("sotv", Context.MODE_PRIVATE).edit().remove("media_muted").apply()
            }
            if (holding) { audio.abandonAudioFocus(listener); holding = false }
            attempts = 0
        } catch (_: Exception) { }
    }
}
