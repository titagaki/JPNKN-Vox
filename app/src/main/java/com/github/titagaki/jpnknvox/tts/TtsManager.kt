package com.github.titagaki.jpnknvox.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 音声合成（TTS）管理クラス
 *
 * - TextToSpeech の初期化と解放
 * - 読み上げキューの管理
 * - 読み上げ状態の管理
 */
class TtsManager(
    context: Context,
    private val onInitialized: () -> Unit,
    private val onError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
    }

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false
    private var isSpeaking = false

    // 読み上げキュー
    private val speechQueue = ConcurrentLinkedQueue<String>()

    /**
     * TTS が初期化済みかどうか
     */
    val isReady: Boolean
        get() = isInitialized

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.JAPANESE)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Japanese language is not supported")
                onError("日本語音声がサポートされていません")
                isInitialized = false
            } else {
                Log.d(TAG, "TTS initialized successfully")
                isInitialized = true
                setupUtteranceListener()
                onInitialized()

                // 初期化完了後、キューに溜まっているメッセージを処理
                if (speechQueue.isNotEmpty()) {
                    Log.d(TAG, "Processing queued messages (${speechQueue.size} items)")
                    processQueue()
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            onError("音声エンジンの初期化に失敗しました")
            isInitialized = false
        }
    }

    /**
     * 読み上げ完了リスナーを設定
     */
    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Speech started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Speech completed: $utteranceId")
                isSpeaking = false
                processQueue()
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Speech error: $utteranceId")
                isSpeaking = false
                processQueue()
            }
        })
    }

    /**
     * テキストを読み上げキューに追加
     *
     * @param text 読み上げるテキスト
     */
    fun enqueue(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "Empty text, skipping")
            return
        }

        speechQueue.offer(text)
        Log.d(TAG, "Enqueued: $text (Queue size: ${speechQueue.size})")

        if (!isInitialized) {
            Log.w(TAG, "TTS not initialized yet, will process queue after initialization")
            return
        }

        processQueue()
    }

    /**
     * 読み上げキューを処理
     */
    private fun processQueue() {
        if (isSpeaking) {
            Log.d(TAG, "Already speaking, waiting...")
            return
        }

        if (!isInitialized) {
            Log.w(TAG, "TTS is not initialized yet")
            return
        }

        val text = speechQueue.poll()
        if (text != null) {
            isSpeaking = true
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, System.currentTimeMillis().toString())
            Log.d(TAG, "Speaking: $text (Remaining: ${speechQueue.size})")
        } else {
            Log.d(TAG, "Speech queue is empty")
        }
    }

    /**
     * 読み上げを停止
     */
    fun stop() {
        tts?.stop()
        isSpeaking = false
        Log.d(TAG, "TTS stopped")
    }

    /**
     * キューをクリア
     */
    fun clearQueue() {
        speechQueue.clear()
        Log.d(TAG, "Queue cleared")
    }

    /**
     * リソースを解放
     */
    fun shutdown() {
        stop()
        clearQueue()
        tts?.shutdown()
        tts = null
        isInitialized = false
        Log.d(TAG, "TTS shutdown completed")
    }
}

