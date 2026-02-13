package com.github.titagaki.jpnknvox.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig

/**
 * ログメッセージをブロードキャストするユーティリティ
 *
 * サービスから MainActivity へログを送信するために使用
 */
class LogBroadcaster(private val context: Context) {

    companion object {
        private const val TAG = "LogBroadcaster"
    }

    /**
     * ログメッセージをブロードキャスト
     *
     * @param message ログメッセージ
     */
    fun broadcast(message: String) {
        val intent = Intent(AppConfig.Broadcast.ACTION_LOG_UPDATE).apply {
            putExtra(AppConfig.Broadcast.EXTRA_LOG_MESSAGE, message)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Broadcasted: $message")
    }

    /**
     * 情報ログをブロードキャスト
     */
    fun info(message: String) {
        Log.i(TAG, message)
        broadcast(message)
    }

    /**
     * エラーログをブロードキャスト
     */
    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        broadcast("エラー: $message")
    }

    /**
     * 警告ログをブロードキャスト
     */
    fun warn(message: String) {
        Log.w(TAG, message)
        broadcast("警告: $message")
    }
}

