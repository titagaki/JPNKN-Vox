package com.github.titagaki.jpnknvox

import android.app.Application
import android.content.Intent
import android.util.Log
import com.github.titagaki.jpnknvox.data.MessageManager

/**
 * JpnknVoxService の起動・停止を担うコントローラー
 *
 * MainViewModel からサービス制御ロジックを分離し、
 * ViewModel が UI 状態の保持のみに専念できるようにする。
 */
class ServiceController(private val application: Application) {

    companion object {
        private const val TAG = "ServiceController"
    }

    fun start(boardId: String, maxMessageLength: Int = 100) {
        val intent = Intent(application, JpnknVoxService::class.java).apply {
            putExtra(JpnknVoxService.EXTRA_BOARD_ID, boardId)
            putExtra(JpnknVoxService.EXTRA_MAX_MESSAGE_LENGTH, maxMessageLength)
        }
        application.startForegroundService(intent)
        MessageManager.addSystemLog("サービスを開始しました (板: $boardId)")
        Log.d(TAG, "JpnknVoxService started with board ID: $boardId, max message length: $maxMessageLength")
    }

    fun stop() {
        val intent = Intent(application, JpnknVoxService::class.java)
        application.stopService(intent)
        MessageManager.addSystemLog("サービスを停止しました")
        Log.d(TAG, "JpnknVoxService stopped")
    }

    /**
     * オーバーレイの表示/非表示をサービスに通知する
     *
     * @param enabled true でオーバーレイを表示、false で非表示
     */
    fun setOverlayEnabled(enabled: Boolean) {
        val intent = Intent(application, JpnknVoxService::class.java).apply {
            putExtra(JpnknVoxService.EXTRA_OVERLAY_ENABLED, enabled)
        }
        application.startService(intent)
        Log.d(TAG, "Overlay enabled set to: $enabled")
    }

    /**
     * メッセージ最大文字数をサービスに通知する
     *
     * @param length 最大文字数
     */
    fun setMaxMessageLength(length: Int) {
        val intent = Intent(application, JpnknVoxService::class.java).apply {
            putExtra(JpnknVoxService.EXTRA_MAX_MESSAGE_LENGTH, length)
        }
        application.startService(intent)
        Log.d(TAG, "Max message length set to: $length")
    }
}

