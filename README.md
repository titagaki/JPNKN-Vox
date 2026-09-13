# JPNKN Vox

[jpnkn.com](https://bbs.jpnkn.com/) 掲示板の新着レスと、[ツイキャス](https://twitcasting.tv/)・[Twitch](https://www.twitch.tv/) のコメントを
Android でリアルタイムに取得・通知するアプリケーション。  
屋外配信（IRL配信）等の、端末を直接操作できない環境において、コメントの音声読み上げおよび画面オーバーレイ表示を行う。

## 特徴

- **画面消灯中も動作** — Foreground Service で常駐し、OS によるタスクキルを防止
- **リアルタイム受信** — jpnkn は MQTT、ツイキャスと Twitch は WebSocket で新着を即座に受信（自動再接続付き）
- **複数の取得先** — 板と配信をいくつでも登録でき、まとめて読み上げる。稼働中でも追加・削除できる
- **アカウント不要** — どの取得先もログインや API キーの登録なしで使える
- **音声読み上げ** — Android 標準 TextToSpeech でハンズフリー確認
- **オーバーレイ表示** — 他アプリ使用中でも最新コメントを画面に重ねて表示
- **配信映像への表示** — 配信アプリ [genkai-broadcaster](https://github.com/titagaki/genkai-broadcaster) と組み合わせると、受信したコメントを配信映像に焼き込める

## スクリーンショット

> （TODO）

## 動作要件

- Android 12 以上（API 31+）
- 日本語 TTS エンジンがインストールされていること

## インストール手順

本アプリは Google Play ストア外で配布しているため、以下の手順で手動インストールが必要。

1. **APKのダウンロード**
  - [Releases](https://github.com/titagaki/jpnkn-vox/releases) から最新の `JPNKNVox-release-<バージョン>.apk` をダウンロード。
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
  - サービスで **jpnkn** を選ぶと板 ID、**ツイキャス** を選ぶとユーザー ID、**Twitch** を選ぶとチャンネル名を入力する。入力欄の下に、その ID がどこを指すか（`bbs/○○` / `twitcasting.tv/○○` / `twitch.tv/○○`）が表示される。
  - 識別色を決め、**[接続をテスト]** で ID が正しいか確認してから追加する。
  - 取得先はいくつでも登録でき、すべて同時に読み上げられる。行をタップすると編集・削除できる。
  - ツイキャスは配信していない間「配信待ち」となり、配信が始まると自動で読み上げを始める。
  - Twitch のチャットは配信していない間も動くため、配信の有無にかかわらず読み上げる。
4. **読み上げ設定**
  - [読み上げ] で話す速度・音量・最大文字数を調整できる。**[テスト再生]** で現在の設定を確認できる。
5. **開始**
  - トップバーのスイッチを有効にすると、バックグラウンドで読み上げが開始される。
  - 設定画面の [動作] > **[起動時に自動で開始]** を有効にすると、次回アプリを開いた時点で読み上げが自動で始まる。
  - タスク一覧からアプリを終了すると、読み上げも停止する。

> [!TIP]
> **「制限された設定」により権限が許可できない場合**
> Android の仕様により、設定がグレーアウトする場合がある。その際は、Android 本体の [設定] > [アプリ] > [JPNKN Vox] を開き、右上のメニューから **[制限された設定を許可]** を選択した後に再度設定を行うこと。

## 配信映像への表示（genkai-broadcaster との連携）

[genkai-broadcaster](https://github.com/titagaki/genkai-broadcaster) をインストールし、その設定画面の [コメント] で **JPNKN Vox** を選ぶと、
配信中に受信したコメントが配信映像の右下に表示される。JPNKN Vox 側の操作はいつも通り読み上げを開始するだけで、
配信アプリが配信中だけ JPNKN Vox に接続してコメントを受け取る。読み上げが停止中は配信アプリ側に「JPNKN Vox が停止中」と出る。

## ビルド

Android Studio でプロジェクトを開き、Run ▶ で実機にデバッグ版をインストールして起動する。

### リリース APK の作成

署名情報は `local.properties`（git 管理外）に書く。鍵ファイルはリポジトリ外に置く。

```properties
KEYSTORE_FILE=C:/Users/<name>/keys/jpnknvox.jks
KEYSTORE_PASSWORD=...
KEY_ALIAS=jpnknvox
KEY_PASSWORD=...
```

1. Build → Select Build Variant… で `app` を **release** にする
2. Build → Generate App Bundles or APKs → **Generate APKs**
3. `app/build/outputs/apk/release/app-release.apk` ができ、続けて配布用の `app/build/outputs/dist/JPNKNVox-release-<バージョン>.apk` が自動でコピーされる
4. 終わったら Build Variant を **debug** に戻す

配布は GitHub Releases にタグ `v<バージョン>` を切って `JPNKNVox-release-<バージョン>.apk` を添付する。
バージョンは `app/build.gradle.kts` 先頭の `appVersion` だけを直す（`versionCode` は自動算出）。

コマンドラインなら `.\gradlew.bat assembleRelease`（JAVA_HOME 未設定時は
`$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`）。

## テスト

Android Studio で `app/src/test` を右クリック → **Run 'Tests in ...'**。
コマンドラインなら `.\gradlew.bat testDebugUnitTest`
（レポート: `app/build/reports/tests/testDebugUnitTest/index.html`）。

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
- [`docs/spec/twitch-comment-spec.md`](docs/spec/twitch-comment-spec.md) — Twitch のコメント取得仕様

## ライセンス

[LICENSE](LICENSE) を参照。