# JPNKN Vox

[jpnkn.com](https://jpnkn.com) 掲示板の新着レスを Android でリアルタイムに読み上げるアプリ。  
外配信（IRL配信）中にコメントを「耳」と「目」の両方で確認できる環境を提供する。

## 特徴

- **画面消灯中も動作** — Foreground Service で常駐し、OS によるタスクキルを防止
- **MQTT リアルタイム受信** — 新着レスを即座に受信（自動再接続付き）
- **音声読み上げ** — Android 標準 TextToSpeech でハンズフリー確認
- **オーバーレイ表示** — 他アプリ使用中でも最新レスを画面に重ねて表示

## スクリーンショット

> （TODO）

## 動作要件

- Android 12 以上（API 31+）
- 日本語 TTS エンジンがインストールされていること
- 以下の権限を許可すること:
  - 通知（Android 13+ は実行時リクエスト）
  - 他のアプリの上に重ねて表示（オーバーレイ）

## セットアップ

1. アプリを起動し、権限を許可する
2. 設定画面で板 ID を入力する（例: `mamiko`, `sumire`）
3. トップバーのスイッチをオンにしてサービスを開始する

## ビルド

```bash
./gradlew assembleDebug
```

> Windows の場合は `.\gradlew.bat assembleDebug`  
> JAVA_HOME 未設定時: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

## テスト

```bash
./gradlew testDebugUnitTest
```

レポート: `app/build/reports/tests/testDebugUnitTest/index.html`

## 技術スタック

| 領域 | 採用技術 |
|---|---|
| 言語 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| MQTT | HiveMQ MQTT Client 1.3.3 |
| 設定永続化 | Jetpack DataStore |
| 音声 | Android TextToSpeech |
| オーバーレイ | WindowManager |

## ドキュメント

- [`docs/SRS-jpnkn-vox.md`](docs/SRS-jpnkn-vox.md) — ソフトウェア要件仕様書
- [`docs/DESIGN-jpnkn-vox.md`](docs/DESIGN-jpnkn-vox.md) — 詳細設計書

## ライセンス

[LICENSE](LICENSE) を参照。

