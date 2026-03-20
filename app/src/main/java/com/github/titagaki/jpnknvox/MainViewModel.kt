package com.github.titagaki.jpnknvox

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.titagaki.jpnknvox.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _boardId = MutableStateFlow("")
    val boardId: StateFlow<String> = _boardId.asStateFlow()

    private val _isOverlayEnabled = MutableStateFlow(true)
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    private val _maxMessageLength = MutableStateFlow(100)
    val maxMessageLength: StateFlow<Int> = _maxMessageLength.asStateFlow()

    private val _overlayAlpha = MutableStateFlow(80)
    val overlayAlpha: StateFlow<Int> = _overlayAlpha.asStateFlow()

    init {
        viewModelScope.launch {
            _boardId.value = settingsRepository.boardIdFlow.first()
            _isOverlayEnabled.value = settingsRepository.overlayEnabledFlow.first()
            _maxMessageLength.value = settingsRepository.maxMessageLengthFlow.first()
            _overlayAlpha.value = settingsRepository.overlayAlphaFlow.first()
            Log.d(TAG, "Loaded board ID: ${_boardId.value}, overlay enabled: ${_isOverlayEnabled.value}, max message length: ${_maxMessageLength.value}, overlay alpha: ${_overlayAlpha.value}")
        }
    }

    /**
     * サービスを開始
     */
    fun startService() {
        serviceController.start(_boardId.value, _maxMessageLength.value, _overlayAlpha.value)
        _isServiceRunning.value = true
    }

    /**
     * サービスを停止
     */
    fun stopService() {
        serviceController.stop()
        _isServiceRunning.value = false
    }

    /**
     * 板 ID を更新・保存
     */
    fun updateBoardId(newBoardId: String) =
        updateAndSave(_boardId, newBoardId, settingsRepository::saveBoardId)

    /**
     * オーバーレイ有効状態を更新・保存
     */
    fun updateOverlayEnabled(enabled: Boolean) =
        updateAndSave(_isOverlayEnabled, enabled, settingsRepository::saveOverlayEnabled) {
            serviceController.setOverlayEnabled(it)
        }

    /**
     * メッセージ最大文字数を更新・保存
     */
    fun updateMaxMessageLength(length: Int) =
        updateAndSave(_maxMessageLength, length, settingsRepository::saveMaxMessageLength) {
            serviceController.setMaxMessageLength(it)
        }

    /**
     * オーバーレイ背景の濃さを更新・保存
     */
    fun updateOverlayAlpha(alpha: Int) =
        updateAndSave(_overlayAlpha, alpha, settingsRepository::saveOverlayAlpha) {
            serviceController.setOverlayAlpha(it)
        }

    private fun <T> updateAndSave(
        flow: MutableStateFlow<T>,
        value: T,
        save: suspend (T) -> Unit,
        applyToService: ((T) -> Unit)? = null
    ) {
        flow.value = value
        viewModelScope.launch { save(value) }
        applyToService?.invoke(value)
    }
}
