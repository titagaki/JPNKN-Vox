# JPNKN Vox - Claude Code ガイド

## プロジェクト概要

JPNKN掲示板（bbs.jpnkn.com）とツイキャスのリアルタイムコメントを受信し、TTSで読み上げるAndroidアプリ。
取得先は複数登録でき、同時に読み上げる。IRL配信（屋外配信）での手放し運用を想定。

**パッケージ名:** `com.github.titagaki.jpnknvox`
**バージョン:** 0.2.0
**最小SDK:** Android 12 (API 31)

## 技術スタック

- **言語:** Kotlin 2.x
- **UI:** Jetpack Compose + Material 3 + Navigation Compose
- **通信:** HiveMQ MQTT Client 1.3.3 + OkHttp 4.12.0
- **音声:** Android TextToSpeech（日本語）
- **設定永続化:** Jetpack DataStore
- **バックグラウンド:** Foreground Service

ディレクトリ構成は `docs/spec/DESIGN-jpnkn-vox.md` §1.1 を参照。

## ビルド・テスト

```bash
# デバッグビルド
./gradlew assembleDebug

# リリースビルド（local.propertiesに署名設定が必要）
./gradlew assembleRelease

# ユニットテスト
./gradlew testDebugUnitTest

# クリーン + テスト
./gradlew clean testDebugUnitTest

# テストレポート
# app/build/reports/tests/testDebugUnitTest/index.html
```

出力APK名: `JPNKNVox-{debug|release}-{バージョン}.apk`
（`assembleXxx` の直後は `app-{debug|release}.apk`。`renameDebugApk` / `renameReleaseApk` を実行するとリネームされる）

バージョンを上げるときは `app/build.gradle.kts` の `appVersion` とこのファイルの記載を直す。
`versionCode` は `appVersion` から自動算出されるので触らない（0.2.0 → 200）。

> Windows では `.\gradlew.bat` を使用。JAVA_HOME 未設定の場合:
> `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

## コメント取得先の仕様

- jpnkn（MQTT）: 接続情報・ペイロード形式は `docs/spec/jpnkn-api-spec.md`
- ツイキャス: `docs/spec/twicas-comment-spec.md`（**公式 API v2 は使わない**。理由も同ファイル）
- 再接続やクライアント実装は `docs/spec/DESIGN-jpnkn-vox.md` §2.4 を参照

## 必要なパーミッション

| パーミッション | 用途 |
|---|---|
| INTERNET / ACCESS_NETWORK_STATE | MQTT通信 |
| WAKE_LOCK | スリープ中の動作維持 |
| FOREGROUND_SERVICE | バックグラウンド動作 |
| POST_NOTIFICATIONS | フォアグラウンドサービス通知（API 33+）|
| SYSTEM_ALERT_WINDOW | オーバーレイ表示 |

`POST_NOTIFICATIONS` と `SYSTEM_ALERT_WINDOW` はランタイムで取得が必要。

## アーキテクチャ上の注意点

- `MessageManager` はSingletonで `StateFlow` を公開。サービスとUIの両方から参照する
- `JpnknVoxService` が `CommentConnector`（取得先ごとに1つ）/ `TtsManager` / `OverlayManager` を統括する
- 取得先の追加・編集・削除・起動はすべて `EXTRA_SOURCES` の1経路。サービス側が差分だけ接続・切断する
- 新しい取得先の種別を足すときは `SourceType` に追加し、`CommentConnector` を実装する
- TTSのキューは全取得先で1本。どこから来たコメントも同じキューに入る
- メッセージログ・システムログともに最大500件で古いものから削除
- オーバーレイ表示は最大60文字に切り詰め（表示は2行固定で、あふれた分は末尾を省略）

## ドキュメント

| 置き場所 | 内容 |
|---|---|
| `CLAUDE.md` | 毎回必ず守るルールだけ（目安100行以内） |
| `docs/spec/` | 仕様・設計書（検証済みのもの） |
| `docs/investigations/` | 未検証の調査結果。事実と仮説を明記して分ける |
| `docs/references/` | UI モックアップなどの参照資料 |
| `docs/roadmap.md` | タスクの進捗状態 |
| `.claude/rules/` | Claudeへの追加指示 |

**運用ルール**

- 新しい文書を作る前に `docs/` 配下を検索し、重複がないか確認する
- 調査結果は検証済みになった時点で `docs/spec/` へ昇格し、元の `docs/investigations/` のファイルは削除する
- タスクを進めたら `docs/roadmap.md` を更新する

**既存の文書**

- `docs/spec/SRS-jpnkn-vox.md` — ソフトウェア要件定義書
- `docs/spec/DESIGN-jpnkn-vox.md` — 設計書（ディレクトリ構成・クラス図・状態遷移図）
- `docs/spec/jpnkn-api-spec.md` — MQTT APIスペック
- `docs/spec/schema-jpnkn.json` — MQTT ペイロードの JSON スキーマ
- `docs/spec/twicas-comment-spec.md` — ツイキャスのコメント取得仕様
- `docs/references/jpnkn-vox-settings-inline.html` — 設定画面のモックアップ（取得先リスト版）
- `docs/references/jpnkn-vox-settings.html` — 設定画面のモックアップ（板 ID 1 件だった頃）
- `README.md` — ユーザー向けインストール・ビルド手順（日本語）
