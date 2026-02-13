package com.github.titagaki.jpnknvox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    }

    /**
     * 板 ID を取得（Flow）
     */
    val boardIdFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[BOARD_ID_KEY] ?: AppConfig.Mqtt.DEFAULT_TOPIC.removePrefix("bbs/")
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
}

