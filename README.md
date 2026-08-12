# JPNKN Vox

[jpnkn.com](https://bbs.jpnkn.com/) 掲示板の新着レスと [ツイキャス](https://twitcasting.tv/) のコメントを
Android でリアルタイムに取得・通知するアプリケーション。  
屋外配信（IRL配信）等の、端末を直接操作できない環境において、コメントの音声読み上げおよび画面オーバーレイ表示を行う。

## 特徴

- **画面消灯中も動作** — Foreground Service で常駐し、OS によるタスクキルを防止
- **リアルタイム受信** — jpnkn は MQTT、ツイキャスは WebSocket で新着を即座に受信（自動再接続付き）
- **複数の取得先** — 板と配信をいくつでも登録でき、まとめて読み上げる。稼働中でも追加・削除できる
- **音声読み上げ** — Android 標準 TextToSpeech でハンズフリー確認
- **オーバーレイ表示** — 他アプリ使用中でも最新コメントを画面に重ねて表示

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
  - 設定画面上部のバナーをタップして Android の設定を開き、`JPNKN Vox` を選択して許可を有効にする。
  - 未許可の権限がある間はバナーが表示され、許可すると消える。
3. **コメント取得先の登録**
  - 設定画面の [コメント取得先] > **[コメント取得先を追加]** をタップする。
  - サービスで **jpnkn** を選ぶと板 ID、**ツイキャス** を選ぶとユーザー ID を入力する。入力欄の下に、その ID がどこを指すか（`bbs/○○` / `twitcasting.tv/○○`）が表示される。
  - 識別色を決め、**[接続をテスト]** で ID が正しいか確認してから追加する。
  - 取得先はいくつでも登録でき、すべて同時に読み上げられる。行をタップすると編集・削除できる。
  - ツイキャスは配信していない間「配信待ち」となり、配信が始まると自動で読み上げを始める。
4. **読み上げ設定**
  - [読み上げ] で話す速度・音量・最大文字数を調整できる。**[テスト再生]** で現在の設定を確認できる。
5. **開始**
  - トップバーのスイッチを有効にすると、バックグラウンドで読み上げが開始される。
  - 設定画面の [動作] > **[起動時に自動で開始]** を有効にすると、次回アプリを開いた時点で読み上げが自動で始まる。
  - タスク一覧からアプリを終了すると、読み上げも停止する。

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
| HTTP / WebSocket | OkHttp 4.12.0 |
| 設定永続化 | Jetpack DataStore |
| 音声 | Android TextToSpeech |
| オーバーレイ | WindowManager |

## ドキュメント

- [`docs/spec/SRS-jpnkn-vox.md`](docs/spec/SRS-jpnkn-vox.md) — ソフトウェア要件仕様書
- [`docs/spec/DESIGN-jpnkn-vox.md`](docs/spec/DESIGN-jpnkn-vox.md) — 詳細設計書
- [`docs/spec/twicas-comment-spec.md`](docs/spec/twicas-comment-spec.md) — ツイキャスのコメント取得仕様

## ライセンス

[LICENSE](LICENSE) を参照。