package com.github.titagaki.jpnknvox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
            preferences[MAX_MESSAGE_LENGTH_KEY] ?: 100
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
}

