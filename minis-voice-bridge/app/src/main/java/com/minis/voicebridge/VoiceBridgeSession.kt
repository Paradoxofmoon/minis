package com.minis.voicebridge

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Assistant session UI — appears when the user triggers the assistant.
 * Immediately starts listening, transcribes speech, and POSTs to Minis.
 */
class VoiceBridgeSession : Activity() {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private var isListening = false

    companion object {
        private const val TAG = "VoiceBridge"
        private const val MINIS_SERVER_URL = "http://127.0.0.1:18765"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Minimal UI: dark semi-transparent overlay with status text
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6000000"))
            gravity = android.view.Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "正在听..."
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(statusText)

        setContentView(layout)
        setFinishOnTouchOutside(true)

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)

        startListening()
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            statusText.text = "语音识别不可用"
            scheduleFinish(2000)
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                statusText.text = "正在听..."
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                statusText.text = "● 正在听..."
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Voice level visual feedback
                val bars = (rmsdB / 2).toInt().coerceIn(0, 10)
                statusText.text = "● ${"▌".repeat(bars)}"
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                statusText.text = "识别中..."
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "麦克风错误"
                    SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有麦克风权限"
                    SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                    SpeechRecognizer.ERROR_NO_MATCH -> "没听清，请再说一次"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                    SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到声音"
                    else -> "识别出错 ($error)"
                }
                statusText.text = msg
                Log.e(TAG, "Recognition error: $msg")
                scheduleFinish(2000)
            }

            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                
                if (text.isNotBlank()) {
                    statusText.text = "「${text}」\n发送中..."
                    sendToMinis(text)
                } else {
                    statusText.text = "没听清"
                    scheduleFinish(1500)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: ""
                if (partial.isNotBlank()) {
                    statusText.text = "「${partial}」"
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        recognizer.startListening(intent)
    }

    private fun sendToMinis(text: String) {
        thread {
            try {
                val url = URL(MINIS_SERVER_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 3000
                conn.readTimeout = 3000

                val json = """{"text": "${text.replace("\"", "\\\"")}"}"""
                OutputStreamWriter(conn.outputStream).use { it.write(json) }

                val status = conn.responseCode
                runOnUiThread {
                    if (status == 200) {
                        statusText.text = "✅ 已发给 Minis\n「${text}」"
                    } else {
                        statusText.text = "发送失败 ($status)"
                    }
                    scheduleFinish(1500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to Minis", e)
                runOnUiThread {
                    statusText.text = "无法连接 Minis\n请确认服务已启动"
                    scheduleFinish(2500)
                }
            }
        }
    }

    private fun scheduleFinish(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) finish()
        }, delayMs)
    }

    override fun onDestroy() {
        if (::recognizer.isInitialized) {
            try {
                recognizer.destroy()
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
