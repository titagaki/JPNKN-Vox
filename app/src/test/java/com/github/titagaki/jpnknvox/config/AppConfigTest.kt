package com.github.titagaki.jpnknvox.config

import org.junit.Assert.*
import org.junit.Test

/**
 * AppConfig のユニットテスト
 *
 * 設定値の定数とトピック生成ロジックを検証する。
 */
class AppConfigTest {

    // ========================================
    // Mqtt.createTopic
    // ========================================

    @Test
    fun `createTopic - 板IDからトピック文字列を生成できる`() {
        val topic = AppConfig.Mqtt.createTopic("mamiko")
        assertEquals("bbs/mamiko", topic)
    }

    @Test
    fun `createTopic - 異なる板IDでも正しく生成される`() {
        assertEquals("bbs/sumire", AppConfig.Mqtt.createTopic("sumire"))
        assertEquals("bbs/test_board", AppConfig.Mqtt.createTopic("test_board"))
    }

    @Test
    fun `createTopic - 空の板IDではプレフィックスのみ`() {
        assertEquals("bbs/", AppConfig.Mqtt.createTopic(""))
    }

    // ========================================
    // 定数値の検証
    // ========================================

    @Test
    fun `Mqtt定数 - サーバーホストが正しい`() {
        assertEquals("bbs.jpnkn.com", AppConfig.Mqtt.SERVER_HOST)
    }

    @Test
    fun `Mqtt定数 - サーバーポートが正しい`() {
        assertEquals(1883, AppConfig.Mqtt.SERVER_PORT)
    }

    @Test
    fun `Mqtt定数 - トピックプレフィックスが正しい`() {
        assertEquals("bbs/", AppConfig.Mqtt.TOPIC_PREFIX)
    }

    @Test
    fun `Mqtt定数 - デフォルトトピックがプレフィックスで始まる`() {
        assertTrue(
            "DEFAULT_TOPIC は TOPIC_PREFIX で始まるべき",
            AppConfig.Mqtt.DEFAULT_TOPIC.startsWith(AppConfig.Mqtt.TOPIC_PREFIX)
        )
    }

    @Test
    fun `Mqtt定数 - 再接続設定が妥当な範囲`() {
        assertTrue(
            "INITIAL_RETRY_DELAY_MS > 0",
            AppConfig.Mqtt.INITIAL_RETRY_DELAY_MS > 0
        )
        assertTrue(
            "MAX_RETRY_DELAY_MS > INITIAL_RETRY_DELAY_MS",
            AppConfig.Mqtt.MAX_RETRY_DELAY_MS > AppConfig.Mqtt.INITIAL_RETRY_DELAY_MS
        )
        assertTrue(
            "MAX_RETRY_ATTEMPTS > 0",
            AppConfig.Mqtt.MAX_RETRY_ATTEMPTS > 0
        )
    }

    @Test
    fun `Notification定数 - チャンネルIDが空でない`() {
        assertTrue(AppConfig.Notification.CHANNEL_ID.isNotEmpty())
    }

    @Test
    fun `Notification定数 - IDが正の値`() {
        assertTrue(AppConfig.Notification.ID > 0)
    }

    @Test
    fun `Broadcast定数 - アクション文字列が空でない`() {
        assertTrue(AppConfig.Broadcast.ACTION_LOG_UPDATE.isNotEmpty())
        assertTrue(AppConfig.Broadcast.ACTION_POST_RECEIVED.isNotEmpty())
    }
}

