# 詳細設計書：JPNKN Vox for Android

**バージョン**: 1.0  
**作成日**: 2026-02-28  
**対応 SRS**: `docs/SRS-jpnkn-vox.md`

---

## 1. システム全体構成

### 1.1 コンポーネント図

```
┌─────────────────────────────────────────────────────┐
│  UI レイヤー（Jetpack Compose）                       │
│  MainActivity  ─  MainViewModel                     │
│  HomeScreen / LogScreen / SettingsScreen            │
└──────────────┬──────────────────────────────────────┘
               │ collectAsState
┌──────────────▼──────────────────────────────────────┐
│  状態ブリッジ（data/MessageManager）                  │
│  messageLogs: StateFlow  /  systemLogs: StateFlow   │
└──────────────┬──────────────────────────────────────┘
               │ addMessage / addSystemLog
┌──────────────▼──────────────────────────────────────┐
│  サービスレイヤー（JpnknVoxService）                   │
│  MqttManager  /  TtsManager  /  OverlayManager      │
└─────────────────────────────────────────────────────┘
```

### 1.2 データフロー

```
[MQTT ブローカー]
      │ TCP:1883
      ▼
MqttManager.handleMessage()
      │ JSON ペイロード
      ▼
JpnknMessage.fromJson()          ← パース失敗時は onError にフォールバック
      │
      ├─► MessageManager.addMessage()  → messageLogs StateFlow → HomeScreen
      ├─► MessageManager.addSystemLog() → systemLogs StateFlow → LogScreen
      ├─► OverlayManager.updateMessage() → システムオーバーレイ
      └─► TtsManager.enqueue()         → TextToSpeech → スピーカー
```

### 1.3 サービス制御フロー

```
UI スイッチ ON
  └─ MainViewModel.startService()
       └─ ServiceController.start(boardId)
            └─ startForegroundService(Intent)
                 └─ JpnknVoxService.onCreate()
                      ├─ TtsManager 初期化
                      └─ MqttManager 初期化 → onTtsInitialized() 後に connect()

UI スイッチ OFF
  └─ MainViewModel.stopService()
       └─ ServiceController.stop()
            └─ stopService(Intent)
                 └─ JpnknVoxService.onDestroy()
                      ├─ OverlayManager.remove()
                      ├─ MqttManager.shutdown()
                      └─ TtsManager.shutdown()
```

---

## 2. クラス設計

### 2.1 エントリーポイント層（ルートパッケージ）

#### `MainActivity`
- **責務**: 権限リクエスト・Compose UI のセットアップのみ
- **保持するもの**: `requestNotificationPermissionLauncher`
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
  | `boardId` | `MutableState<String>` | 現在の板 ID |

- **委譲先**: `ServiceController`（起動・停止）、`SettingsRepository`（板 ID 永続化）

#### `ServiceController`
- **責務**: `JpnknVoxService` の起動・停止を `Application` コンテキスト経由で実行
- **メソッド**:

  | メソッド | 説明 |
  |---|---|
  | `start(boardId: String)` | `startForegroundService` でサービス起動、`MessageManager.addSystemLog` に記録 |
  | `stop()` | `stopService` でサービス停止、`MessageManager.addSystemLog` に記録 |

---

### 2.2 サービス層

#### `JpnknVoxService`
- **継承**: `Service`
- **種別**: Foreground Service（`START_STICKY`）
- **通知**: `NotificationChannel(IMPORTANCE_LOW)` + `startForeground`
  - Android 14+: `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`
- **ライフサイクルと処理**:

  ```
  onCreate()
    ├─ LogBroadcaster 初期化
    ├─ NotificationChannel 作成
    ├─ OverlayManager.create()
    ├─ TtsManager(onInitialized = ::onTtsInitialized)
    └─ MqttManager(各コールバック).initialize()

  onStartCommand(intent)
    ├─ EXTRA_BOARD_ID を boardId にセット
    └─ startForeground(通知)

  onDestroy()
    ├─ MessageManager.addSystemLog("停止")
    ├─ OverlayManager.remove()
    ├─ MqttManager.shutdown()
    └─ TtsManager.shutdown()
  ```

- **コールバック**:

  | コールバック | 処理 |
  |---|---|
  | `onTtsInitialized()` | `MessageManager.addSystemLog`、`MqttManager.connect(topic)` |
  | `onMqttConnected()` | `MessageManager.addSystemLog`、`OverlayManager.showConnected()`、TTS「接続しました」 |
  | `onMqttDisconnected(cause)` | `MessageManager.addSystemLog`、`OverlayManager.showDisconnected()` |
  | `onMessageReceived(message)` | `MessageManager.addMessage()`、`addSystemLog()`、`OverlayManager.updateMessage()`、`TtsManager.enqueue()` |

---

### 2.3 データ層（`data/` パッケージ）

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

  | インデックス | 内容 |
  |---|---|
  | 0 | 名前（`extractName()`） |
  | 1 | メール欄（`extractMail()`） |
  | 2 | 日時（`extractDate()`） |
  | 3 | 本文（`extractMessage()`） |

- **JSON 仕様**: `fromJson(String): JpnknMessage?`（失敗時 `null`）、`toJson(): String`

#### `MessageLog`
- **種別**: `data class`（UI 表示用の不変スナップショット）
- **フィールド**:

  | フィールド | 型 | 生成元 |
  |---|---|---|
  | `id` | `String` | `UUID.randomUUID().toString()` |
  | `no` | `String` | `JpnknMessage.no` |
  | `name` | `String` | `JpnknMessage.extractName()` |
  | `message` | `String` | `JpnknMessage.extractMessage()` |
  | `timestamp` | `Long` | `System.currentTimeMillis()` |

- **生成**: `fun JpnknMessage.toLog(): MessageLog`（拡張関数）

#### `MessageManager`
- **種別**: `object`（シングルトン）
- **責務**: Service と UI の状態ブリッジ
- **状態**:

  | StateFlow | 型 | 上限 |
  |---|---|---|
  | `messageLogs` | `StateFlow<List<MessageLog>>` | 500件（超過時に先頭から `drop`） |
  | `systemLogs` | `StateFlow<List<String>>` | 500件（同上） |

- **`addSystemLog` のフォーマット**: `[HH:mm:ss] テキスト`

#### `SettingsRepository`
- **永続化**: `androidx.datastore:datastore-preferences`
- **キー**: `board_id`（`stringPreferencesKey`）
- **デフォルト値**: `AppConfig.Mqtt.DEFAULT_TOPIC` から `bbs/` プレフィックスを除去した値

---

### 2.4 機能層

#### `MqttManager`（`mqtt/`）
- **ライブラリ**: `com.hivemq:hivemq-mqtt-client:1.3.3`（MQTT v3.1.1）
- **接続パラメータ**:

  | 項目 | 値 |
  |---|---|
  | ホスト | `bbs.jpnkn.com:1883` |
  | 認証 | Username/Password（`AppConfig.Mqtt` 参照） |
  | KeepAlive | 60秒 |
  | CleanSession | `false` |
  | QoS | `AT_MOST_ONCE`（QoS 0） |

- **自動再接続**: HiveMQ の `automaticReconnect` 機能を使用
  - 初回遅延: `1000ms`、最大遅延: `60000ms`
  - 接続/切断は `addConnectedListener` / `addDisconnectedListener` で検知

- **メッセージ処理**: `handleMessage()` でバイト列 → `String` → `JpnknMessage.fromJson()` → コールバック
  - 本文が空の場合はスキップ、パース失敗時は `onError` 通知

#### `TtsManager`（`tts/`）
- **API**: `android.speech.tts.TextToSpeech`
- **言語**: `Locale.JAPANESE`
- **読み上げキュー**: `ConcurrentLinkedQueue<String>`（スレッドセーフ）
- **処理フロー**:
  ```
  enqueue(text)
    └─ speechQueue.offer(text)
         └─ processQueue()（未初期化時はスキップ、初期化後に再処理）

  processQueue()
    └─ isSpeaking でガード
         └─ tts.speak(QUEUE_FLUSH)
              └─ UtteranceProgressListener.onDone() → processQueue() 再帰
  ```
- **空文字ガード**: `enqueue` で `isBlank()` チェック

#### `OverlayManager`（`overlay/`）
- **権限**: `Settings.canDrawOverlays(context)`
- **ウィンドウタイプ**: `TYPE_APPLICATION_OVERLAY`
- **フラグ**: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN`
- **ビュー**: `android.R.layout.simple_list_item_2`（`text1`=ステータス、`text2`=本文）
- **ドラッグ**: `ACTION_DOWN` で初期位置を記録、`ACTION_MOVE` で `WindowManager.updateViewLayout`
- **UI 更新**: `Handler(Looper.getMainLooper()).post` でメインスレッドに切り替え
- **本文の切り詰め**: `AppConfig.Overlay.MAX_MESSAGE_LENGTH`（= 30文字）を超えた場合 `...` を付加

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
- **リスト**: `LazyColumn`（`key = { it.id }`）
- **自動スクロール**:
  - `isAtBottom`: `derivedStateOf { lastVisibleIndex >= totalItems - 2 }`
  - `LaunchedEffect(messageLogs.size)` で `isAtBottom == true` のときのみ `animateScrollToItem(末尾)`
- **各アイテムレイアウト**:
  ```
  [no] name（Bold）
  ─────────────────
  message（制限なし）
                          HH:mm:ss（Gray）
  ```

#### `LogScreen`
- **データ源**: `MessageManager.systemLogs.collectAsState()`（`logMessages` 引数はフォールバック）
- **リスト**: `LazyColumn`（モノスペースフォント、緑文字 `#00FF00`）
- **自動スクロール**: `LaunchedEffect(allLogs.size)` → `animateScrollToItem(末尾)`

#### `SettingsScreen`
- **板 ID 入力**: `OutlinedTextField`（英数字・アンダースコアのみ許可、サービス稼働中は無効）
- **権限状態**: 通知権限・オーバーレイ権限それぞれの取得ボタン付きカード

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
| `Mqtt` | `TOPIC_PREFIX` | `bbs/` |
| `Mqtt` | `INITIAL_RETRY_DELAY_MS` | `1000` |
| `Mqtt` | `MAX_RETRY_DELAY_MS` | `60000` |
| `Notification` | `CHANNEL_ID` | `jpnkn_vox_channel` |
| `Notification` | `ID` | `1` |
| `Overlay` | `MAX_MESSAGE_LENGTH` | `30` |
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
| TTS 速度・ピッチ・音量の調整 | 未実装 | `SettingsScreen` に項目追加が必要 |
| NG ワードフィルタリング | 未実装 | `TtsManager.enqueue()` にフィルタ処理を追加 |
| URL・記号スキップ | 未実装 | 同上 |
| 読み上げキューの自動スキップ（大量連投時） | 未実装 | `TtsManager` のキュー上限と `clearQueue` ロジック追加が必要 |
| 通知領域からの操作 | 未実装 | `PendingIntent` を持つ通知アクションを追加 |
| Audio Focus 管理 | 未実装 | `AudioManager.requestAudioFocus` を `TtsManager` に追加 |
| 接続状態の詳細表示（再試行中など） | 部分実装 | `TopAppBar` は稼働中/停止中のみ。再試行中状態は未反映 |

