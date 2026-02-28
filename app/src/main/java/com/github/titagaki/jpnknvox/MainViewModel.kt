package com.github.titagaki.jpnknvox

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.titagaki.jpnknvox.data.JpnknMessage
import com.github.titagaki.jpnknvox.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * メイン ViewModel
 *
 * サービス状態、ログメッセージ、ポスト履歴、板 ID を一元管理する
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val MAX_LOG_MESSAGES = 100
        private const val MAX_POST_HISTORY = 200
    }

    private val settingsRepository = SettingsRepository(application)

    /** サービス稼働状態 */
    val isServiceRunning = mutableStateOf(false)

    /** 板 ID */
    val boardId = mutableStateOf("mamiko")

    /** ログメッセージのリスト（新しいものが先頭） */
    val logMessages = mutableStateListOf<String>()

    /** ポスト（投稿）履歴（新しいものが先頭） */
    val postHistory = mutableStateListOf<JpnknMessage>()

    init {
        // 保存済みの板 ID を読み込み
        viewModelScope.launch {
            boardId.value = settingsRepository.boardIdFlow.first()
            Log.d(TAG, "Loaded board ID: ${boardId.value}")
        }
    }

    // ========================================
    // サービス制御
    // ========================================

    /**
     * サービスを開始
     */
    fun startService() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, JpnknVoxService::class.java).apply {
            putExtra(JpnknVoxService.EXTRA_BOARD_ID, boardId.value)
        }
        context.startForegroundService(serviceIntent)
        isServiceRunning.value = true
        addLogMessage("サービスを開始しました (板: ${boardId.value})")
        Log.d(TAG, "JpnknVoxService started with board ID: ${boardId.value}")
    }

    /**
     * サービスを停止
     */
    fun stopService() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, JpnknVoxService::class.java)
        context.stopService(serviceIntent)
        isServiceRunning.value = false
        addLogMessage("サービスを停止しました")
        Log.d(TAG, "JpnknVoxService stopped")
    }

    // ========================================
    // 板 ID
    // ========================================

    /**
     * 板 ID を更新・保存
     */
    fun updateBoardId(newBoardId: String) {
        boardId.value = newBoardId
        viewModelScope.launch {
            settingsRepository.saveBoardId(newBoardId)
            Log.d(TAG, "Saved board ID: $newBoardId")
        }
    }

    // ========================================
    // ログ
    // ========================================

    /**
     * ログメッセージを追加
     */
    fun addLogMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        logMessages.add(0, "[$timestamp] $message")
        if (logMessages.size > MAX_LOG_MESSAGES) {
            logMessages.removeAt(logMessages.size - 1)
        }
    }

    // ========================================
    // ポスト履歴
    // ========================================

    /**
     * 受信した投稿をポスト履歴に追加
     */
    fun addPost(message: JpnknMessage) {
        postHistory.add(0, message)
        if (postHistory.size > MAX_POST_HISTORY) {
            postHistory.removeAt(postHistory.size - 1)
        }
    }
}

