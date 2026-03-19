# JPNKN Vox - Claude Code ガイド

## プロジェクト概要

JPNKN掲示板（bbs.jpnkn.com）のリアルタイムコメントをMQTTで受信し、TTSで読み上げるAndroidアプリ。
IRL配信（屋外配信）での手放し運用を想定。

**パッケージ名:** `com.github.titagaki.jpnknvox`
**バージョン:** 0.1.0
**最小SDK:** Android 12 (API 31)

## 技術スタック

- **言語:** Kotlin 2.x
- **UI:** Jetpack Compose + Material 3 + Navigation Compose
- **通信:** HiveMQ MQTT Client 1.3.3 + OkHttp 4.12.0
- **音声:** Android TextToSpeech（日本語）
- **設定永続化:** Jetpack DataStore
- **バックグラウンド:** Foreground Service

## ディレクトリ構成

```
app/src/main/java/com/github/titagaki/jpnknvox/
├── MainActivity.kt          # エントリーポイント（Compose UI）
├── MainViewModel.kt         # UI状態管理
├── JpnknVoxService.kt       # フォアグラウンドサービス（メイン処理）
├── ServiceController.kt     # サービスライフサイクル制御
├── config/AppConfig.kt      # 定数・設定値（MQTTサーバー情報等）
├── data/
│   ├── JpnknMessage.kt      # MQTTペイロードのデータモデル
│   ├── MessageLog.kt        # 表示用ログエントリ
│   ├── MessageManager.kt    # Singleton StateFlow（メッセージ状態）
│   └── SettingsRepository.kt # DataStore永続化
├── mqtt/MqttManager.kt      # MQTT接続・再接続管理
├── tts/TtsManager.kt        # TTS管理・キュー制御
├── overlay/OverlayManager.kt # WindowManagerオーバーレイ
└── ui/
    ├── screens/             # Home / Log / Settings 画面
    ├── navigation/Screen.kt # ナビゲーション定義
    └── theme/               # Color / Theme / Type
```

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

出力APK名: `JPNKNVox-{debug|release}-0.1.0.apk`

> Windows では `.\gradlew.bat` を使用。JAVA_HOME 未設定の場合:
> `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

## MQTT仕様

- **サーバー:** bbs.jpnkn.com:1883
- **認証情報:** AppConfig.ktに定義
- **トピック形式:** `bbs/{boardId}`（例: `bbs/mamiko`）
- **ペイロード（JSON）:**
  ```json
  {
    "body": "名前<>メール<>日時<>本文<>",
    "no": "レス番号",
    "bbsid": "板ID",
    "threadkey": "スレッドキー"
  }
  ```
- **再接続:** 指数バックオフ（1〜60秒、最大10回）

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
- `JpnknVoxService` が `MqttManager` / `TtsManager` / `OverlayManager` を統括する
- メッセージログ・システムログともに最大500件で古いものから削除
- オーバーレイ表示は最大30文字に切り詰め

## ドキュメント

- `docs/SRS-jpnkn-vox.md` — ソフトウェア要件定義書
- `docs/DESIGN-jpnkn-vox.md` — 設計書（クラス図・状態遷移図）
- `docs/jpnkn-api-spec.md` — MQTT APIスペック
- `docs/schema-jpnkn.json` — MQTT ペイロードの JSON スキーマ
- `README.md` — ユーザー向けインストール・ビルド手順（日本語）
