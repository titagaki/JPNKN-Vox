package com.github.titagaki.jpnknvox.twicas

import org.junit.Assert.*
import org.junit.Test

/**
 * ツイキャスから届く JSON のパースのテスト
 *
 * 期待値は実際のエンドポイントの応答から取っている（docs/spec/twicas-comment-spec.md）。
 */
class TwicasEventTest {

    // ========================================
    // parseStreamServer
    // ========================================

    @Test
    fun `parseStreamServer - 配信中の応答から動画 ID と配信中を取り出す`() {
        val json = """{"movie":{"id":839426324,"live":true},"hls":{"host":"twitcasting.tv"}}"""

        val movie = TwicasEvent.parseStreamServer(json)

        assertNotNull(movie)
        assertEquals("839426324", movie!!.movieId)
        assertTrue(movie.isLive)
    }

    @Test
    fun `parseStreamServer - 配信していない場合も動画 ID は取れる`() {
        val json = """{"movie":{"id":838588284,"live":false},"hls":{"source":true}}"""

        val movie = TwicasEvent.parseStreamServer(json)

        assertNotNull(movie)
        assertEquals("838588284", movie!!.movieId)
        assertFalse(movie.isLive)
    }

    @Test
    fun `parseStreamServer - 存在しないユーザーは空の JSON が返るので null`() {
        // 存在しないユーザーでも HTTP 200 で {} が返る。ここで見分ける
        assertNull(TwicasEvent.parseStreamServer("{}"))
    }

    @Test
    fun `parseStreamServer - 壊れた JSON は null`() {
        assertNull(TwicasEvent.parseStreamServer("not json"))
        assertNull(TwicasEvent.parseStreamServer(""))
    }

    // ========================================
    // parsePubSubUrl
    // ========================================

    @Test
    fun `parsePubSubUrl - WebSocket の URL を取り出す`() {
        val json = """{"url":"wss://202-218-171-232.twitcasting.tv/event.pubsub/v1/streams/839426324/events?token=abc"}"""

        assertEquals(
            "wss://202-218-171-232.twitcasting.tv/event.pubsub/v1/streams/839426324/events?token=abc",
            TwicasEvent.parsePubSubUrl(json)
        )
    }

    @Test
    fun `parsePubSubUrl - URL が無い場合は null`() {
        assertNull(TwicasEvent.parsePubSubUrl("{}"))
        assertNull(TwicasEvent.parsePubSubUrl("""{"url":""}"""))
        assertNull(TwicasEvent.parsePubSubUrl("壊れた"))
    }

    // ========================================
    // parseComments
    // ========================================

    @Test
    fun `parseComments - コメントを名前と本文に変換する`() {
        val raw = """
            [{"type":"comment","id":33664442674,"message":"カメラ下げてー","createdAt":1786529216000,
              "author":{"id":"kazurin","name":"かずりん","screenName":"kazurin","grade":0},
              "numComments":2511}]
        """.trimIndent()

        val comments = TwicasEvent.parseComments(raw)

        assertEquals(1, comments.size)
        assertEquals("かずりん", comments[0].name)
        assertEquals("カメラ下げてー", comments[0].message)
    }

    @Test
    fun `parseComments - numComments は番号として扱わない`() {
        // numComments は配信全体の累計コメント数であって、そのコメントの番号ではない。
        // レス番号のつもりで表示すると誤解を招くので持たせていない
        val raw = """[{"type":"comment","message":"やあ","author":{"name":"太郎"},"numComments":2511}]"""

        val comment = TwicasEvent.parseComments(raw).single()

        assertEquals(TwicasComment(name = "太郎", message = "やあ"), comment)
    }

    @Test
    fun `parseComments - 新着が無いときの空配列は空のリスト`() {
        // 新着が無い間も [] が届き続ける（接続維持を兼ねている）
        assertTrue(TwicasEvent.parseComments("[]").isEmpty())
    }

    @Test
    fun `parseComments - 1 フレームに複数のコメントが入る`() {
        val raw = """
            [{"type":"comment","message":"1つめ","author":{"name":"太郎"},"numComments":1},
             {"type":"comment","message":"2つめ","author":{"name":"次郎"},"numComments":2}]
        """.trimIndent()

        val comments = TwicasEvent.parseComments(raw)

        assertEquals(2, comments.size)
        assertEquals("1つめ", comments[0].message)
        assertEquals("2つめ", comments[1].message)
    }

    @Test
    fun `parseComments - 匿名コメントも名前を取り出せる`() {
        val raw = """
            [{"type":"comment","message":"こんばんは",
              "author":{"id":"c:tw1","name":"匿名コメント#546","screenName":"c:tw1"},
              "isAnonymous":true,"numComments":381}]
        """.trimIndent()

        val comments = TwicasEvent.parseComments(raw)

        assertEquals(1, comments.size)
        assertEquals("匿名コメント#546", comments[0].name)
    }

    @Test
    fun `parseComments - ギフトはアイテム名を頭に付けて読み上げる`() {
        val raw = """
            [{"type":"gift","id":"g1","message":"応援してます","plainMessage":"応援してます",
              "item":{"name":"コーヒー","image":"https://example.com/a.png"},
              "sender":{"id":"u1","name":"送り主"}}]
        """.trimIndent()

        val comments = TwicasEvent.parseComments(raw)

        assertEquals(1, comments.size)
        assertEquals("送り主", comments[0].name)
        assertEquals("コーヒー 応援してます", comments[0].message)
    }

    @Test
    fun `parseComments - メッセージの無いギフトはアイテム名だけになる`() {
        val raw = """[{"type":"gift","item":{"name":"お茶"},"sender":{"name":"送り主"}}]"""

        val comments = TwicasEvent.parseComments(raw)

        assertEquals(1, comments.size)
        assertEquals("お茶", comments[0].message)
    }

    @Test
    fun `parseComments - 知らない種類のイベントは読み飛ばす`() {
        val raw = """
            [{"type":"unknown","foo":"bar"},
             {"type":"comment","message":"残る","author":{"name":"太郎"},"numComments":3}]
        """.trimIndent()

        val comments = TwicasEvent.parseComments(raw)

        assertEquals(1, comments.size)
        assertEquals("残る", comments[0].message)
    }

    @Test
    fun `parseComments - 本文が空のコメントは読み飛ばす`() {
        val raw = """[{"type":"comment","message":"   ","author":{"name":"太郎"},"numComments":1}]"""

        assertTrue(TwicasEvent.parseComments(raw).isEmpty())
    }

    @Test
    fun `parseComments - 壊れた JSON は空のリスト`() {
        assertTrue(TwicasEvent.parseComments("壊れた").isEmpty())
        assertTrue(TwicasEvent.parseComments("").isEmpty())
    }
}
