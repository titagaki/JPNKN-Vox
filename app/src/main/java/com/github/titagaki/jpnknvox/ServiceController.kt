package com.github.titagaki.jpnknvox

import android.content.Context
import android.content.Intent
import android.util.Log
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.MessageManager

/**
 * JpnknVoxService の起動・停止を担うコントローラー
 *
 * MainViewModel からサービス制御ロジックを分離し、
 * ViewModel が UI 状態の保持のみに専念できるようにする。
 */
class ServiceController(context: Context) {

    companion object {
        private const val TAG = "ServiceController"
    }

    private val appContext: Context = context.applicationContext

    fun start(
        boardId: String,
        maxMessageLength: Int = AppConfig.Tts.DEFAULT_MAX_MESSAGE_LENGTH,
        overlayAlpha: Int = AppConfig.Overlay.DEFAULT_ALPHA,
        overlayTextSize: Int = AppConfig.Overlay.DEFAULT_TEXT_SIZE
    ) {
        val intent = Intent(appContext, JpnknVoxService::class.java).apply {
            putExtra(JpnknVoxService.EXTRA_BOARD_ID, boardId)
            putExtra(JpnknVoxService.EXTRA_MAX_MESSAGE_LENGTH, maxMessageLength)
            putExtra(JpnknVoxService.EXTRA_OVERLAY_ALPHA, overlayAlpha)
            putExtra(JpnknVoxService.EXTRA_OVERLAY_TEXT_SIZE, overlayTextSize)
        }
        appContext.startForegroundService(intent)
        MessageManager.addSystemLog("サービスを開始しました (板: $boardId)")
        Log.d(TAG, "JpnknVoxService started with board ID: $boardId, max message length: $maxMessageLength, overlay alpha: $overlayAlpha")
    }

    fun stop() {
        val intent = Intent(appContext, JpnknVoxService::class.java)
        appContext.stopService(intent)
        MessageManager.addSystemLog("サービスを停止しました")
        Log.d(TAG, "JpnknVoxService stopped")
    }

    /**
     * オーバーレイの表示/非表示をサービスに即時反映する
     *
     * @param enabled true でオーバーレイを表示、false で非表示
     */
    fun setOverlayEnabled(enabled: Boolean) {
        val intent = Intent(appContext, JpnknVoxService::class.java)
            .putExtra(JpnknVoxService.EXTRA_OVERLAY_ENABLED, enabled)
        appContext.startService(intent)
        Log.d(TAG, "Overlay enabled set to: $enabled")
    }

    /**
     * メッセージ最大文字数をサービスに即時反映する
     *
     * @param length 最大文字数
     */
    fun setMaxMessageLength(length: Int) {
        val intent = Intent(appContext, JpnknVoxService::class.java)
            .putExtra(JpnknVoxService.EXTRA_MAX_MESSAGE_LENGTH, length)
        appContext.startService(intent)
        Log.d(TAG, "Max message length set to: $length")
    }

    /**
     * オーバーレイ背景の濃さをサービスに即時反映する
     *
     * @param alpha 0〜100 の整数（%）
     */
    fun setOverlayAlpha(alpha: Int) {
        val intent = Intent(appContext, JpnknVoxService::class.java)
            .putExtra(JpnknVoxService.EXTRA_OVERLAY_ALPHA, alpha)
        appContext.startService(intent)
        Log.d(TAG, "Overlay alpha set to: $alpha")
    }

    /**
     * オーバーレイの文字サイズをサービスに即時反映する
     *
     * @param textSize sp の整数
     */
    fun setOverlayTextSize(textSize: Int) {
        val intent = Intent(appContext, JpnknVoxService::class.java)
            .putExtra(JpnknVoxService.EXTRA_OVERLAY_TEXT_SIZE, textSize)
        appContext.startService(intent)
        Log.d(TAG, "Overlay text size set to: $textSize")
    }

    /**
     * 話す速度をサービスに即時反映する
     *
     * @param rate 100 で等倍の百分率
     */
    fun setSpeechRate(rate: Int) {
        val intent = Intent(appContext, JpnknVoxService::class.java)
            .putExtra(JpnknVoxService.EXTRA_SPEECH_RATE, rate)
        appContext.startService(intent)
        Log.d(TAG, "Speech rate set to: $rate")
    }

    /**
     * 読み上げ音量をサービスに即時反映する
     *
     * @param volume 0〜100 の整数（%）
     */
    fun setSpeechVolume(volume: Int) {
        val intent = Intent(appContext, JpnknVoxService::class.java)
            .putExtra(JpnknVoxService.EXTRA_SPEECH_VOLUME, volume)
        appContext.startService(intent)
        Log.d(TAG, "Speech volume set to: $volume")
    }
}

