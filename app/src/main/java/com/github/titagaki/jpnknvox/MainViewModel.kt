package com.github.titagaki.jpnknvox

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.titagaki.jpnknvox.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * メイン ViewModel
 *
 * UI 状態（サービス稼働状態・板 ID）の保持のみを担う。
 * サービス制御は [ServiceController] に委譲。
 * メッセージ・システムログは [com.github.titagaki.jpnknvox.data.MessageManager] の StateFlow を直接 collect する。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val settingsRepository = SettingsRepository(application)
    private val serviceController = ServiceController(application)

    /** サービス稼働状態 */
    val isServiceRunning = mutableStateOf(false)

    /** 板 ID */
    val boardId = mutableStateOf("")

    /** オーバーレイ表示有効状態 */
    val isOverlayEnabled = mutableStateOf(true)

    /** メッセージ最大文字数 */
    val maxMessageLength = mutableStateOf(100)

    /** オーバーレイ背景の濃さ（0〜100 %） */
    val overlayAlpha = mutableStateOf(80)

    init {
        viewModelScope.launch {
            boardId.value = settingsRepository.boardIdFlow.first()
            isOverlayEnabled.value = settingsRepository.overlayEnabledFlow.first()
            maxMessageLength.value = settingsRepository.maxMessageLengthFlow.first()
            overlayAlpha.value = settingsRepository.overlayAlphaFlow.first()
            Log.d(TAG, "Loaded board ID: ${boardId.value}, overlay enabled: ${isOverlayEnabled.value}, max message length: ${maxMessageLength.value}, overlay alpha: ${overlayAlpha.value}")
        }
    }

    /**
     * サービスを開始
     */
    fun startService() {
        serviceController.start(boardId.value, maxMessageLength.value, overlayAlpha.value)
        isServiceRunning.value = true
    }

    /**
     * サービスを停止
     */
    fun stopService() {
        serviceController.stop()
        isServiceRunning.value = false
    }

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

    /**
     * オーバーレイ有効状態を更新・保存
     */
    fun updateOverlayEnabled(enabled: Boolean) {
        isOverlayEnabled.value = enabled
        viewModelScope.launch {
            settingsRepository.saveOverlayEnabled(enabled)
            Log.d(TAG, "Saved overlay enabled: $enabled")
        }
        // サービスが稼働中であればオーバーレイを即時制御
        serviceController.setOverlayEnabled(enabled)
    }

    /**
     * メッセージ最大文字数を更新・保存
     */
    fun updateMaxMessageLength(length: Int) {
        maxMessageLength.value = length
        viewModelScope.launch {
            settingsRepository.saveMaxMessageLength(length)
            Log.d(TAG, "Saved max message length: $length")
        }
        // サービスが稼働中であれば最大文字数を即時反映
        serviceController.setMaxMessageLength(length)
    }

    /**
     * オーバーレイ背景の濃さを更新・保存
     */
    fun updateOverlayAlpha(alpha: Int) {
        overlayAlpha.value = alpha
        viewModelScope.launch {
            settingsRepository.saveOverlayAlpha(alpha)
            Log.d(TAG, "Saved overlay alpha: $alpha")
        }
        // サービスが稼働中であれば即時反映
        serviceController.setOverlayAlpha(alpha)
    }
}
