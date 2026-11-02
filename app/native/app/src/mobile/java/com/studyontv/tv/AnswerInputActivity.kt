package com.studyontv.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class AnswerInputActivity : Activity() {
    private lateinit var text: EditText
    private lateinit var ink: InkView
    private lateinit var status: TextView
    private var delivered = false
    private var voicePending = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val voiceTimeout = Runnable { if (voicePending && !hasWindowFocus()) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFFF8F9F6.toInt())
        }
        root.addView(TextView(this).apply { text = "填写答案"; textSize = 26f; setTextColor(0xFF303B31.toInt()) })
        text = EditText(this).apply {
            hint = "键盘、输入法手写，或语音转文字"; minLines = 3; learningField()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            if (Build.VERSION.SDK_INT >= 33) isAutoHandwritingEnabled = true
        }
        if (savedInstanceState != null) text.setText(savedInstanceState.getString("draft", ""))
        root.addView(text, LinearLayout.LayoutParams(-1, -2))
        status = TextView(this).apply { setTextColor(0xFFFFB020.toInt()); textSize = 14f }
        root.addView(status)
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "语音输入"; learningStyle(); setOnClickListener { voice() } }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "清空手写"; learningStyle(); setOnClickListener { ink.clear() } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        root.addView(TextView(this).apply { text = "手写作答（含公式、图形）"; textSize = 16f; setTextColor(0xFF303B31.toInt()) })
        ink = InkView(this)
        root.addView(ink, LinearLayout.LayoutParams(-1, dp(300)))
        root.addView(Button(this).apply {
            text = "使用此答案"
            learningStyle(true)
            setOnClickListener {
                if (this@AnswerInputActivity.text.text.isBlank() && !ink.hasInk) {
                    status.text = "请先填写或手写答案"; return@setOnClickListener
                }
                val draft = this@AnswerInputActivity.text.text.toString()
                val bitmap = ink.snapshot()
                isEnabled = false
                Thread {
                    val drawing = if (bitmap == null) "" else {
                        val bytes = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes)
                        bitmap.recycle()
                        "data:image/png;base64," + Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                    }
                    val result = JSONObject().put("text", draft).put("drawing", drawing)
                    runOnUiThread {
                        delivered = true
                        AnswerInput.complete(intent.getStringExtra("requestId") ?: "", result)
                        finish()
                    }
                }.start()
            }
        })
        root.addView(Button(this).apply { text = "取消"; learningStyle(); setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { isFillViewport = true; setBackgroundColor(0xFFF8F9F6.toInt()); addView(root) })
        if (intent.getBooleanExtra("voice", false) && savedInstanceState == null) voice()
    }

    private fun voice() {
        try {
            voicePending = true
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "请说出答案"), 42)
            handler.postDelayed(voiceTimeout, 120000)
        } catch (e: Exception) {
            voicePending = false
            status.text = "此设备没有语音识别服务，可使用输入法的语音按钮"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42) { voicePending = false; handler.removeCallbacks(voiceTimeout) }
        if (requestCode == 42 && resultCode == RESULT_OK) {
            val value = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            text.append(value)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", text.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        if (!voicePending && !isChangingConfigurations && !isFinishing) finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(voiceTimeout)
        if (!delivered && !isChangingConfigurations) AnswerInput.complete(intent.getStringExtra("requestId") ?: "", null)
        super.onDestroy()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    class InkView(context: Context) : View(context) {
        private val path = Path()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        var hasInk = false
            private set
        init { setBackgroundColor(Color.WHITE) }
        override fun onDraw(canvas: Canvas) { super.onDraw(canvas); canvas.drawPath(path, paint) }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            parent.requestDisallowInterceptTouchEvent(true)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { path.moveTo(event.x, event.y); path.lineTo(event.x + 0.1f, event.y + 0.1f); hasInk = true }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until event.historySize) path.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
                    path.lineTo(event.x, event.y)
                }
                MotionEvent.ACTION_UP -> { path.lineTo(event.x, event.y); performClick() }
            }
            invalidate(); return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
        fun clear() { path.reset(); hasInk = false; invalidate() }
        fun snapshot(): Bitmap? {
            if (!hasInk || width <= 0 || height <= 0) return null
            val scale = minOf(1f, 1600f / width)
            val bitmap = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE); canvas.scale(scale, scale); canvas.drawPath(path, paint)
            return bitmap
        }
    }
}
