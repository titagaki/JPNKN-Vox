# JPNKN Vox — エージェント向けプロジェクト情報

## ドキュメント

| ファイル | 内容 |
|---|---|
| `docs/SRS-jpnkn-vox.md` | ソフトウェア要件仕様書 |
| `docs/DESIGN-jpnkn-vox.md` | 詳細設計書（クラス設計・データフロー・未実装項目） |
| `docs/jpnkn-api-spec.md` | API 仕様 |
| `docs/schema-jpnkn.json` | JSON スキーマ |

## プロジェクト概要

**JPNKN Vox** は、jpnkn.com 掲示板の新着レスを MQTT で受信し、Android の TextToSpeech で読み上げるフォアグラウンドサービス型アプリ。

- **applicationId**: `com.github.titagaki.jpnknvox`
- **minSdk / targetSdk**: 31 / 36 (Android 12〜16 対応)
- **言語**: Kotlin 2.0.21
- **UI**: Jetpack Compose + Material 3
- **ビルドスクリプト**: Kotlin DSL (`build.gradle.kts`)

## ビルド・テストコマンド

```bash
# デバッグビルド
./gradlew assembleDebug

# ユニットテスト（JVM）
./gradlew testDebugUnitTest

# クリーン + テスト
./gradlew clean testDebugUnitTest
```

> Windows PowerShell では `.\gradlew.bat` を使用。  
> JAVA_HOME が未設定の場合: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

テストレポート出力先: `app/build/reports/tests/testDebugUnitTest/index.html`

## アーキテクチャ

```
app/src/main/java/com/github/titagaki/jpnknvox/
├── (ルート)     MainActivity, MainViewModel, JpnknVoxService, ServiceController
├── config/      AppConfig（接続先・通知・ブロードキャスト定数）
├── data/        JpnknMessage, MessageLog, MessageManager, SettingsRepository
├── mqtt/        MqttManager（HiveMQ クライアントのラッパー）
├── tts/         TtsManager（TextToSpeech のラッパー）
├── overlay/     OverlayManager（システムオーバーレイ UI）
└── ui/
    ├── navigation/  Screen（BottomNav 定義）
    ├── screens/     HomeScreen, LogScreen, SettingsScreen
    └── theme/       Material 3 テーマ定義
```

**各クラスの責務:**

| クラス | 責務 |
|---|---|
| `MainActivity` | 権限リクエスト・Compose エントリーポイントのみ。BroadcastReceiver は持たない |
| `MainViewModel` | UI 状態（`isServiceRunning`, `boardId`）の保持のみ |
| `ServiceController` | `JpnknVoxService` の起動・停止を担うコントローラー |
| `data/MessageManager` | Service↔UI の状態ブリッジ（`object` シングルトン, `StateFlow`） |

### 主要データフロー

```
MQTT受信
  └─ MqttManager.onMessageReceived
       └─ JpnknVoxService.onMessageReceived
            ├─ MessageManager.addMessage()   → StateFlow → HomeScreen (collectAsState)
            ├─ MessageManager.addSystemLog() → StateFlow → LogScreen  (collectAsState)
            └─ TtsManager.enqueue()          → TextToSpeech 読み上げ

サービス制御
  └─ MainViewModel.startService() / stopService()
       └─ ServiceController.start() / stop()
            └─ JpnknVoxService (startForegroundService / stopService)
```

### 主要ライブラリ

| 用途 | ライブラリ |
|---|---|
| MQTT | `com.hivemq:hivemq-mqtt-client:1.3.3` |
| HTTP | `com.squareup.okhttp3:okhttp:4.12.0` |
| 設定永続化 | `androidx.datastore:datastore-preferences:1.1.1` |
| リアクティブ | `io.reactivex.rxjava3:rxjava:3.1.8` |
| テスト | JUnit 4.13.2 + `org.json:json:20231013` + `kotlinx-coroutines-test:1.7.3` |

