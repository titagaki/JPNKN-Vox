# 配信アプリへのコメント受け渡し仕様

**作成日**: 2026-09-13
**相手側**: [genkai-broadcaster](https://github.com/titagaki/genkai-broadcaster)（Android RTMP 配信アプリ）。
契約の正本は genkai-broadcaster の `docs/engineering/comment-overlay.md`。本書は JPNKN Vox 側の実装をまとめる。

---

## 1. 目的

genkai-broadcaster は配信中の映像にコメントを焼き込むが、コメントの取得は自分では行わない。
JPNKN Vox が取得したコメント（jpnkn / ツイキャス / Twitch）を Bound Service (AIDL) で渡し、
配信映像に載せてもらう。JPNKN Vox の読み上げ・オーバーレイはそのまま動く。

## 2. 構成

```
JPNKN Vox                                           genkai-broadcaster
JpnknVoxService ─ addMessage ─▶ MessageManager
                                  ├ newMessages (SharedFlow)   ┐
                                  ├ serviceRunning             ├▶ export/CommentExportService ◀── bind (配信中のみ)
                                  └ sourceStatuses             ┘     ICommentSource.Stub
                                                                     RemoteCallbackList<ICommentListener> ──▶ onComments / onStateChanged
```

- `export/CommentExportService`: 配信アプリが bind する Service。`MessageManager` を購読して送るだけで、取得には関与しない。
- `export/ExportState`: サービスの稼働状態と取得先の `SourceStatus` から、配信アプリへ知らせる状態を決める純粋ロジック。
- `MessageManager.newMessages`: 受信コメントを 1 件ずつ流す `SharedFlow`。`serviceRunning`: サービスの稼働状態。
- AIDL (`app/src/main/aidl/io/github/titagaki/genkaibroadcaster/comment/`) と `CommentEntry.kt` は
  genkai-broadcaster からのコピー。**変更は配信アプリ側で行い、こちらへ同じものをコピーする**。
  `buildFeatures { aidl = true }` が必要。

## 3. 契約

| 項目 | 値 |
|---|---|
| 検出用 action | `io.github.titagaki.genkaibroadcaster.comment.action.COMMENT_SOURCE`（Service の intent-filter） |
| bind の権限 | **なし**（`exported="true"` のみ）。両アプリの署名鍵が違うため `signature` は使えず、`normal` の自前権限は定義側 (JPNKN Vox) が配信アプリより後にインストールされると付与されず `SecurityException: Not allowed to bind to service` になった (2026-09-13 実機で確認)。渡すのは公開掲示板・チャットのコメントだけで、こちらへの書き込み・設定変更の口は無いので保護は不要と判断 |
| プロトコル版 | `ICommentSource.VERSION` = 1。配信アプリは一致しない版には接続しない |
| 表示名 (`getSourceName`) | `JPNKN Vox` |

### 3.1 `ICommentSource`（JPNKN Vox が実装）

| メソッド | 動作 |
|---|---|
| `getVersion()` | `VERSION` を返す |
| `getSourceName()` | `JPNKN Vox` |
| `registerListener(l)` | `RemoteCallbackList` に登録し、**現在の状態をすぐ `onStateChanged` で送る**。システムログに「配信アプリが接続しました」 |
| `unregisterListener(l)` | 登録解除。システムログに「配信アプリが切断しました」 |

### 3.2 `ICommentListener`（配信アプリが実装、JPNKN Vox が呼ぶ。すべて `oneway`）

| メソッド | 送るタイミング・内容 |
|---|---|
| `onComments(List<CommentEntry>)` | `MessageManager.addMessage` のたびに 1 件ずつ。`id` = `MessageLog.id`（UUID）、`receivedAtMillis` = `MessageLog.timestamp`、`author` = 投稿者名（空なら null）、`body` = 本文 |
| `onStateChanged(state, detail)` | `ExportState` が変わったとき（同じ状態は送らない）。登録直後にも 1 回送る |

登録前に受信したコメントは送らない（配信アプリの契約。過去分は `messageLogs` に残っているだけ）。

### 3.3 状態の決め方（`ExportState.from`）

| サービス | 取得先の集約 (`SourceStatus.aggregate`) | state | detail |
|---|---|---|---|
| 停止中 | — | `STATE_ERROR` | `JPNKN Vox が停止中` |
| 稼働中 | 取得先なし (null) | `STATE_CONNECTING` | `取得先の接続待ち` |
| 稼働中 | `ERROR` | `STATE_ERROR` | `JPNKN Vox の取得先でエラー` |
| 稼働中 | `DISCONNECTED` | `STATE_CONNECTING` | `再接続中` |
| 稼働中 | `WAITING` | `STATE_CONNECTING` | `接続待ち` |
| 稼働中 | `CONNECTED`（配信待ちを含む） | `STATE_READY` | 空 |

配信アプリは `ERROR` の `detail` をそのまま状態行に出す。配信者が対処できること（読み上げの開始）は ERROR に寄せる。

## 4. 動作上の取り決め

- bind されても `JpnknVoxService`（読み上げ）は自動では開始しない。配信者が JPNKN Vox 側で開始する。
  配信中に TTS の音声がマイクに乗るかどうかは配信者の運用（イヤホン等）に任せる。
- 配信アプリのプロセスが落ちた場合、`RemoteCallbackList` が listener を自動で外す。
- `CommentExportService` は bind されている間だけ生きる（`onCreate` で購読開始、全員が unbind したら `onDestroy`）。
  購読は `Dispatchers.Main.immediate` に寄せ、`beginBroadcast` を重ねない。
- debug ビルド同士（`.debug` サフィックス）でも同じ action 名で繋がる（名前は `applicationId` に依存しない）。

## 5. 実機確認

2026-09-13 に genkai-broadcaster v0.2.0（debug 同士）で確認済み:

- genkai-broadcaster の設定 → コメントに `JPNKN Vox` が出ること（Android 11+ の `<queries>` 越し）。
- 読み上げ停止中に配信を始めると配信アプリの状態行が `コメント: JPNKN Vox が停止中` になり、
  JPNKN Vox を開始すると `コメント: JPNKN Vox` に変わること。
- コメントが配信映像の右下（設定で右上）に出ること。投稿者名が薄い色で前置されること。

未確認:

- JPNKN Vox を強制終了 → 再起動で配信アプリ側が自動で復帰すること。
- release 同士（署名鍵が別）でも同じように繋がること（権限を使わないので debug と同じはず）。
