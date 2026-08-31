package com.github.titagaki.jpnknvox.twitch

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TwitchEvent のユニットテスト
 *
 * 実際に IRC サーバから受け取った行と、GQL の応答を元にしている。
 */
class TwitchEventTest {

    // ========================================
    // parseLine
    // ========================================

    @Test
    fun `parseLine - タグ付きの PRIVMSG を分解できる`() {
        val line = TwitchEvent.parseLine(
            "@badge-info=;badges=;color=#B22222;display-name=kuma_xqf;emotes=;mod=0;" +
                    "room-id=545050196;subscriber=0;user-id=453253536 " +
                    ":kuma_xqf!kuma_xqf@kuma_xqf.tmi.twitch.tv PRIVMSG #kato_junichi0817 :スパ様 寝てる？"
        )

        assertNotNull(line)
        assertEquals("PRIVMSG", line!!.command)
        assertEquals("kuma_xqf", line.tags["display-name"])
        assertEquals("kuma_xqf", line.nick)
        assertEquals("kato_junichi0817", line.channel)
        assertEquals("スパ様 寝てる？", line.trailing)
    }

    @Test
    fun `parseLine - タグの無い行も分解できる`() {
        val line = TwitchEvent.parseLine(":tmi.twitch.tv 001 justinfan14220 :Welcome, GLHF!")

        assertNotNull(line)
        assertEquals("001", line!!.command)
        assertTrue(line.tags.isEmpty())
        assertEquals("tmi.twitch.tv", line.prefix)
        assertEquals("Welcome, GLHF!", line.trailing)
    }

    @Test
    fun `parseLine - prefix の無い PING を分解できる`() {
        val line = TwitchEvent.parseLine("PING :tmi.twitch.tv")

        assertNotNull(line)
        assertEquals(TwitchEvent.CMD_PING, line!!.command)
        assertEquals("", line.prefix)
        assertEquals("tmi.twitch.tv", line.trailing)
    }

    @Test
    fun `parseLine - JOIN 完了の 366 からチャンネル名を取れる`() {
        val line = TwitchEvent.parseLine(
            ":justinfan14220.tmi.twitch.tv 366 justinfan14220 #kato_junichi0817 :End of /NAMES list"
        )

        assertNotNull(line)
        assertEquals(TwitchEvent.CMD_END_OF_NAMES, line!!.command)
        assertEquals("kato_junichi0817", line.channel)
    }

    @Test
    fun `parseLine - サーバからの行では nick が空になる`() {
        val line = TwitchEvent.parseLine(":tmi.twitch.tv RECONNECT")

        assertNotNull(line)
        assertEquals(TwitchEvent.CMD_RECONNECT, line!!.command)
        assertEquals("", line.nick)
    }

    @Test
    fun `parseLine - 本文にコロンが含まれていても切り分けを間違えない`() {
        val line = TwitchEvent.parseLine(
            ":a!a@a.tmi.twitch.tv PRIVMSG #chan :17時 :30 から開始"
        )

        assertNotNull(line)
        assertEquals("chan", line!!.channel)
        assertEquals("17時 :30 から開始", line.trailing)
    }

    @Test
    fun `parseLine - 空行は null`() {
        assertNull(TwitchEvent.parseLine(""))
        assertNull(TwitchEvent.parseLine("   "))
    }

    // ========================================
    // タグのエスケープ
    // ========================================

    @Test
    fun `parseLine - タグの値のエスケープを解ける`() {
        val line = TwitchEvent.parseLine(
            "@display-name=Foo\\sBar;system-msg=a\\:b :a!a@a.tmi.twitch.tv PRIVMSG #chan :hi"
        )

        assertNotNull(line)
        assertEquals("Foo Bar", line!!.tags["display-name"])
        assertEquals("a;b", line.tags["system-msg"])
    }

    @Test
    fun `parseLine - 値が空のタグも読める`() {
        val line = TwitchEvent.parseLine("@badges=;color= :a!a@a.tmi.twitch.tv PRIVMSG #chan :hi")

        assertNotNull(line)
        assertEquals("", line!!.tags["badges"])
        assertEquals("", line.tags["color"])
    }

    // ========================================
    // parseFrame
    // ========================================

    @Test
    fun `parseFrame - 1 フレームに複数行入っていても分解できる`() {
        val lines = TwitchEvent.parseFrame(
            ":tmi.twitch.tv 001 justinfan1 :Welcome, GLHF!\r\n" +
                    ":tmi.twitch.tv 002 justinfan1 :Your host is tmi.twitch.tv\r\n"
        )

        assertEquals(2, lines.size)
        assertEquals("001", lines[0].command)
        assertEquals("002", lines[1].command)
    }

    @Test
    fun `parseFrame - 壊れた行があっても他の行は読める`() {
        val lines = TwitchEvent.parseFrame("@tags-only\r\nPING :tmi.twitch.tv")

        assertEquals(1, lines.size)
        assertEquals(TwitchEvent.CMD_PING, lines[0].command)
    }

    // ========================================
    // toComment
    // ========================================

    @Test
    fun `toComment - 表示名と本文を取り出せる`() {
        val line = TwitchEvent.parseLine(
            "@display-name=空崎ヒナたそ :kuusakihina!kuusakihina@kuusakihina.tmi.twitch.tv " +
                    "PRIVMSG #chan :こんばんは"
        )!!

        val comment = TwitchEvent.toComment(line)

        assertEquals(TwitchComment("空崎ヒナたそ", "こんばんは"), comment)
    }

    @Test
    fun `toComment - 表示名が無ければニックネームで代用する`() {
        val line = TwitchEvent.parseLine(
            "@display-name= :someone!someone@someone.tmi.twitch.tv PRIVMSG #chan :hi"
        )!!

        assertEquals("someone", TwitchEvent.toComment(line)?.name)
    }

    @Test
    fun `toComment - PRIVMSG 以外は null`() {
        val line = TwitchEvent.parseLine("PING :tmi.twitch.tv")!!

        assertNull(TwitchEvent.toComment(line))
    }

    @Test
    fun `toComment - 本文が空白だけなら null`() {
        val line = TwitchEvent.parseLine(":a!a@a.tmi.twitch.tv PRIVMSG #chan :   ")!!

        assertNull(TwitchEvent.toComment(line))
    }

    // ========================================
    // buildChannelQuery
    // ========================================

    @Test
    fun `buildChannelQuery - チャンネル名を埋め込んだクエリになる`() {
        val query = JSONObject(TwitchEvent.buildChannelQuery("shroud")).getString("query")

        assertEquals("{user(login:\"shroud\"){login displayName stream{id}}}", query)
    }

    @Test
    fun `buildChannelQuery - 引用符を含む名前でもクエリが壊れない`() {
        // 入力欄では弾いているが、組み立て側でも JSON として成立していることを確かめる
        val query = JSONObject(TwitchEvent.buildChannelQuery("a\"b")).getString("query")

        assertEquals("{user(login:\"a\\\"b\"){login displayName stream{id}}}", query)
    }

    // ========================================
    // parseChannel
    // ========================================

    @Test
    fun `parseChannel - 配信中のチャンネルを読める`() {
        val channel = TwitchEvent.parseChannel(
            """{"data":{"user":{"login":"kato_junichi0817","displayName":"加藤純一うん〇ちゃん",
               "stream":{"id":"316449372403"}}}}"""
        )

        assertNotNull(channel)
        assertEquals("kato_junichi0817", channel!!.login)
        assertEquals("加藤純一うん〇ちゃん", channel.displayName)
        assertTrue(channel.isLive)
    }

    @Test
    fun `parseChannel - 配信していない場合は stream が null になる`() {
        val channel = TwitchEvent.parseChannel(
            """{"data":{"user":{"login":"shroud","displayName":"shroud","stream":null}}}"""
        )

        assertNotNull(channel)
        assertFalse(channel!!.isLive)
    }

    @Test
    fun `parseChannel - 存在しないチャンネルは null`() {
        assertNull(TwitchEvent.parseChannel("""{"data":{"user":null}}"""))
    }

    @Test
    fun `parseChannel - 壊れた JSON でも例外を投げない`() {
        assertNull(TwitchEvent.parseChannel("not json"))
        assertNull(TwitchEvent.parseChannel(""))
    }

    @Test
    fun `parseChannel - 表示名が空なら login で代用する`() {
        val channel = TwitchEvent.parseChannel(
            """{"data":{"user":{"login":"shroud","displayName":"","stream":null}}}"""
        )

        assertEquals("shroud", channel?.displayName)
    }
}
