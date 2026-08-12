package com.github.titagaki.jpnknvox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.titagaki.jpnknvox.config.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore インスタンス
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 設定の永続化を管理するリポジトリ
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private val BOARD_ID_KEY = stringPreferencesKey("board_id")
        private val OVERLAY_ENABLED_KEY = booleanPreferencesKey("overlay_enabled")
        private val MAX_MESSAGE_LENGTH_KEY = intPreferencesKey("max_message_length")
        private val OVERLAY_ALPHA_KEY = intPreferencesKey("overlay_alpha")
        private val OVERLAY_TEXT_SIZE_KEY = intPreferencesKey("overlay_text_size")
        private val SPEECH_RATE_KEY = intPreferencesKey("speech_rate")
        private val SPEECH_VOLUME_KEY = intPreferencesKey("speech_volume")
        private val AUTO_START_ON_LAUNCH_KEY = booleanPreferencesKey("auto_start_on_launch")
    }

    /**
     * 板 ID を取得（Flow）
     */
    val boardIdFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[BOARD_ID_KEY] ?: ""
        }

    /**
     * オーバーレイ有効状態を取得（Flow）
     */
    val overlayEnabledFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[OVERLAY_ENABLED_KEY] ?: true
        }

    /**
     * メッセージ最大文字数を取得（Flow）
     */
    val maxMessageLengthFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MAX_MESSAGE_LENGTH_KEY] ?: AppConfig.Tts.DEFAULT_MAX_MESSAGE_LENGTH
        }

    /**
     * オーバーレイ濃さを取得（Flow）。0〜100 の整数（%）
     */
    val overlayAlphaFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[OVERLAY_ALPHA_KEY] ?: AppConfig.Overlay.DEFAULT_ALPHA
        }

    /**
     * オーバーレイの文字サイズを取得（Flow）。sp の整数
     */
    val overlayTextSizeFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[OVERLAY_TEXT_SIZE_KEY] ?: AppConfig.Overlay.DEFAULT_TEXT_SIZE
        }

    /**
     * 話す速度を取得（Flow）。100 で等倍の百分率
     */
    val speechRateFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SPEECH_RATE_KEY] ?: AppConfig.Tts.DEFAULT_SPEECH_RATE
        }

    /**
     * 読み上げ音量を取得（Flow）。0〜100 の整数（%）
     */
    val speechVolumeFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[SPEECH_VOLUME_KEY] ?: AppConfig.Tts.DEFAULT_VOLUME
        }

    /**
     * アプリ起動時の自動開始が有効かを取得（Flow）
     */
    val autoStartOnLaunchFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_START_ON_LAUNCH_KEY] ?: false
        }

    /**
     * 板 ID を保存
     *
     * @param boardId 板 ID
     */
    suspend fun saveBoardId(boardId: String) {
        context.dataStore.edit { preferences ->
            preferences[BOARD_ID_KEY] = boardId
        }
    }

    /**
     * オーバーレイ有効状態を保存
     *
     * @param enabled オーバーレイを有効にするか
     */
    suspend fun saveOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_ENABLED_KEY] = enabled
        }
    }

    /**
     * メッセージ最大文字数を保存
     *
     * @param length 最大文字数
     */
    suspend fun saveMaxMessageLength(length: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_MESSAGE_LENGTH_KEY] = length
        }
    }

    /**
     * オーバーレイ濃さを保存
     *
     * @param alpha 0〜100 の整数（%）
     */
    suspend fun saveOverlayAlpha(alpha: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_ALPHA_KEY] = alpha
        }
    }

    /**
     * オーバーレイの文字サイズを保存
     *
     * @param textSize sp の整数
     */
    suspend fun saveOverlayTextSize(textSize: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_TEXT_SIZE_KEY] = textSize
        }
    }

    /**
     * 話す速度を保存
     *
     * @param rate 100 で等倍の百分率
     */
    suspend fun saveSpeechRate(rate: Int) {
        context.dataStore.edit { preferences ->
            preferences[SPEECH_RATE_KEY] = rate
        }
    }

    /**
     * 読み上げ音量を保存
     *
     * @param volume 0〜100 の整数（%）
     */
    suspend fun saveSpeechVolume(volume: Int) {
        context.dataStore.edit { preferences ->
            preferences[SPEECH_VOLUME_KEY] = volume
        }
    }

    /**
     * アプリ起動時の自動開始を保存
     *
     * @param enabled 自動開始を有効にするか
     */
    suspend fun saveAutoStartOnLaunch(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_START_ON_LAUNCH_KEY] = enabled
        }
    }
}

