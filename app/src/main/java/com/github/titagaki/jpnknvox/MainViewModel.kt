package com.github.titagaki.jpnknvox

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.MessageManager
import com.github.titagaki.jpnknvox.data.SettingsRepository
import com.github.titagaki.jpnknvox.tts.TtsManager
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

    private val _isServiceRunning = MutableStateFlow(JpnknVoxService.isRunning)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _boardId = MutableStateFlow("")
    val boardId: StateFlow<String> = _boardId.asStateFlow()

    private val _isOverlayEnabled = MutableStateFlow(true)
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    private val _maxMessageLength = MutableStateFlow(AppConfig.Tts.DEFAULT_MAX_MESSAGE_LENGTH)
    val maxMessageLength: StateFlow<Int> = _maxMessageLength.asStateFlow()

    private val _overlayAlpha = MutableStateFlow(AppConfig.Overlay.DEFAULT_ALPHA)
    val overlayAlpha: StateFlow<Int> = _overlayAlpha.asStateFlow()

    private val _overlayTextSize = MutableStateFlow(AppConfig.Overlay.DEFAULT_TEXT_SIZE)
    val overlayTextSize: StateFlow<Int> = _overlayTextSize.asStateFlow()

    private val _speechRate = MutableStateFlow(AppConfig.Tts.DEFAULT_SPEECH_RATE)
    val speechRate: StateFlow<Int> = _speechRate.asStateFlow()

    private val _speechVolume = MutableStateFlow(AppConfig.Tts.DEFAULT_VOLUME)
    val speechVolume: StateFlow<Int> = _speechVolume.asStateFlow()

    private val _autoStartOnLaunch = MutableStateFlow(false)
    val autoStartOnLaunch: StateFlow<Boolean> = _autoStartOnLaunch.asStateFlow()

    /** テスト再生用の TTS。初回のテスト再生時に生成する */
    private var previewTtsManager: TtsManager? = null

    init {
        viewModelScope.launch {
            _boardId.value = settingsRepository.boardIdFlow.first()
            _isOverlayEnabled.value = settingsRepository.overlayEnabledFlow.first()
            _maxMessageLength.value = settingsRepository.maxMessageLengthFlow.first()
            _overlayAlpha.value = settingsRepository.overlayAlphaFlow.first()
            _overlayTextSize.value = settingsRepository.overlayTextSizeFlow.first()
            _speechRate.value = settingsRepository.speechRateFlow.first()
            _speechVolume.value = settingsRepository.speechVolumeFlow.first()
            _autoStartOnLaunch.value = settingsRepository.autoStartOnLaunchFlow.first()
            Log.d(TAG, "Loaded board ID: ${_boardId.value}, overlay enabled: ${_isOverlayEnabled.value}, max message length: ${_maxMessageLength.value}, overlay alpha: ${_overlayAlpha.value}, speech rate: ${_speechRate.value}, speech volume: ${_speechVolume.value}, auto start: ${_autoStartOnLaunch.value}")

            autoStartIfNeeded()
        }
    }

    /**
     * 設定が有効なら、アプリ起動時にサービスを自動開始する
     *
     * 板 ID 未設定時と、すでに稼働中の場合は何もしない。
     */
    private fun autoStartIfNeeded() {
        if (!_autoStartOnLaunch.value) return

        if (_isServiceRunning.value || JpnknVoxService.isRunning) {
            Log.d(TAG, "Auto start skipped: service is already running")
            return
        }

        if (_boardId.value.isBlank()) {
            Log.w(TAG, "Auto start skipped: board ID is not set")
            MessageManager.addSystemLog("板 ID が未設定のため自動開始をスキップしました")
            return
        }

        Log.d(TAG, "Auto starting service on app launch")
        startService()
    }

    /**
     * サービスを開始
     */
    fun startService() {
        serviceController.start(
            boardId = _boardId.value,
            maxMessageLength = _maxMessageLength.value,
            overlayAlpha = _overlayAlpha.value,
            overlayTextSize = _overlayTextSize.value
        )
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

    /**
     * オーバーレイの文字サイズを更新・保存
     */
    fun updateOverlayTextSize(textSize: Int) =
        updateAndSave(_overlayTextSize, textSize, settingsRepository::saveOverlayTextSize) {
            serviceController.setOverlayTextSize(it)
        }

    /**
     * 話す速度を更新・保存
     */
    fun updateSpeechRate(rate: Int) =
        updateAndSave(_speechRate, rate, settingsRepository::saveSpeechRate) {
            serviceController.setSpeechRate(it)
        }

    /**
     * 読み上げ音量を更新・保存
     */
    fun updateSpeechVolume(volume: Int) =
        updateAndSave(_speechVolume, volume, settingsRepository::saveSpeechVolume) {
            serviceController.setSpeechVolume(it)
        }

    /**
     * アプリ起動時の自動開始を更新・保存
     */
    fun updateAutoStartOnLaunch(enabled: Boolean) =
        updateAndSave(_autoStartOnLaunch, enabled, settingsRepository::saveAutoStartOnLaunch)

    /**
     * 現在の速度・音量でテスト再生する
     *
     * サービスの TTS とは独立したインスタンスを使うため、サービス停止中でも再生できる。
     */
    fun playTestSpeech() {
        val manager = previewTtsManager ?: TtsManager(
            context = getApplication(),
            coroutineScope = viewModelScope,
            speechRate = _speechRate.value,
            volume = _speechVolume.value,
            onInitialized = { Log.d(TAG, "Preview TTS initialized") },
            onError = { message ->
                MessageManager.addSystemLog("TTS エラー: $message")
            }
        ).also { previewTtsManager = it }

        manager.setSpeechRate(_speechRate.value)
        manager.setVolume(_speechVolume.value)

        // 初期化前はキューに積み、初期化完了後に読み上げられる
        if (manager.isReady) {
            manager.speakNow(AppConfig.Tts.TEST_TEXT)
        } else {
            manager.enqueue(AppConfig.Tts.TEST_TEXT)
        }
    }

    override fun onCleared() {
        previewTtsManager?.shutdown()
        previewTtsManager = null
        super.onCleared()
    }

    private fun <T> updateAndSave(
        flow: MutableStateFlow<T>,
        value: T,
        save: suspend (T) -> Unit,
        applyToService: ((T) -> Unit)? = null
    ) {
        flow.value = value
        viewModelScope.launch { save(value) }
        if (_isServiceRunning.value) {
            applyToService?.invoke(value)
        }
    }
}
