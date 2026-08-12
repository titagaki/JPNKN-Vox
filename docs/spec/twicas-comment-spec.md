# ツイキャス コメント取得仕様

JPNKN Vox がツイキャスのコメントを受け取るために使うエンドポイントの仕様。
2026-08-12 に実際のエンドポイントへリクエストして応答を確認した。

## 公式 API v2 を使わない理由

公式の [TwitCasting API v2](https://apiv2-doc.twitcasting.tv/#comment) にも
コメント取得 API はあるが、次の理由で採用していない。

- コメントの WebSocket が無く、`GET /movies/:movie_id/comments` のポーリングになる
- レート制限が 60 リクエスト / 60 秒。しかも Basic 認証（ClientID:ClientSecret）だと
  **アプリケーション単位**で数えられる（ドキュメント「各単位については Access Token をご覧ください」）。
  APK に鍵を 1 つ埋め込むと、全ユーザーで 1 リクエスト/秒を奪い合うことになる
- 利用者ごとに ClientID の登録を求めるのも、手放し運用が前提のアプリには重い

代わりに、ツイキャスの Web ページ自身が使っている認証不要のエンドポイントを叩く。
デスクトップアプリ unacast（`src/main/twicas/index.ts`）と同じ方式。

**留意点**: 公開ドキュメントの無いエンドポイントなので、予告なく変わる可能性がある。
壊れた場合はこの文書の 3 つのエンドポイントを確認するところから始める。

## 1. 配信状態の取得

```
GET https://twitcasting.tv/streamserver.php?target={userId}&mode=client
```

認証・特別なヘッダは不要。`{userId}` は `casting_id` 形式と `c:xxxx` 形式の両方が使える。

配信中の応答（抜粋）:

```json
{"movie":{"id":839426324,"live":true},"hls":{"host":"twitcasting.tv","proto":"https","source":true}}
```

配信していない場合は `"live":false`（直近の動画 ID は残る）。

**存在しないユーザーでも HTTP 200 が返り、本文が `{}` になる。**
`movie` キーの有無でユーザーの存在を見分ける。

## 2. コメントサーバの URL 取得

```
POST https://twitcasting.tv/eventpubsuburl.php
Content-Type: multipart/form-data

movie_id={動画 ID}
__n={現在時刻のミリ秒}
password=
```

応答:

```json
{"url":"wss://202-218-171-232.twitcasting.tv/event.pubsub/v1/streams/839426324/events?token=...&n=..."}
```

URL にはトークンが含まれ、接続のたびに取り直す必要がある。

## 3. コメントの受信（WebSocket）

2 で得た URL に接続する。イベントの配列がテキストフレームで届く。

**新着が無い間は `[]` が届き続ける**（接続維持を兼ねている）。
接続時に過去のコメントは送られてこないため、
過去ログを読み上げてしまう心配はない（新着だけが届く）。

コメント:

```json
[{"type":"comment","id":33664442674,"message":"カメラ下げてー","createdAt":1786529216000,
  "author":{"id":"kazurin","name":"かずりん","screenName":"kazurin",
            "profileImage":"https://...","grade":0},
  "numComments":2511}]
```

- `createdAt` は**ミリ秒**
- 匿名コメントには `"isAnonymous":true` が付き、`author.name` は「匿名コメント#546」のようになる
- 1 フレームに複数のイベントが入ることがある
- `numComments` は配信全体の累計コメント数であって、そのコメントの番号ではない。
  jpnkn のレス番号のつもりで表示すると誤解を招くため、アプリでは扱っていない

ギフト（unacast の型定義に基づく。実物は未確認）:

```json
[{"type":"gift","id":"...","message":"応援してます","plainMessage":"応援してます",
  "isPaidGift":false,
  "item":{"name":"コーヒー","image":"https://...","detailImage":"https://..."},
  "sender":{"id":"...","name":"送り主","screenName":"...","grade":0},
  "createdAt":1786529216000}]
```

JPNKN Vox ではギフトを「アイテム名 メッセージ」の 1 件のコメントとして読み上げる。
`comment` と `gift` 以外の種類は読み飛ばす。

## アプリでの扱い

| 段階 | 実装 |
|---|---|
| 1〜3 の通信 | `twicas/TwicasClient` |
| 応答のパース | `twicas/TwicasEvent`（純粋関数。ユニットテストあり） |
| 状態遷移 | `source/TwicasConnector` |

配信していない間は 5 秒間隔で 1 を繰り返し（`配信待ち`）、
配信が始まったら 2 → 3 と進む。切断されたら 5 秒待って 1 に戻る。
枠の終了も回線断もこの経路を通るため、扱いを分けていない。
