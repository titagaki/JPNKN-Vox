# 詳細設計書：JPNKN Vox for Android

**バージョン**: 1.5
**作成日**: 2026-02-28
**最終更新**: 2026-08-12
**対応 SRS**: `docs/spec/SRS-jpnkn-vox.md`

---

## 1. システム全体構成

### 1.1 ディレクトリ構成

```
app/src/main/java/com/github/titagaki/jpnknvox/
├── MainActivity.kt          # エントリーポイント（Compose UI）
├── MainViewModel.kt         # UI状態管理
├── JpnknVoxService.kt       # フォアグラウンドサービス（メイン処理）
├── ServiceController.kt     # サービスライフサイクル制御
├── config/AppConfig.kt      # 定数・設定値（MQTT/ツイキャス接続情報等）
├── data/
│   ├── CommentSource.kt     # コメント取得先（種別・ID・識別色）
│   ├── ReceivedComment.kt   # 取得先によらないコメントの共通形
│   ├── JpnknMessage.kt      # MQTTペイロードのデータモデル
│   ├── MessageLog.kt        # 表示用ログエントリ
│   ├── MessageManager.kt    # Singleton StateFlow（メッセージ・接続状態）
│   └── SettingsRepository.kt # DataStore永続化
├── source/
│   ├── CommentConnector.kt  # 取得先の接続の抽象（SourceStatus を含む）
│   ├── JpnknConnector.kt    # jpnkn（MqttManager を包む）
│   ├── TwicasConnector.kt   # ツイキャス（配信待ち→WebSocket の状態遷移）
│   └── SourceTester.kt      # 登録前の接続テスト
├── mqtt/MqttManager.kt      # MQTT接続・再接続管理
├── twicas/
│   ├── TwicasClient.kt      # ツイキャスのHTTP/WebSocket通信
│   └── TwicasEvent.kt       # ツイキャスの応答のパース（純粋関数）
├── tts/TtsManager.kt        # TTS管理・キュー制御
├── overlay/OverlayManager.kt # WindowManagerオーバーレイ
└── ui/
    ├── screens/             # Home / Log / Settings 画面
    ├── navigation/Screen.kt # ナビゲーション定義
    └── theme/               # Color / Theme / Type
```

### 1.2 コンポーネント図

```
┌─────────────────────────────────────────────────────┐
│  UI レイヤー（Jetpack Compose）                       │
│  MainActivity  ─  MainViewModel                     │
│  HomeScreen / LogScreen / SettingsScreen            │
└──────────────┬──────────────────────────────────────┘
               │ collectAsState
┌──────────────▼──────────────────────────────────────┐
│  状態ブリッジ（data/MessageManager）                  │
│  messageLogs / systemLogs / sourceStatuses          │
└──────────────┬──────────────────────────────────────┘
               │ addMessage / addSystemLog / updateSourceStatus
┌──────────────▼──────────────────────────────────────┐
│  サービスレイヤー（JpnknVoxService）                   │
│  CommentConnector × N  /  TtsManager                │
│  OverlayManager                                     │
│    ├ JpnknConnector  → MqttManager                  │
│    └ TwicasConnector → TwicasClient                 │
└─────────────────────────────────────────────────────┘
```

取得先は複数を同時に扱う。TTS のキューは 1 本で、
どの取得先から来たコメントも同じキューに入る。

### 1.3 データフロー

```
[MQTT ブローカー]              [ツイキャス コメントサーバ]
      │ TCP:1883                    │ WebSocket
      ▼                             ▼
MqttManager.handleMessage()   TwicasClient (TwicasEvent でパース)
      │                             │
      ▼                             ▼
JpnknConnector                TwicasConnector
      └──────────┬──────────────────┘
                 ▼
          ReceivedComment          ← 取得先によらない共通形
                 │
      ├─► MessageManager.addMessage()  → messageLogs StateFlow → HomeScreen
      ├─► MessageManager.addSystemLog() → systemLogs StateFlow → LogScreen
      ├─► OverlayManager.updateMessage() → システムオーバーレイ
      └─► TtsManager.enqueue()         → TextToSpeech → スピーカー
```

### 1.4 サービス制御フロー

```
UI スイッチ ON
  └─ MainViewModel.startService()
       └─ ServiceController.start(sources, maxMessageLength, overlayAlpha)
            └─ startForegroundService(Intent)   ← 取得先は JSON 配列で渡す
                 └─ JpnknVoxService.onCreate()
                      ├─ TtsManager 初期化
                      └─ onStartCommand → applySources()
                           （TTS 未初期化なら保留し、onTtsInitialized() 後に接続）

UI スイッチ OFF
  └─ MainViewModel.stopService()
       └─ ServiceController.stop()
            └─ stopService(Intent)
                 └─ JpnknVoxService.onDestroy()
                      ├─ OverlayManager.remove()
                      ├─ 全 CommentConnector.stop()
                      └─ TtsManager.shutdown()

取得先の追加・編集・削除（稼働中でも可）
  └─ MainViewModel.addSource / updateSource / removeSource
       └─ ServiceController.setSources(sources)
            └─ JpnknVoxService.applySources()
                 ├─ 消えた取得先・接続先が変わった取得先 → stop()
                 └─ 増えた取得先 → createConnector().start()
       ※ 識別色だけの変更では接続を張り直さない
         （CommentSource.connectsTo で判定）

設定画面からの即時反映
  ├─ MainViewModel.updateOverlayEnabled(enabled)
  │    └─ ServiceController.setOverlayEnabled(enabled)
  │         └─ JpnknVoxService.instance?.applyOverlayEnabled(enabled)
  ├─ MainViewModel.updateMaxMessageLength(length)
  │    └─ ServiceController.setMaxMessageLength(length)
  │         └─ JpnknVoxService.instance?.applyMaxMessageLength(length)
  ├─ MainViewModel.updateOverlayAlpha(alpha)
  │    └─ ServiceController.setOverlayAlpha(alpha)
  ├─ MainViewModel.updateSpeechRate(rate)
  │    └─ ServiceController.setSpeechRate(rate)
  │         └─ TtsManager.setSpeechRate(rate)
  └─ MainViewModel.updateSpeechVolume(volume)
       └─ ServiceController.setSpeechVolume(volume)
            └─ TtsManager.setVolume(volume)
            └─ JpnknVoxService.instance?.applyOverlayAlpha(alpha)
                 └─ OverlayManager.updateAlpha(alpha)
```

---

## 2. クラス設計

### 2.1 エントリーポイント層（ルートパッケージ）

#### `MainActivity`
- **責務**: 権限リクエスト・権限状態の保持・Compose UI のセットアップのみ
- **保持するもの**: `requestNotificationPermissionLauncher`、`hasNotificationPermission` / `hasOverlayPermission`（`mutableStateOf`。`onCreate` と `onResume`、権限リクエスト結果で更新し、権限画面から戻った直後の表示に反映する）
- **持たないもの**: BroadcastReceiver、ViewModel への直接参照（Compose 内で取得）
- **権限処理**:
  - `POST_NOTIFICATIONS`（Android 13+）: `ActivityResultContracts.RequestPermission` で取得
  - `SYSTEM_ALERT_WINDOW`: `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` へ誘導

#### `MainViewModel`
- **継承**: `AndroidViewModel`
- **責務**: UI 状態の保持と公開のみ
- **公開状態**:

  | プロパティ | 型 | 説明 |
  |---|---|---|
  | `isServiceRunning` | `MutableState<Boolean>` | サービス稼働状態 |
  | `sources` | `StateFlow<List<CommentSource>>` | コメント取得先の一覧 |
  | `isOverlayEnabled` | `MutableState<Boolean>` | オーバーレイ表示の有効状態 |
  | `maxMessageLength` | `MutableState<Int>` | 読み上げ最大文字数 |
  | `overlayAlpha` | `MutableState<Int>` | オーバーレイ背景の濃さ（0〜100 %） |
  | `speechRate` | `MutableState<Int>` | 話す速度（100 で等倍の百分率） |
  | `speechVolume` | `MutableState<Int>` | 読み上げ音量（0〜100 %） |
  | `autoStartOnLaunch` | `MutableState<Boolean>` | アプリ起動時の自動開始 |

- **初期化**: `init` ブロックで `SettingsRepository` の各 Flow を `first()` で取得し状態に反映したのち、`autoStartIfNeeded()` を呼ぶ
- **自動開始（`autoStartIfNeeded()`）**: `autoStartOnLaunch` が `true` で、サービスが未稼働かつ取得先が 1 件以上ある場合のみ `startService()` を呼ぶ。取得先が無い場合は `MessageManager` にその旨を記録してスキップする
- **委譲先**: `ServiceController`（起動・停止・即時反映）、`SettingsRepository`（各設定の永続化）
- **メソッド**:

  | メソッド | 説明 |
  |---|---|
  | `startService()` | `ServiceController.start(sources, maxMessageLength, overlayAlpha)` を呼び出す |
  | `stopService()` | `ServiceController.stop()` を呼び出す |
  | `addSource(type, sourceId, name, color)` | 取得先を追加して保存 + `ServiceController.setSources` |
  | `updateSource(uuid, sourceId, name, color)` | 取得先を更新して保存 + `ServiceController.setSources`（種別は変更不可） |
  | `removeSource(uuid)` | 取得先を削除して保存 + `ServiceController.setSources` |
  | `testSource(type, sourceId, onResult)` | `SourceTester` で接続テストし、結果をコールバックで返す |
  | `updateOverlayEnabled(enabled)` | 状態更新 + `SettingsRepository.saveOverlayEnabled` + `ServiceController.setOverlayEnabled` |
  | `updateMaxMessageLength(length)` | 状態更新 + `SettingsRepository.saveMaxMessageLength` + `ServiceController.setMaxMessageLength` |
  | `updateOverlayAlpha(alpha)` | 状態更新 + `SettingsRepository.saveOverlayAlpha` + `ServiceController.setOverlayAlpha` |
  | `updateSpeechRate(rate)` | 状態更新 + `SettingsRepository.saveSpeechRate` + `ServiceController.setSpeechRate` |
  | `updateSpeechVolume(volume)` | 状態更新 + `SettingsRepository.saveSpeechVolume` + `ServiceController.setSpeechVolume` |
  | `updateAutoStartOnLaunch(enabled)` | 状態更新 + `SettingsRepository.saveAutoStartOnLaunch`（サービスへの反映は不要） |
  | `playTestSpeech()` | テスト再生。サービス停止中でも鳴らせるよう、ViewModel が専用の `TtsManager`（`previewTtsManager`）を遅延生成して `AppConfig.Tts.TEST_TEXT` を読み上げる。`onCleared()` で `shutdown()` |

#### `ServiceController`
- **責務**: `JpnknVoxService` の起動・停止・即時設定反映を `applicationContext` 経由で実行（`Context` を受け取り、`MainViewModel` と `BootReceiver` の双方から利用する）
- **メソッド**:

  | メソッド | 説明 |
  |---|---|
  | `start(sources, maxMessageLength, overlayAlpha)` | `startForegroundService` でサービス起動（`EXTRA_SOURCES`・`EXTRA_MAX_MESSAGE_LENGTH`・`EXTRA_OVERLAY_ALPHA` を Intent に付与）、`MessageManager.addSystemLog` に記録 |
  | `stop()` | `stopService` でサービス停止、`MessageManager.addSystemLog` に記録 |
  | `setSources(sources: List<CommentSource>)` | `EXTRA_SOURCES`（JSON 配列）を付けて `startService`。サービス側が差分だけ接続・切断する |
  | `setOverlayEnabled(enabled: Boolean)` | `JpnknVoxService.instance?.applyOverlayEnabled(enabled)` を呼び出す |
  | `setMaxMessageLength(length: Int)` | `JpnknVoxService.instance?.applyMaxMessageLength(length)` を呼び出す |
  | `setOverlayAlpha(alpha: Int)` | `JpnknVoxService.instance?.applyOverlayAlpha(alpha)` を呼び出す |
  | `setSpeechRate(rate: Int)` | `EXTRA_SPEECH_RATE` を付けて `startService`（`TtsManager.setSpeechRate` に反映） |
  | `setSpeechVolume(volume: Int)` | `EXTRA_SPEECH_VOLUME` を付けて `startService`（`TtsManager.setVolume` に反映） |

---

### 2.2 サービス層

#### `JpnknVoxService`
- **継承**: `Service`
- **種別**: Foreground Service（`START_NOT_STICKY`）
  - `START_NOT_STICKY` を返すのは、プロセスが落ちたときに OS がサービスを作り直すと Intent が `null` になり取得先を失う（1 件も繋がないまま常駐してしまう）ため。開始・停止は明示的な操作に限る
  - `onTaskRemoved()`: タスク一覧からアプリがスワイプで終了されたら `stopSelf()` でサービスも停止する
- **通知**: `NotificationChannel(IMPORTANCE_LOW)` + `startForeground`
  - Android 14+: `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
  - 通知タップで `MainActivity` を `FLAG_ACTIVITY_SINGLE_TOP` で起動する `PendingIntent` を設定
- **companion object**:

  | 定数/プロパティ | 説明 |
  |---|---|
  | `EXTRA_SOURCES` | Intent に渡すコメント取得先（JSON 配列）のキー |
  | `EXTRA_MAX_MESSAGE_LENGTH` | Intent に渡す最大文字数のキー |
  | `EXTRA_OVERLAY_ALPHA` | Intent に渡すオーバーレイ濃さのキー |
  | `EXTRA_OVERLAY_ENABLED` | Intent に渡すオーバーレイ表示 ON/OFF のキー |
  | `EXTRA_SPEECH_RATE` | Intent に渡す話す速度のキー |
  | `EXTRA_SPEECH_VOLUME` | Intent に渡す読み上げ音量のキー |
  | `instance: JpnknVoxService?` | 稼働中インスタンス（設定の即時反映用、`private set`） |

- **フィールド**:

  | フィールド | 型 | デフォルト |
  |---|---|---|
  | `connectors` | `LinkedHashMap<String, CommentConnector>` | 空（uuid → 稼働中の接続。メインスレッドからのみ触る） |
  | `sources` | `ConcurrentHashMap<String, CommentSource>` | 空（uuid → 取得先。通信スレッドから読む） |
  | `statuses` | `ConcurrentHashMap<String, SourceStatus>` | 空（uuid → 接続状態。通信スレッドから書く） |
  | `maxMessageLength` | `Int` | `100` |
  | `overlayAlpha` | `Int` | `80` （0〜100 %） |
  | `speechRate` | `Int` | `120` （100 で等倍の百分率） |
  | `speechVolume` | `Int` | `80` （0〜100 %） |

- **ライフサイクルと処理**:

  ```
  onCreate()
    ├─ instance = this
    ├─ NotificationChannel 作成
    ├─ SettingsRepository から overlayAlpha / speechRate / speechVolume を読み込み（runBlocking）
    ├─ OverlayManager.create(overlayAlpha)
    └─ TtsManager(speechRate, speechVolume, onInitialized = ::onTtsInitialized, onError)

  onStartCommand(intent)
    ├─ EXTRA_SOURCES を applySources() に渡す（TTS 未初期化なら pendingSources に保留）
    ├─ EXTRA_MAX_MESSAGE_LENGTH を maxMessageLength にセット
    ├─ EXTRA_OVERLAY_ALPHA を overlayAlpha にセット
    ├─ EXTRA_SPEECH_RATE を speechRate にセットし TtsManager.setSpeechRate()
    ├─ EXTRA_SPEECH_VOLUME を speechVolume にセットし TtsManager.setVolume()
    ├─ EXTRA_OVERLAY_ENABLED でオーバーレイ表示を切り替え
    └─ startForeground(通知)

  onDestroy()
    ├─ instance = null
    ├─ MessageManager.addSystemLog("サービスを停止しています...")
    ├─ OverlayManager.remove()
    ├─ 全 CommentConnector.stop() → connectors/statuses/sources をクリア
    └─ TtsManager.shutdown()
  ```

- **取得先の差分反映（`applySources(newSources)`）**:
  1. `sources` を新しい一覧で置き換える
  2. TTS が未初期化なら `pendingSources` に保留して抜ける（`onTtsInitialized()` で再開）
  3. 一覧から消えた取得先と、接続先が変わった取得先（`!connectsTo`）を `stop()`
  4. まだ繋いでいない取得先を `createConnector()` して `start()`
  5. オーバーレイの状態を集約し直す

  追加・編集・削除・起動のいずれもこの 1 経路を通る。
  識別色だけの変更では接続を張り直さない。

- **即時反映メソッド**:

  | メソッド | 処理 |
  |---|---|
  | `applyOverlayEnabled(enabled: Boolean)` | `true` ならオーバーレイを `create(overlayAlpha)` で再作成し、`OverlayManager.aggregateStatus(statuses)` で現在の状態を復元、`false` なら `remove()` + `overlayManager = null` |

- **コールバック**（`CommentConnectorCallbacks`）:

  | コールバック | 処理 |
  |---|---|
  | `onTtsInitialized()` | `MessageManager.addSystemLog`、TTS「じゃぱんくん-Vox 開始しました」、保留していた `pendingSources` があれば `applySources()` |
  | `onStatusChanged(source, status)` | `statuses` を更新し `MessageManager.updateSourceStatus()`、`OverlayManager.showStatus(aggregateStatus(statuses))` |
  | `onComment(comment)` | `MessageManager.addMessage()`、`TtsManager.enqueue(ttsText)`（`maxMessageLength` 超過時は末尾を「以下略」で省略）、読み上げ開始時に `OverlayManager.updateMessage()` |
  | `onSystemLog(message)` | `MessageManager.addSystemLog()` |

  オーバーレイに渡す取得先の ID は、取得先が 2 件以上あるときだけ付ける（1 件なら自明なので出さない）。

---

### 2.3 データ層（`data/` パッケージ）

#### `CommentSource` / `SourceType`
- **種別**: `data class` と `enum class`
- **責務**: コメント取得先 1 件の設定を表す
- **`SourceType`**: `JPNKN`（jpnkn 掲示板）、`TWICAS`（ツイキャス）。
  永続化用の `id`、設定画面に出す `label` / `idFieldLabel` / `idFieldDescription`、
  取得先の場所を表す `locationHint(sourceId)`（`bbs/xxx` / `twitcasting.tv/xxx`。ID が空なら空文字列）を持つ
- **フィールド**:

  | フィールド | 型 | 説明 |
  |---|---|---|
  | `uuid` | `String` | 内部識別子。ID を編集しても同じ取得先として追える |
  | `type` | `SourceType` | 取得先の種別 |
  | `sourceId` | `String` | 板 ID（jpnkn）／ユーザー ID（ツイキャス）。一覧やログの表示にも使う |
  | `color` | `Int` | 識別色（ARGB。`AppConfig.Source.PALETTE` から選ぶ） |

- **`connectsTo(other)`**: 種別と `sourceId` だけで比較する。
  識別色を変えただけで接続を張り直さないために使う
- **永続化**: `listToJson` / `listFromJson`。壊れた要素や未知の種別は読み飛ばし、残りは保つ
- **移行（`migrateFromBoardId(boardId)`）**: 板 ID 1 つだけを持っていた頃の設定を
  jpnkn の取得先 1 件に変換する。uuid は固定値（`legacy-jpnkn`）で、
  保存される前に読み直しても別の取得先にならないようにしている

#### `ReceivedComment`
- **種別**: `data class`
- **責務**: 取得先の種別によらないコメントの共通形。
  これより先（読み上げ・オーバーレイ・ログ）は取得元を意識しない
- **フィールド**: `sourceUuid` / `no`（jpnkn のレス番号。ツイキャスには相当するものが無く空文字列）/ `name` / `message`
- **生成**: `fun JpnknMessage.toReceivedComment(sourceUuid): ReceivedComment`（拡張関数）、
  ツイキャスは `TwicasConnector` が `TwicasComment` から組み立てる

#### `JpnknMessage`
- **種別**: `data class`
- **フィールド**:

  | フィールド | 型 | 説明 |
  |---|---|---|
  | `body` | `String` | `名前<>メール<>日時<>本文<>` 形式の生データ |
  | `no` | `String` | レス番号 |
  | `bbsid` | `String` | 板 ID |
  | `threadkey` | `String` | スレッドキー |

- **body のパース仕様**: `<>` で `split` し、インデックスで各フィールドを取得

  | インデックス | 内容 | メソッド |
  |---|---|---|
  | 0 | 名前 | `extractName()` |
  | 1 | メール欄 | `extractMail()` |
  | 2 | 日時 | `extractDate()` |
  | 3 | 本文（`<br>` を改行に変換） | `extractMessage()` |

- **JSON 仕様**: `fromJson(String): JpnknMessage?`（失敗時 `null`）、`toJson(): String`

#### `MessageLog`
- **種別**: `data class`（UI 表示用の不変スナップショット）
- **フィールド**:

  | フィールド | 型 | 生成元 |
  |---|---|---|
  | `id` | `String` | `UUID.randomUUID().toString()` |
  | `no` | `String` | `ReceivedComment.no`（空なら表示しない） |
  | `name` | `String` | `ReceivedComment.name` |
  | `message` | `String` | `ReceivedComment.message` |
  | `timestamp` | `Long` | `System.currentTimeMillis()` |
  | `sourceColor` | `Int` | `CommentSource.color` |

- **生成**: `fun ReceivedComment.toLog(source: CommentSource): MessageLog`（拡張関数）

#### `MessageManager`
- **種別**: `object`（シングルトン）
- **責務**: Service と UI の状態ブリッジ
- **状態**:

  | StateFlow | 型 | 上限 |
  |---|---|---|
  | `messageLogs` | `StateFlow<List<MessageLog>>` | 500件（超過時に先頭から `drop`） |
  | `systemLogs` | `StateFlow<List<String>>` | 500件（同上） |
  | `sourceStatuses` | `StateFlow<Map<String, SourceStatus>>` | — （uuid → 接続状態。設定画面の一覧に出す） |

- **`addSystemLog` のフォーマット**: `[HH:mm:ss] テキスト`
- **接続状態のメソッド**: `updateSourceStatus(uuid, status)` / `removeSourceStatus(uuid)` / `clearSourceStatuses()`

#### `SettingsRepository`
- **永続化**: `androidx.datastore:datastore-preferences`
- **キー**:

  | キー | 型 | デフォルト値 |
  |---|---|---|
  | `comment_sources` | `stringPreferencesKey` | 未保存時は `board_id` から移行 |
  | `board_id` | `stringPreferencesKey` | `""` （移行元。取得先の保存後も消さずに残す） |
  | `overlay_enabled` | `booleanPreferencesKey` | `true` |
  | `max_message_length` | `intPreferencesKey` | `100`（`AppConfig.Tts.DEFAULT_MAX_MESSAGE_LENGTH`） |
  | `overlay_alpha` | `intPreferencesKey` | `80`（`AppConfig.Overlay.DEFAULT_ALPHA`） |
  | `speech_rate` | `intPreferencesKey` | `120`（`AppConfig.Tts.DEFAULT_SPEECH_RATE`） |
  | `speech_volume` | `intPreferencesKey` | `80`（`AppConfig.Tts.DEFAULT_VOLUME`） |
  | `auto_start_on_launch` | `booleanPreferencesKey` | `false` |

- **Flow プロパティ**: `commentSourcesFlow`・`overlayEnabledFlow`・`maxMessageLengthFlow`・`overlayAlphaFlow`・`overlayTextSizeFlow`・`speechRateFlow`・`speechVolumeFlow`・`autoStartOnLaunchFlow`
- **保存メソッド**: `saveCommentSources()`・`saveOverlayEnabled()`・`saveMaxMessageLength()`・`saveOverlayAlpha()`・`saveOverlayTextSize()`・`saveSpeechRate()`・`saveSpeechVolume()`・`saveAutoStartOnLaunch()`（各 `suspend fun`）
- **`commentSourcesFlow` の移行**: `comment_sources` キーが無ければ `board_id` から
  `CommentSource.migrateFromBoardId()` で組み立てる。移行結果はここでは保存せず、
  次に取得先が編集された時点で書き込まれる

---

### 2.4 機能層

#### `CommentConnector` / `SourceStatus`（`source/`）
- **`CommentConnector`**: 取得先 1 件の接続を表すインターフェース（`source` / `start()` / `stop()`）。
  jpnkn は MQTT、ツイキャスは WebSocket と手段が違うため、サービスからは同じ扱いにする
- **`CommentConnectorCallbacks`**: `onStatusChanged` / `onComment` / `onSystemLog` の 3 つ
- **`SourceStatus`**: `WAITING` / `CONNECTED` / `WAITING_BROADCAST` / `DISCONNECTED` / `ERROR`。
  それぞれ表示文言（`label`）と、対処が要らない状態かどうか（`isHealthy`）を持つ
  - `WAITING_BROADCAST`（ツイキャスの配信待ち）は待っているだけなので `isHealthy = true`。
    ここを異常扱いにすると、配信していない間ずっとオーバーレイが警告色になる
  - **`aggregate(statuses)`**: 複数の取得先の状態を 1 つにまとめる（オーバーレイは色を 1 つしか出せないため）。
    `ERROR` > `DISCONNECTED` > 全部 `WAITING` なら `WAITING` > それ以外は `CONNECTED`。
    取得先が無ければ `null`

#### `JpnknConnector`（`source/`）
- 既存の `MqttManager` を 1 取得先につき 1 つ持ち、通知を `CommentConnectorCallbacks` に流し替えるだけの薄い層
- `start()` で `AppConfig.Mqtt.createTopic(sourceId)` を購読、`stop()` で `MqttManager.shutdown()`

#### `TwicasConnector`（`source/`）
- **責務**: 配信待ち → コメント受信 → 切断 を繰り返す状態遷移（コルーチンの 1 ループ）
  1. `TwicasClient.fetchMovie()` で配信中かを確認。配信していなければ
     `WAITING_BROADCAST` にして 5 秒待ち、繰り返す
  2. 配信中なら `fetchCommentServerUrl()` → `openCommentSocket()` で `CONNECTED`
  3. 切断されたら 5 秒待って 1 に戻る（枠の終了も回線断もこの経路）
- ユーザーが見つからない場合は `ERROR`。通信エラーは `DISCONNECTED`
- **接続時に過去のコメントは取得しない**。読み上げアプリで過去ログを喋り始めると事故になるため
- 配信待ちは 5 秒ごとに回るので、状態が変わった瞬間だけシステムログに出す（`updateStatus`）

#### `SourceTester`（`source/`）
- **責務**: 取得先を登録する前に ID が正しいかを確かめる
- jpnkn: 板の URL（`AppConfig.Jpnkn.BOARD_BASE_URL` + 板 ID）が引けるか。
  MQTT はトピックの購読に成功しても板の実在までは分からないため、HTTP で確認する
- ツイキャス: `TwicasClient.fetchMovie()` の結果で「配信中」「配信の開始を待つ」「ユーザーが見つからない」を出し分ける

#### `TwicasClient` / `TwicasEvent`（`twicas/`）
- **ライブラリ**: OkHttp（HTTP と WebSocket の両方）
- 公式 API v2 ではなく認証不要の内部エンドポイントを使う。
  仕様と選定理由は `docs/spec/twicas-comment-spec.md` を参照
- **`TwicasClient`**: `fetchMovie()`（`streamserver.php`）、
  `fetchCommentServerUrl()`（`eventpubsuburl.php`）、`openCommentSocket()`（WebSocket）。
  回線が黙って切れたときに気付けるよう `pingInterval` を 30 秒に設定している
- **`TwicasEvent`**: 応答のパースだけを担う純粋関数の置き場（ユニットテストあり）。
  存在しないユーザーは HTTP 200 で `{}` が返るため、`movie` キーの有無で見分ける

#### `MqttManager`（`mqtt/`）
- **ライブラリ**: `com.hivemq:hivemq-mqtt-client:1.3.3`（MQTT v3.1.1）
- **接続パラメータ**:

  | 項目 | 値 |
  |---|---|
  | ホスト | `bbs.jpnkn.com:1883` |
  | 認証 | Username/Password（`AppConfig.Mqtt` 参照） |
  | KeepAlive | 60秒 |
  | CleanSession | `true` |
  | QoS | `AT_MOST_ONCE`（QoS 0） |
  | クライアント ID | `AppConfig.Mqtt.CLIENT_ID_PREFIX` + `_` + `UUID.randomUUID()`（取得先ごとに 1 本張るため、同時生成でも衝突しない値にしている） |

- **自動再接続**: 手動リトライ方式（指数バックオフ、試行回数上限なし）
  - 初回遅延: `AppConfig.Mqtt.INITIAL_RETRY_DELAY_MS`（1000ms）
  - 最大遅延: `AppConfig.Mqtt.MAX_RETRY_DELAY_MS`（60000ms）
  - バックオフ計算: `INITIAL_RETRY_DELAY_MS * 2^min(retryCount, 6)`（指数が 6 を超えないようクランプし、さらに MAX_RETRY_DELAY_MS で上限クランプ）
  - 切断検知: 購読成功後に 5秒間隔のポーリングウォッチャー（`startDisconnectWatcher()`）を起動し、`client.state.isConnected != true` を検知したら `scheduleReconnect()` を呼び出す
  - `isShuttingDown` フラグが `true` の場合は再接続をスキップ
  - `AppConfig.Mqtt.MAX_RETRY_ATTEMPTS`（10）は AppConfig に定義されているが、現在の `MqttManager` では参照されていない（実質的に試行回数は無制限）

- **メッセージ処理**: `handleMessage()` でバイト列 → `String` → `JpnknMessage.fromJson()` → コールバック
  - `extractMessage()` が空の場合はスキップ、パース失敗時は `onError` 通知

- **公開プロパティ**: `connectionState: Boolean`（`isConnected` の読み取り専用ビュー）

#### `TtsManager`（`tts/`）
- **API**: `android.speech.tts.TextToSpeech`
- **言語**: `Locale.JAPANESE`
- **読み上げキュー**: `ConcurrentLinkedQueue<String>`（スレッドセーフ）
- **定数**:

  | 定数 | 値 | 説明 |
  |---|---|---|
  | `SPEECH_INTERVAL_MS` | `500L` | 発話完了後から次の発話開始までの待機時間（ms） |

- **処理フロー**:
  ```
  enqueue(text)
    └─ isBlank() チェック（空文字はスキップ）
         └─ speechQueue.offer(text)
              └─ isInitialized でガード（未初期化時はスキップ、初期化後に再処理）
                   └─ processQueue()

  processQueue()
    └─ isSpeaking でガード
         └─ tts.speak(QUEUE_FLUSH)
              └─ UtteranceProgressListener.onDone() / onError()
                   └─ isSpeaking = false
                        └─ Handler.postDelayed(SPEECH_INTERVAL_MS) → processQueue()
  ```
- **初期化完了後処理**: `onInit(SUCCESS)` 後、`setSpeechRate` を適用し、キューに溜まっているメッセージがあれば即座に `processQueue()` を呼び出す
- **話す速度・音量**:
  - コンストラクタ引数 `speechRate`（100 で等倍の百分率）・`volume`（0〜100 %）で初期値を受け取る
  - 速度は `TextToSpeech.setSpeechRate(rate / 100f)`、音量は発話ごとに `Bundle(KEY_PARAM_VOLUME = volume / 100f)` で適用する（音量は次の発話から反映）
  - `setSpeechRate(rate)` / `setVolume(volume)` で稼働中に変更できる
- **その他メソッド**: `speakNow(text)`（キューを無視して即時発話。テスト再生用）、`stop()`（読み上げ中断）、`clearQueue()`（キュークリア）、`shutdown()`（stop + clearQueue + TTS 解放）

#### `OverlayManager`（`overlay/`）
- **権限**: `Settings.canDrawOverlays(context)`
- **ウィンドウタイプ**: `TYPE_APPLICATION_OVERLAY`
- **フラグ**: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`
- **ビュー**: `android.R.layout.simple_list_item_2`（`text1`=ステータス、`text2`=本文）
- **ステータス表示**: `updateStatus(status, color)` → `"$appName: $status"` 形式でアプリ名を付与
- **ドラッグ**: `ACTION_DOWN` で初期位置を記録、`ACTION_MOVE` で `WindowManager.updateViewLayout`
- **UI 更新**: `Handler(Looper.getMainLooper()).post` でメインスレッドに切り替え
- **本文の切り詰め**: `AppConfig.Overlay.MAX_MESSAGE_LENGTH`（= 30文字）を超えた場合 `...` を付加
- **公開メソッド**:

  | メソッド | 説明 |
  |---|---|
  | `create(alpha: Int = 80): Boolean` | オーバーレイウィンドウを作成。`alpha`（0〜100 %）を背景色に反映。権限がない・例外時は `false` を返す |
  | `updateAlpha(alpha: Int)` | 背景色を `Color.argb(alpha * 255 / 100, 0, 0, 0)` で即時更新（メインスレッドに `post`） |
  | `remove()` | オーバーレイウィンドウを削除 |
  | `showConnected()` | ステータスを「接続済み」（緑）に更新 |
  | `showDisconnected()` | ステータスを「切断」（黄）に更新 |
  | `showNotConnected()` | ステータスを「未接続」（赤）に更新 |
  | `updateMessage(message)` | 本文を更新（MAX_MESSAGE_LENGTH で切り詰め） |

---

### 2.5 UI 層（`ui/`）

#### 画面構成（`MainActivity` の `JpnknVoxApp`）

```
Scaffold
 ├─ TopAppBar
 │   ├─ タイトル "JPNKN Vox"
 │   └─ actions: ステータスドット + "稼働中/停止中" + Switch
 ├─ BottomNavigationBar（Screen.items: Home / Log / Settings）
 └─ NavHost（startDestination = Screen.Home）
     ├─ HomeScreen
     ├─ LogScreen
     └─ SettingsScreen
```

#### `HomeScreen`
- **データ源**: `MessageManager.messageLogs.collectAsState()`
- **並び順**: 新着が上（`messageLogs.asReversed()`）
- **リスト**: `LazyColumn`（`key = { it.id }`）
- **自動スクロール**:
  - `isAtTop`: `derivedStateOf { listState.firstVisibleItemIndex == 0 }`
  - `LaunchedEffect(size)` で `isAtTop == true` のときのみ `animateScrollToItem(0)`
  - 読み返している最中に先頭へ引き戻さないため、先頭にいるときだけ追従する
- **各アイテムレイアウト**:
  ```
  ▌[no] name（Bold）      ← ▌は取得先の識別色。no は空なら出さない
  ─────────────────
  message（制限なし）
                          HH:mm:ss（Gray）
  ```
  取得先は色だけで示し、板 ID などの文字は出さない

#### `LogScreen`
- **データ源**: `MessageManager.systemLogs.collectAsState()`（`logMessages` 引数はフォールバック）
- **リスト**: `LazyColumn`（モノスペースフォント、緑文字 `#00FF00`）
- **自動スクロール**: `LaunchedEffect(allLogs.size)` → `animateScrollToItem(末尾)`

#### `SettingsScreen`
- **引数**:

  | 引数 | 型 | 説明 |
  |---|---|---|
  | `sources` | `List<CommentSource>` | コメント取得先の一覧 |
  | `sourceStatuses` | `Map<String, SourceStatus>` | uuid ごとの接続状態 |
  | `onAddSource` | `(SourceType, String, String, Int) -> Unit` | 取得先の追加 |
  | `onUpdateSource` | `(String, String, String, Int) -> Unit` | 取得先の更新（第 1 引数は uuid） |
  | `onRemoveSource` | `(String) -> Unit` | 取得先の削除 |
  | `onTestSource` | `(SourceType, String, (SourceTestResult) -> Unit) -> Unit` | 接続テスト |
  | `isServiceRunning` | `Boolean` | サービス稼働中フラグ |
  | `hasNotificationPermission` | `Boolean` | 通知権限状態 |
  | `hasOverlayPermission` | `Boolean` | オーバーレイ権限状態 |
  | `isOverlayEnabled` | `Boolean` | オーバーレイ表示の有効状態 |
  | `onOverlayEnabledChange` | `(Boolean) -> Unit` | オーバーレイ ON/OFF コールバック |
  | `overlayAlpha` | `Int` | オーバーレイ背景の濃さ（0〜100 %） |
  | `onOverlayAlphaChange` | `(Int) -> Unit` | オーバーレイ濃さ変更コールバック |
  | `maxMessageLength` | `Int` | 読み上げ最大文字数 |
  | `onMaxMessageLengthChange` | `(Int) -> Unit` | 最大文字数変更コールバック |
  | `speechRate` | `Int` | 話す速度（100 で等倍の百分率） |
  | `onSpeechRateChange` | `(Int) -> Unit` | 話す速度変更コールバック |
  | `speechVolume` | `Int` | 読み上げ音量（0〜100 %） |
  | `onSpeechVolumeChange` | `(Int) -> Unit` | 音量変更コールバック |
  | `autoStartOnLaunch` | `Boolean` | アプリ起動時の自動開始 |
  | `onAutoStartOnLaunchChange` | `(Boolean) -> Unit` | 自動開始 ON/OFF コールバック |
  | `onTestSpeech` | `() -> Unit` | テスト再生 |
  | `onRequestNotificationPermission` | `() -> Unit` | 通知権限リクエスト |
  | `onRequestOverlayPermission` | `() -> Unit` | オーバーレイ権限リクエスト |

- **レイアウト**: `docs/references/jpnkn-vox-settings-inline.html` のモックアップに準拠。カードは使わず、セクション見出し（`HorizontalDivider` + `labelMedium` / primary 色）と行リストで構成する
- **構成**:
  1. **権限バナー（`PermissionBanner`）**: 未許可のオーバーレイ権限・通知権限それぞれについて `errorContainer` 色のバナーを表示。タップで権限リクエストへ進む。許可済みの権限はバナーごと消える
  2. **コメント取得先**: 取得先ごとに 1 行（左端に識別色の帯、副題に `jpnkn · bbs/xxx` をモノスペース表示、右端に接続状態）。行タップで編集シート、末尾に「コメント取得先を追加」。1 件も無ければ「まだ登録されていません」を出す。**稼働中でも追加・削除できる**（その場で接続・切断される）
  3. **読み上げ**: 話す速度スライダー（50〜200 %、`1.2x` 形式で表示）／音量スライダー（0〜100 %）／最大文字数行（ダイアログで編集）／テスト再生ボタン（`OutlinedButton`）
  4. **表示**: オーバーレイ表示スイッチ（オーバーレイ権限がない場合は無効）／背景の濃さスライダー／文字の大きさ（オーバーレイ権限がないか OFF の場合は無効）
  5. **動作**: 起動時に自動で開始スイッチ（アプリを開いたときにサービスを自動開始する。端末の再起動時ではない）
- **取得先の編集シート（`SourceEditSheet`）**: `ModalBottomSheet`（`skipPartiallyExpanded = true`。
  接続テストの結果で中身の高さが変わるたびにシートが初期位置まで下がるのを防ぐため、常に全開で使う）。
  サービス種別（追加時のみ `SingleChoiceSegmentedButtonRow` で選ぶ。**編集時は変更不可**で、
  選べない選択肢を並べると押せる物に見えるため、ただの文字として出す。
  接続先が変わると別の取得先と区別が付かなくなるので、変えたい場合は削除して追加し直す）／
  ID 欄（`supportingText` は、未入力なら何を入れる欄かの説明、入力済みなら `bbs/xxx` などの取得先の場所に切り替える）／
  識別色（`AppConfig.Source.PALETTE` の 7 色。選択中はチェックと、少し離した位置のリングで示す。
  円の縁に線を引くだけだと濃い色で線が埋もれるため）／接続をテスト／追加・保存／削除（編集時のみ）
- **接続状態の表示**: サービス停止中は接続していないので、状態によらず「待機」に見せる
- **共通部品**: `SettingRow`（タップで編集）・`SwitchSettingRow`・`SliderSettingRow`・`ChoiceDialog`・`EditValueDialog`（`sanitize` で入力文字を制限し、`isValid` で保存ボタンを制御）
- **スライダーの保存タイミング**: ドラッグ中は内部状態のみ更新し、指を離した時点（`onValueChangeFinished`）で永続化とサービス反映を行う
- **スライダーの刻み**: トラック上に目盛りが表示されるのを避けるため、いずれのスライダーも `steps` は設定せず連続値で扱う

#### `Screen`（ナビゲーション定義）
```kotlin
sealed class Screen(route, title, icon)
  Home     → "home"     / "ホーム"  / Icons.Default.Home
  Log      → "log"      / "ログ"    / Icons.AutoMirrored.Filled.List
  Settings → "settings" / "設定"    / Icons.Default.Settings
```

---

### 2.6 設定・定数（`config/AppConfig`）

| ネームスペース | 定数 | 値 |
|---|---|---|
| `Mqtt` | `SERVER_HOST` | `bbs.jpnkn.com` |
| `Mqtt` | `SERVER_PORT` | `1883` |
| `Mqtt` | `USERNAME` | `genkai` |
| `Mqtt` | `PASSWORD` | `7144` |
| `Mqtt` | `TOPIC_PREFIX` | `bbs/` |
| `Mqtt` | `CLIENT_ID_PREFIX` | `jpnkn_vox_android` |
| `Mqtt` | `INITIAL_RETRY_DELAY_MS` | `1000L` |
| `Mqtt` | `MAX_RETRY_DELAY_MS` | `60000L` |
| `Mqtt` | `MAX_RETRY_ATTEMPTS` | `10` |
| `Jpnkn` | `BOARD_BASE_URL` | `https://bbs.jpnkn.com/`（接続テストで板の実在確認に使う） |
| `Twicas` | `STREAM_SERVER_URL` | `https://twitcasting.tv/streamserver.php` |
| `Twicas` | `EVENT_PUBSUB_URL` | `https://twitcasting.tv/eventpubsuburl.php` |
| `Twicas` | `BROADCAST_POLLING_INTERVAL_MS` | `5000L`（配信開始待ちのポーリング間隔） |
| `Twicas` | `RECONNECT_DELAY_MS` | `5000L` |
| `Twicas` | `PING_INTERVAL_SEC` | `30L` |
| `Twicas` | `REQUEST_TIMEOUT_SEC` | `15L` |
| `Source` | `PALETTE` | 識別色 7 色（赤・黄・緑・水色・青・紫・桃。色相順） |
| `Notification` | `CHANNEL_ID` | `jpnkn_vox_channel` |
| `Notification` | `CHANNEL_NAME` | `JPNKN Vox サービス` |
| `Notification` | `ID` | `1` |
| `Tts` | `DEFAULT_SPEECH_RATE` | `120`（%） |
| `Tts` | `MIN_SPEECH_RATE` / `MAX_SPEECH_RATE` | `50` / `200`（%） |
| `Tts` | `DEFAULT_VOLUME` | `80`（%） |
| `Tts` | `DEFAULT_MAX_MESSAGE_LENGTH` | `100` |
| `Tts` | `TEST_TEXT` | `じゃぱんくん-Vox のテスト再生です` |
| `Overlay` | `MAX_MESSAGE_LENGTH` | `30` |
| `Overlay` | `DEFAULT_ALPHA` | `80`（%） |
| `Overlay` | `INITIAL_Y_POSITION` | `100` |

---

## 3. 権限

| 権限 | 用途 | 取得タイミング |
|---|---|---|
| `FOREGROUND_SERVICE` | サービス常駐 | AndroidManifest（自動付与） |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ のフォアグラウンドサービス | AndroidManifest（自動付与） |
| `POST_NOTIFICATIONS` | 常駐通知の表示 | Android 13+ は実行時リクエスト |
| `SYSTEM_ALERT_WINDOW` | オーバーレイ表示 | `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` へ誘導 |

---

## 4. 未実装項目（SRS との差分）

SRS に記載があるが現時点で未実装の機能。今後の課題として管理する。

| SRS 要件 | 状態 | 備考 |
|---|---|---|
| TTS 速度・ピッチ・音量の調整 | 部分実装 | 速度（50〜200 %）・音量（0〜100 %）は `SettingsScreen` から設定可能。ピッチは未実装 |
| NG ワードフィルタリング | 未実装 | `TtsManager.enqueue()` にフィルタ処理を追加 |
| URL・記号スキップ | 未実装 | 同上 |
| 読み上げキューの自動スキップ（大量連投時） | 部分実装 | `TtsManager.clearQueue()` は実装済み。キュー上限超過時の自動クリアロジックは未実装 |
| 通知領域からの操作 | 部分実装 | 通知タップで `MainActivity` を起動する `PendingIntent` は設定済み。通知アクションボタン（停止など）は未実装 |
| Audio Focus 管理 | 未実装 | `AudioManager.requestAudioFocus` を `TtsManager` に追加 |
| 接続状態の詳細表示（再試行中など） | 部分実装 | `TopAppBar` は稼働中/停止中のみ。再試行中状態は未反映 |

