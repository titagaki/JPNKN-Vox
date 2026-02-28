package com.github.titagaki.jpnknvox

import com.github.titagaki.jpnknvox.data.JpnknMessage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MessageManager のユニットテスト
 *
 * シングルトンのため、各テスト前に状態をリセットする。
 */
class MessageManagerTest {

    @Before
    fun setUp() {
        // MessageManager はシングルトンなので、テスト間で状態をリセット
        // リフレクションで内部フィールドをリセット
        val messageLogsField = MessageManager::class.java.getDeclaredField("_messageLogs")
        messageLogsField.isAccessible = true
        val messageLogsFlow = messageLogsField.get(MessageManager) as kotlinx.coroutines.flow.MutableStateFlow<*>
        @Suppress("UNCHECKED_CAST")
        (messageLogsFlow as kotlinx.coroutines.flow.MutableStateFlow<List<Any>>).value = emptyList()

        val systemLogsField = MessageManager::class.java.getDeclaredField("_systemLogs")
        systemLogsField.isAccessible = true
        val systemLogsFlow = systemLogsField.get(MessageManager) as kotlinx.coroutines.flow.MutableStateFlow<*>
        @Suppress("UNCHECKED_CAST")
        (systemLogsFlow as kotlinx.coroutines.flow.MutableStateFlow<List<Any>>).value = emptyList()
    }

    // ========================================
    // addMessage
    // ========================================

    @Test
    fun `addMessage - メッセージが正しく追加される`() {
        val msg = createTestMessage("1", "テスト太郎", "こんにちは")

        MessageManager.addMessage(msg)

        val logs = MessageManager.messageLogs.value
        assertEquals(1, logs.size)
        assertEquals("1", logs[0].no)
        assertEquals("テスト太郎", logs[0].name)
        assertEquals("こんにちは", logs[0].message)
    }

    @Test
    fun `addMessage - 複数メッセージが末尾に追加される（時系列順）`() {
        val msg1 = createTestMessage("1", "太郎", "1番目")
        val msg2 = createTestMessage("2", "次郎", "2番目")
        val msg3 = createTestMessage("3", "三郎", "3番目")

        MessageManager.addMessage(msg1)
        MessageManager.addMessage(msg2)
        MessageManager.addMessage(msg3)

        val logs = MessageManager.messageLogs.value
        assertEquals(3, logs.size)
        assertEquals("1", logs[0].no)
        assertEquals("2", logs[1].no)
        assertEquals("3", logs[2].no)
    }

    @Test
    fun `addMessage - 500件を超えたら古いものから削除される`() {
        // 501件追加
        for (i in 1..501) {
            val msg = createTestMessage(i.toString(), "名前$i", "本文$i")
            MessageManager.addMessage(msg)
        }

        val logs = MessageManager.messageLogs.value
        assertEquals(500, logs.size)
        // 最初のメッセージ（no=1）は削除され、no=2から始まる
        assertEquals("2", logs[0].no)
        // 最後のメッセージはno=501
        assertEquals("501", logs[499].no)
    }

    @Test
    fun `addMessage - 各ログのidはユニーク`() {
        val msg = createTestMessage("1", "名前", "本文")

        MessageManager.addMessage(msg)
        MessageManager.addMessage(msg)

        val logs = MessageManager.messageLogs.value
        assertEquals(2, logs.size)
        assertNotEquals(logs[0].id, logs[1].id)
    }

    // ========================================
    // addSystemLog
    // ========================================

    @Test
    fun `addSystemLog - システムログが追加される`() {
        MessageManager.addSystemLog("テストログ")

        val logs = MessageManager.systemLogs.value
        assertEquals(1, logs.size)
        assertTrue("タイムスタンプ付きであること", logs[0].matches(Regex("\\[\\d{2}:\\d{2}:\\d{2}] テストログ")))
    }

    @Test
    fun `addSystemLog - 複数ログが末尾に追加される`() {
        MessageManager.addSystemLog("ログ1")
        MessageManager.addSystemLog("ログ2")
        MessageManager.addSystemLog("ログ3")

        val logs = MessageManager.systemLogs.value
        assertEquals(3, logs.size)
        assertTrue(logs[0].contains("ログ1"))
        assertTrue(logs[1].contains("ログ2"))
        assertTrue(logs[2].contains("ログ3"))
    }

    @Test
    fun `addSystemLog - 500件を超えたら古いものから削除される`() {
        for (i in 1..501) {
            MessageManager.addSystemLog("ログ$i")
        }

        val logs = MessageManager.systemLogs.value
        assertEquals(500, logs.size)
        // 最初のログ（ログ1）は削除されている
        assertTrue(logs[0].contains("ログ2"))
        // 最後のログはログ501
        assertTrue(logs[499].contains("ログ501"))
    }

    @Test
    fun `addSystemLog - タイムスタンプのフォーマットが HH-mm-ss`() {
        MessageManager.addSystemLog("フォーマット確認")

        val log = MessageManager.systemLogs.value[0]
        // [HH:mm:ss] の形式で始まること
        assertTrue(
            "タイムスタンプが [HH:mm:ss] 形式であること: $log",
            log.matches(Regex("^\\[\\d{2}:\\d{2}:\\d{2}] .+"))
        )
    }

    // ========================================
    // 初期状態
    // ========================================

    @Test
    fun `初期状態 - messageLogs は空リスト`() {
        assertEquals(emptyList<Any>(), MessageManager.messageLogs.value)
    }

    @Test
    fun `初期状態 - systemLogs は空リスト`() {
        assertEquals(emptyList<Any>(), MessageManager.systemLogs.value)
    }

    // ========================================
    // ヘルパー
    // ========================================

    private fun createTestMessage(no: String, name: String, message: String): JpnknMessage {
        return JpnknMessage(
            body = "$name<>sage<>2024/01/01<>$message<>",
            no = no,
            bbsid = "test",
            threadkey = "key"
        )
    }
}

