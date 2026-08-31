# Twitch コメント取得仕様

JPNKN Vox が Twitch のチャットを受け取るために使うエンドポイントの仕様。
2026-08-31 に実際のエンドポイントへ接続して応答を確認した。

## 公式 API（Helix）を使わない理由

公式の [Twitch API](https://dev.twitch.tv/docs/api/) にもチャット関連の API はあるが、
次の理由で採用していない。

- チャットの受信は EventSub（`channel.chat.message`）で、**ユーザーアクセストークンが必須**。
  つまり利用者ごとに Twitch アカウントでの OAuth 認可が要る
- 手放し運用が前提のアプリで、トークンの期限切れによる再認可を屋外で踏むのは重い
- Client ID の発行を利用者に求めるのも同様に重い（ツイキャスで公式 API v2 を見送ったのと同じ理由）

代わりに、Twitch が読み取り専用の匿名接続として公開している IRC を使う。
デスクトップアプリ unacast（`dank-twitch-irc` を利用）と同じ方式。

**留意点**: IRC は Twitch 自身が「レガシー」と位置づけている。
将来止まる可能性があるため、壊れた場合はこの文書の 2 つの接続先を確認するところから始める。

## 1. チャットの受信（IRC over WebSocket）

```
wss://irc-ws.chat.twitch.tv:443
```

接続したら次の 3 行を送る。認証は要らない。

```
CAP REQ :twitch.tv/tags twitch.tv/commands
NICK justinfan12345
JOIN #{チャンネル名}
```

- **`justinfan` で始まるニックネームは匿名の読み取り専用ユーザーとして扱われ、
  `PASS` を求められない。** 数字部分は乱数にして、同じ名前で複数繋がるのを避ける
- `twitch.tv/tags` を要求しないと `display-name` が付いてこない。
  ニックネーム（小文字のみ）しか得られなくなる
- チャンネル名は小文字で送る

接続直後のやり取り（実際の応答）:

```
:tmi.twitch.tv CAP * ACK :twitch.tv/tags twitch.tv/commands
:tmi.twitch.tv 001 justinfan14220 :Welcome, GLHF!
（002〜376 の定型メッセージが続く）
:justinfan14220!justinfan14220@justinfan14220.tmi.twitch.tv JOIN #kato_junichi0817
@emote-only=0;followers-only=10080;... :tmi.twitch.tv ROOMSTATE #kato_junichi0817
:justinfan14220.tmi.twitch.tv 353 justinfan14220 = #kato_junichi0817 :justinfan14220
:justinfan14220.tmi.twitch.tv 366 justinfan14220 #kato_junichi0817 :End of /NAMES list
```

**`366`（End of /NAMES list）が JOIN の完了を示す。**

コメント（`PRIVMSG`）:

```
@badge-info=;badges=;color=#B22222;display-name=kuma_xqf;emotes=;first-msg=0;
id=44418d61-...;mod=0;room-id=545050196;subscriber=0;tmi-sent-ts=1788178486479;user-id=453253536
 :kuma_xqf!kuma_xqf@kuma_xqf.tmi.twitch.tv PRIVMSG #kato_junichi0817 :スパ様 寝てる？
```

- 名前は `display-name` タグを使う。空のことがある（表示名を設定していないユーザー）ので、
  その場合は prefix のニックネームで代用する
- タグの値は IRCv3 の規則でエスケープされる（`\s` が空白、`\:` がセミコロン）
- **1 つの WebSocket フレームに複数行入ることがある。** 各行は CRLF 区切り
- 接続した時点より前のコメントは送られてこない。
  過去ログを読み上げてしまう心配はない（新着だけが届く）

その他に扱うコマンド:

| コマンド | 意味 | アプリでの扱い |
|---|---|---|
| `PING :tmi.twitch.tv` | 生存確認。数分おきに来る | `PONG :tmi.twitch.tv` を返す。返さないと切断される |
| `NOTICE` | 入れないチャンネルなどの理由 | システムログに出す |
| `RECONNECT` | サーバ側の都合で繋ぎ直してほしい | 切断と同じ扱いにして繋ぎ直す |

エモートは `emotes` タグで本文中の位置が分かるが、JPNKN Vox では本文を
そのまま読み上げる（エモート名が読み上げられる）。

### 存在しないチャンネルは黙殺される

**存在しないチャンネルに `JOIN` しても、成功も失敗も返ってこない。**
`366` も `NOTICE` も来ず、接続だけが維持される（確認済み）。

そのため IRC だけではチャンネル名の誤りを検出できない。アプリでは
`366` を一定時間待ち、来なければ「チャンネルが見つかりません」として扱う。

## 2. チャンネルの存在確認（GQL）

接続テストで名前の誤りを伝えるためだけに使う。コメントの受信には使わない。

```
POST https://gql.twitch.tv/gql
Client-Id: kimne78kx3ncx6brgo4mv6wki5h1ko
Content-Type: application/json

{"query":"{user(login:\"shroud\"){login displayName stream{id}}}"}
```

`Client-Id` は Twitch の Web ページ自身が使っている公開の値。認証は要らない。

配信中の応答:

```json
{"data":{"user":{"login":"kato_junichi0817","displayName":"加藤純一うん〇ちゃん",
                 "stream":{"id":"316449372403"}}}}
```

配信していない場合は `"stream":null`。

**存在しないチャンネルでも HTTP 200 が返り、`data.user` が `null` になる。**
`user` の有無でチャンネルの存在を見分ける。

なお **Twitch のチャットは配信していない間も動く**ため、
配信状態は読み上げの可否とは関係しない。接続テストの表示に添えるだけで、
ツイキャスのような「配信待ち」の状態は持たない。

## アプリでの扱い

| 段階 | 実装 |
|---|---|
| 1〜2 の通信 | `twitch/TwitchClient` |
| IRC の行と GQL 応答のパース | `twitch/TwitchEvent`（純粋関数。ユニットテストあり） |
| 状態遷移 | `source/TwitchConnector` |

繋いだら切れるまで受け続け、切れたら 5 秒待って繋ぎ直すだけを繰り返す。
JOIN の完了（`366`）が 10 秒待っても来なければ `エラー` にしていったん切り、
同じように繋ぎ直す（名前が合っていて通信の側で失敗しただけ、という場合に自力で戻れるようにするため）。
