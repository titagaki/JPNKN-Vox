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

    fun start(boardId: String) {
        val intent = Intent(application, JpnknVoxService::class.java).apply {
            putExtra(JpnknVoxService.EXTRA_BOARD_ID, boardId)
        }
        application.startForegroundService(intent)
        MessageManager.addSystemLog("サービスを開始しました (板: $boardId)")
        Log.d(TAG, "JpnknVoxService started with board ID: $boardId")
    }

    fun stop() {
        val intent = Intent(application, JpnknVoxService::class.java)
        application.stopService(intent)
        MessageManager.addSystemLog("サービスを停止しました")
        Log.d(TAG, "JpnknVoxService stopped")
    }
}

