# JPNKN Vox

[jpnkn.com](https://bbs.jpnkn.com/) 掲示板の新着レスを Android でリアルタイムに取得・通知するアプリケーション。  
屋外配信（IRL配信）等の、端末を直接操作できない環境において、レスの音声読み上げおよび画面オーバーレイ表示を行う。

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

## インストール手順

本アプリは Google Play ストア外で配布しているため、以下の手順で手動インストールが必要。

1. **APKのダウンロード**
  - [Releases](https://github.com/titagaki/jpnkn-vox/releases) から最新の `app-release.apk` をダウンロード。
2. **不明なアプリのインストール許可**
  - ダウンロードしたファイルを開く際、ブラウザ（Chrome等）に対して「不明なアプリのインストール」の許可を求められた場合は、設定画面から **[このソースのアプリを許可]** を有効にする。
3. **Playプロテクトの警告回避**
  - 「Playプロテクトによりブロックされました」と表示された場合、**[詳細]** をタップし、**[インストールする（安全ではありません）]** を選択。
  - ※Google未登録アプリに対する定型警告。

## セットアップ

初回起動時に以下の権限設定が必要。

1. **通知の許可 (Android 13+)**
  - 起動時に表示されるダイアログで「許可」を選択（サービスの常駐に必要）。
2. **他のアプリの上に重ねて表示**
  - 設定画面の案内から Android の設定を開き、`JPNKN Vox` を選択して許可を有効にする。
3. **読み上げ設定**
  - 設定画面で対象の板 ID（例: `mamiko`）を入力。
4. **開始**
  - トップバーのスイッチを有効にすると、バックグラウンドで読み上げが開始される。

> [!TIP]
> **「制限された設定」により権限が許可できない場合**
> Android の仕様により、設定がグレーアウトする場合がある。その際は、Android 本体の [設定] > [アプリ] > [JPNKN Vox] を開き、右上のメニューから **[制限された設定を許可]** を選択した後に再度設定を行うこと。

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