package com.github.titagaki.jpnknvox.twitch

import org.json.JSONObject

/**
 * Twitch のチャンネルの状態
 *
 * @param login チャンネル名（URL に出る方の名前）
 * @param displayName 表示名。login と大文字小文字だけ違うことも、日本語などの別名のこともある
 * @param isLive 配信中かどうか
 */
data class TwitchChannel(
    val login: String,
    val displayName: String,
    val isLive: Boolean
)

/**
 * チャットのコメント 1 件
 *
 * Twitch のチャットにはレス番号にあたるものが無い。
 */
data class TwitchComment(
    val name: String,
    val message: String
)

/**
 * IRC の 1 行
 *
 * @param tags `@` で始まるタグ。要求しなければ空
 * @param prefix `:` で始まる送信元。無い行もある
 * @param command `PRIVMSG` や `PING`、`366` などのコマンド
 * @param params コマンドの引数。末尾の `:` 以降（trailing）も 1 要素として最後に入る
 */
data class TwitchIrcLine(
    val tags: Map<String, String>,
    val prefix: String,
    val command: String,
    val params: List<String>
) {

    /**
     * 送信元のニックネーム。`nick!user@host` の `nick` にあたる部分
     *
     * サーバ自身が送り元の行（`:tmi.twitch.tv ...`）には `!` が無い。
     * その場合は人ではないので空文字列を返す。
     */
    val nick: String
        get() = if (prefix.contains('!')) prefix.substringBefore('!') else ""

    /** 末尾の引数。`PRIVMSG` の本文や `PING` のパラメータがここに入る */
    val trailing: String
        get() = params.lastOrNull().orEmpty()

    /** 対象のチャンネル名（先頭の `#` を除く）。チャンネルを取らないコマンドでは空 */
    val channel: String
        get() = params.firstOrNull { it.startsWith("#") }?.removePrefix("#").orEmpty()
}

/**
 * Twitch から届くデータのパース
 *
 * 通信を伴わない純粋な変換だけをここに置き、ユニットテストの対象にする。
 */
object TwitchEvent {

    /** JOIN が通ったことを示す応答（RPL_ENDOFNAMES） */
    const val CMD_END_OF_NAMES = "366"

    const val CMD_PRIVMSG = "PRIVMSG"
    const val CMD_PING = "PING"
    const val CMD_NOTICE = "NOTICE"

    /** サーバから接続し直すよう促されたときのコマンド */
    const val CMD_RECONNECT = "RECONNECT"

    /**
     * WebSocket の 1 フレームを IRC の行に分割してパースする
     *
     * 1 フレームに複数行入ることがあり、各行は CRLF で区切られる。
     *
     * @return パースできた行。空行は読み飛ばす
     */
    fun parseFrame(frame: String): List<TwitchIrcLine> =
        frame.split("\r\n", "\n")
            .filter { it.isNotBlank() }
            .mapNotNull { parseLine(it) }

    /**
     * IRC の 1 行をパースする
     *
     * 形式は `@tags :prefix COMMAND param1 param2 :trailing`。
     * tags と prefix は無いことがある。
     *
     * @return パース結果。コマンドが取れない場合は null
     */
    fun parseLine(line: String): TwitchIrcLine? {
        var rest = line.trim()
        if (rest.isEmpty()) return null

        var tags: Map<String, String> = emptyMap()
        if (rest.startsWith("@")) {
            val end = rest.indexOf(' ')
            if (end < 0) return null
            tags = parseTags(rest.substring(1, end))
            rest = rest.substring(end + 1).trimStart()
        }

        var prefix = ""
        if (rest.startsWith(":")) {
            val end = rest.indexOf(' ')
            if (end < 0) return null
            prefix = rest.substring(1, end)
            rest = rest.substring(end + 1).trimStart()
        }

        // trailing は空文字列もあり得るので、先に切り離してから残りを空白で分ける
        val trailingStart = rest.indexOf(" :")
        val trailing: String?
        if (trailingStart >= 0) {
            trailing = rest.substring(trailingStart + 2)
            rest = rest.substring(0, trailingStart)
        } else {
            trailing = null
        }

        val tokens = rest.split(' ').filter { it.isNotEmpty() }
        val command = tokens.firstOrNull() ?: return null

        val params = tokens.drop(1) + listOfNotNull(trailing)
        return TwitchIrcLine(tags = tags, prefix = prefix, command = command, params = params)
    }

    /**
     * タグ部分（`key=value;key2=value2`）をパースする
     *
     * 値の中の空白やセミコロンはエスケープされて届くので、元に戻す。
     */
    private fun parseTags(raw: String): Map<String, String> =
        raw.split(';')
            .filter { it.isNotEmpty() }
            .associate { entry ->
                val key = entry.substringBefore('=')
                val value = entry.substringAfter('=', "")
                key to unescapeTagValue(value)
            }

    /**
     * タグの値のエスケープを解く
     *
     * IRCv3 の規則で `\s` が空白、`\:` がセミコロンを表す。
     * 表示名に空白は入らないが、将来ほかのタグを使うときに効いてくる。
     */
    private fun unescapeTagValue(value: String): String {
        if (!value.contains('\\')) return value

        val result = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c != '\\' || i == value.lastIndex) {
                // 末尾の単独の `\` は規則上は無効。落として扱う
                if (c != '\\') result.append(c)
                i++
                continue
            }
            when (value[i + 1]) {
                's' -> result.append(' ')
                ':' -> result.append(';')
                'r' -> result.append('\r')
                'n' -> result.append('\n')
                '\\' -> result.append('\\')
                else -> result.append(value[i + 1])
            }
            i += 2
        }
        return result.toString()
    }

    /**
     * `PRIVMSG` の行を読み上げ対象のコメントに変換する
     *
     * 名前は `display-name` タグを使う。タグが空の場合（表示名を設定していない、
     * または tags を要求できていない場合）はニックネームで代用する。
     *
     * @return コメント。`PRIVMSG` でない、または本文が空の場合は null
     */
    fun toComment(line: TwitchIrcLine): TwitchComment? {
        if (line.command != CMD_PRIVMSG) return null

        val message = line.trailing.trim()
        if (message.isEmpty()) return null

        return TwitchComment(
            name = line.tags["display-name"]?.trim().orEmpty().ifEmpty { line.nick },
            message = message
        )
    }

    /**
     * チャンネルの存在と配信状態を問い合わせる GQL のクエリを組み立てる
     */
    fun buildChannelQuery(login: String): String {
        val body = JSONObject().apply {
            put("query", "{user(login:${JSONObject.quote(login)}){login displayName stream{id}}}")
        }
        return body.toString()
    }

    /**
     * GQL の応答をパースする
     *
     * チャンネルが存在しない場合、応答は 200 だが `data.user` が null になる。
     *
     * @return チャンネルの状態。存在しない・パースできない場合は null
     */
    fun parseChannel(json: String): TwitchChannel? {
        return try {
            val user = JSONObject(json).optJSONObject("data")?.optJSONObject("user") ?: return null
            val login = user.optString("login")
            if (login.isBlank()) return null

            TwitchChannel(
                login = login,
                displayName = user.optString("displayName").ifBlank { login },
                // 配信中でなければ stream ごと null になる
                isLive = user.optJSONObject("stream") != null
            )
        } catch (_: Exception) {
            null
        }
    }
}
