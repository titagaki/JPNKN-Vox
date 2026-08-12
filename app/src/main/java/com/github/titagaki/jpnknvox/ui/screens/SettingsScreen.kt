package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.CommentSource
import com.github.titagaki.jpnknvox.data.SourceType
import com.github.titagaki.jpnknvox.source.SourceStatus
import com.github.titagaki.jpnknvox.source.SourceTestResult

/**
 * オーバーレイの文字の大きさとして選べる段階（sp と表示名）
 *
 * 既定値の [AppConfig.Overlay.DEFAULT_TEXT_SIZE] は「中」に対応する。
 */
private val TEXT_SIZE_PRESETS: List<Pair<Int, String>> = listOf(
    10 to "小",
    12 to "中",
    16 to "大",
    22 to "特大"
)

/**
 * 設定画面
 *
 * `docs/references/jpnkn-vox-settings-inline.html` のモックアップに沿った
 * セクション見出し＋行リスト形式のレイアウト。
 */
@Composable
fun SettingsScreen(
    sources: List<CommentSource>,
    sourceStatuses: Map<String, SourceStatus>,
    onAddSource: (SourceType, String, Int) -> Unit,
    onUpdateSource: (String, String, Int) -> Unit,
    onRemoveSource: (String) -> Unit,
    onTestSource: (SourceType, String, (SourceTestResult) -> Unit) -> Unit,
    isServiceRunning: Boolean,
    hasNotificationPermission: Boolean,
    hasOverlayPermission: Boolean,
    isOverlayEnabled: Boolean,
    onOverlayEnabledChange: (Boolean) -> Unit,
    overlayAlpha: Int,
    onOverlayAlphaChange: (Int) -> Unit,
    overlayTextSize: Int,
    onOverlayTextSizeChange: (Int) -> Unit,
    maxMessageLength: Int,
    onMaxMessageLengthChange: (Int) -> Unit,
    speechRate: Int,
    onSpeechRateChange: (Int) -> Unit,
    speechVolume: Int,
    onSpeechVolumeChange: (Int) -> Unit,
    autoStartOnLaunch: Boolean,
    onAutoStartOnLaunchChange: (Boolean) -> Unit,
    onTestSpeech: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMaxLengthDialog by remember { mutableStateOf(false) }
    var showTextSizeDialog by remember { mutableStateOf(false) }

    // 編集シートの対象。null なら閉じている。追加のときは editingSource が null の Add
    var sheetTarget by remember { mutableStateOf<SourceSheetTarget?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 未許可の権限をバナーで通知
        if (!hasOverlayPermission) {
            PermissionBanner(
                title = "オーバーレイ権限が未許可",
                subtitle = "タップして許可する",
                onClick = onRequestOverlayPermission
            )
        }
        if (!hasNotificationPermission) {
            PermissionBanner(
                title = "通知権限が未許可",
                subtitle = "タップして許可する",
                onClick = onRequestNotificationPermission
            )
        }

        // ========== コメント取得先 ==========
        SectionHeader(title = "コメント取得先", showDivider = false)
        if (sources.isEmpty()) {
            Text(
                text = "まだ登録されていません",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            sources.forEach { source ->
                SourceRow(
                    source = source,
                    status = sourceStatuses[source.uuid],
                    isServiceRunning = isServiceRunning,
                    onClick = { sheetTarget = SourceSheetTarget(source) }
                )
            }
        }
        AddSourceRow(onClick = { sheetTarget = SourceSheetTarget(null) })

        // ========== 読み上げ ==========
        SectionHeader(title = "読み上げ")
        SliderSettingRow(
            label = "話す速度",
            value = speechRate,
            valueRange = AppConfig.Tts.MIN_SPEECH_RATE.toFloat()..AppConfig.Tts.MAX_SPEECH_RATE.toFloat(),
            valueLabel = { "%.1fx".format(it / 100f) },
            onValueChangeFinished = onSpeechRateChange
        )
        SliderSettingRow(
            label = "音量",
            value = speechVolume,
            valueRange = 0f..100f,
            valueLabel = { "$it%" },
            onValueChangeFinished = onSpeechVolumeChange
        )
        SettingRow(
            title = "最大文字数",
            subtitle = "超えた分は「以下略」として省略します",
            value = maxMessageLength.toString(),
            onClick = { showMaxLengthDialog = true }
        )
        OutlinedButton(
            onClick = onTestSpeech,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("テスト再生")
        }

        // ========== 表示 ==========
        SectionHeader(title = "表示")
        SwitchSettingRow(
            title = "オーバーレイ表示",
            checked = isOverlayEnabled,
            enabled = hasOverlayPermission,
            onCheckedChange = onOverlayEnabledChange
        )
        SliderSettingRow(
            label = "背景の濃さ",
            value = overlayAlpha,
            valueRange = 0f..100f,
            enabled = hasOverlayPermission && isOverlayEnabled,
            valueLabel = { "$it%" },
            onValueChangeFinished = onOverlayAlphaChange
        )
        SettingRow(
            title = "文字の大きさ",
            subtitle = "オーバーレイに表示するコメントの大きさ",
            value = textSizeLabel(overlayTextSize),
            enabled = hasOverlayPermission && isOverlayEnabled,
            onClick = { showTextSizeDialog = true }
        )

        // ========== 動作 ==========
        SectionHeader(title = "動作")
        SwitchSettingRow(
            title = "起動時に自動で開始",
            subtitle = "アプリを開いたときに読み上げを自動で開始します",
            checked = autoStartOnLaunch,
            onCheckedChange = onAutoStartOnLaunchChange
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    sheetTarget?.let { target ->
        SourceEditSheet(
            editingSource = target.source,
            // 追加のたびに色をずらし、既定のままでも取得先を見分けられるようにする
            defaultColor = AppConfig.Source.PALETTE[sources.size % AppConfig.Source.PALETTE.size],
            onDismiss = { sheetTarget = null },
            onTest = onTestSource,
            onSave = { type, sourceId, color ->
                val editing = target.source
                if (editing == null) {
                    onAddSource(type, sourceId, color)
                } else {
                    onUpdateSource(editing.uuid, sourceId, color)
                }
                sheetTarget = null
            },
            onDelete = {
                target.source?.let { onRemoveSource(it.uuid) }
                sheetTarget = null
            }
        )
    }

    if (showMaxLengthDialog) {
        EditValueDialog(
            title = "最大文字数",
            initialValue = maxMessageLength.toString(),
            label = "最大文字数",
            supportingText = "指定した文字数を超えるメッセージは「以下略」として省略して読み上げます。",
            keyboardType = KeyboardType.Number,
            sanitize = { input -> input.filter { it.isDigit() } },
            isValid = { (it.toIntOrNull() ?: 0) > 0 },
            onDismiss = { showMaxLengthDialog = false },
            onConfirm = { value ->
                value.toIntOrNull()?.let { onMaxMessageLengthChange(it) }
                showMaxLengthDialog = false
            }
        )
    }

    if (showTextSizeDialog) {
        ChoiceDialog(
            title = "文字の大きさ",
            choices = TEXT_SIZE_PRESETS,
            selected = overlayTextSize,
            onDismiss = { showTextSizeDialog = false },
            onSelect = {
                onOverlayTextSizeChange(it)
                showTextSizeDialog = false
            }
        )
    }
}

// ========================================
// コメント取得先
// ========================================

/**
 * 編集シートを開く対象
 *
 * @param source 編集する取得先。null なら新規追加
 */
private data class SourceSheetTarget(val source: CommentSource?)

/**
 * 取得先 1 件の行
 *
 * 左端に識別色の帯、右端に接続状態を出す。
 * 停止中は接続していないので、状態は「待機」に見せる。
 */
@Composable
private fun SourceRow(
    source: CommentSource,
    status: SourceStatus?,
    isServiceRunning: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(source.color))
        )
        Column(modifier = Modifier.weight(1f)) {
            // 等幅にすると 1 文字ずつが広がって全角のように見えるので、本文と同じ書体で出す
            Text(
                text = source.sourceId,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = source.type.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        SourceStatusLabel(status = status, isServiceRunning = isServiceRunning)
    }
}

/**
 * 行の右端に出す接続状態
 */
@Composable
private fun SourceStatusLabel(status: SourceStatus?, isServiceRunning: Boolean) {
    val effectiveStatus = if (isServiceRunning) status else null

    val (text, color) = when {
        effectiveStatus == null -> "待機" to MaterialTheme.colorScheme.onSurfaceVariant
        effectiveStatus == SourceStatus.CONNECTED -> effectiveStatus.label to CONNECTED_COLOR
        effectiveStatus.isHealthy -> effectiveStatus.label to MaterialTheme.colorScheme.onSurfaceVariant
        else -> effectiveStatus.label to MaterialTheme.colorScheme.error
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

/**
 * 取得先を追加する行
 */
@Composable
private fun AddSourceRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "コメント取得先を追加",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 取得先の追加・編集シート
 *
 * 種別は追加時にしか選べない。接続先が変わると別の取得先と区別が付かなくなるため、
 * 変えたい場合は削除して追加し直してもらう。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditSheet(
    editingSource: CommentSource?,
    defaultColor: Int,
    onDismiss: () -> Unit,
    onTest: (SourceType, String, (SourceTestResult) -> Unit) -> Unit,
    onSave: (SourceType, String, Int) -> Unit,
    onDelete: () -> Unit
) {
    val isEditing = editingSource != null

    var type by remember { mutableStateOf(editingSource?.type ?: SourceType.JPNKN) }
    var sourceId by remember { mutableStateOf(editingSource?.sourceId ?: "") }
    var color by remember { mutableIntStateOf(editingSource?.color ?: defaultColor) }
    var testResult by remember { mutableStateOf<SourceTestResult?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    // 接続テストの結果が出ると中身の高さが変わる。半開き状態を許すと
    // そのたびにシートが初期位置まで下がってしまうので、常に全開で使う
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isEditing) editingSource.sourceId else "コメント取得先を追加",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (isEditing) {
                    "サービスの種類は変更できません。変える場合は削除して追加し直してください。"
                } else {
                    "読み上げたい掲示板や配信を登録します。稼働中でもそのまま追加できます。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SheetFieldLabel("サービス")
            if (isEditing) {
                // 選べない選択肢を並べても押せる物に見えてしまうので、
                // 編集中はただの文字として出す（変えられないことは上の説明で伝える）
                Text(
                    text = type.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
            } else {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SourceType.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = type == entry,
                            onClick = {
                                type = entry
                                sourceId = ""
                                testResult = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SourceType.entries.size
                            )
                        ) {
                            Text(entry.label)
                        }
                    }
                }
            }

            SheetFieldLabel(type.idFieldLabel)
            OutlinedTextField(
                value = sourceId,
                onValueChange = { input ->
                    sourceId = input.filter { it.isLetterOrDigit() || it == '_' || it == ':' }
                    testResult = null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                supportingText = {
                    // 未入力なら何を入れる欄かを説明し、
                    // 入力済みならその ID がどこを指すかに切り替える
                    val hint = type.locationHint(sourceId)
                    Text(
                        text = hint.ifEmpty { type.idFieldDescription },
                        fontFamily = if (hint.isEmpty()) null else FontFamily.Monospace
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            SheetFieldLabel("識別色")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AppConfig.Source.PALETTE.forEach { paletteColor ->
                    ColorSwatch(
                        color = paletteColor,
                        selected = paletteColor == color,
                        onClick = { color = paletteColor }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    isTesting = true
                    testResult = null
                    onTest(type, sourceId) { result ->
                        testResult = result
                        isTesting = false
                    }
                },
                enabled = !isTesting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isTesting) "接続を確認しています…" else "接続をテスト")
            }

            testResult?.let { result ->
                val (message, resultColor) = when (result) {
                    is SourceTestResult.Success -> result.message to CONNECTED_COLOR
                    is SourceTestResult.Failure -> result.message to MaterialTheme.colorScheme.error
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = resultColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = { onSave(type, sourceId, color) },
                enabled = sourceId.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEditing) "保存する" else "追加する")
            }

            if (isEditing) {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("削除する")
                }
            }
        }
    }
}

/**
 * 編集シートの中の項目名
 */
@Composable
private fun SheetFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
}

/**
 * 識別色の選択肢 1 つ
 *
 * 選択中はチェックと、少し離した位置のリングの 2 つで示す。
 * 円の縁に線を引くだけだと、濃い色では線が色に埋もれて分かりにくい。
 * 外側の枠は選択中だけ描くが、大きさは常に同じにして並びがずれないようにする。
 */
@Composable
private fun ColorSwatch(
    color: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val swatchColor = Color(color)

    Box(
        modifier = Modifier
            .size(SWATCH_TOTAL_SIZE)
            .then(
                if (selected) {
                    Modifier.border(width = 2.dp, color = swatchColor, shape = CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(SWATCH_RING_GAP)
            .clip(CircleShape)
            .background(swatchColor),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                // 白と黒のどちらが読めるかの境目。単純に 0.5 で切ると、
                // 黄色や水色のように明るく見える色にも白が載って埋もれる
                tint = if (swatchColor.luminance() > CONTRAST_LUMINANCE_THRESHOLD) {
                    Color.Black
                } else {
                    Color.White
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 識別色の選択肢 1 つ分の大きさ（選択中のリングを含む） */
private val SWATCH_TOTAL_SIZE = 44.dp

/** リングと色の円のあいだの隙間 */
private val SWATCH_RING_GAP = 5.dp

/**
 * 色の上に白と黒のどちらを載せるかの境目となる相対輝度
 *
 * これより明るい色には黒を載せた方が読める（WCAG の相対輝度で、
 * 白との対比と黒との対比が入れ替わる点）。
 */
private const val CONTRAST_LUMINANCE_THRESHOLD = 0.179f

// ========================================
// 部品
// ========================================

/**
 * 接続中であることを示す色
 *
 * TopAppBar の稼働中表示と同じ緑。テーマの色にはこの意味を持つ色が無いため直接指定する。
 */
private val CONNECTED_COLOR = Color(0xFF4CAF50)

/**
 * 未許可の権限を知らせるバナー。タップで権限要求に進む
 */
@Composable
private fun PermissionBanner(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * セクション見出し。先頭以外は上に区切り線を引く
 */
@Composable
private fun SectionHeader(
    title: String,
    showDivider: Boolean = true
) {
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

/**
 * タップでダイアログを開く設定行。右端に現在値を表示する
 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    subtitleMonospace: Boolean = false
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (subtitleMonospace) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
        )
    }
}

/**
 * スイッチ付きの設定行
 */
@Composable
private fun SwitchSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

/**
 * 決まった段階から 1 つ選ぶダイアログ。選んだ時点で確定する
 *
 * @param choices 値と表示名の組
 */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    choices: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                choices.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = value == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            )
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            // 行全体で選択を受けるため、ボタン自体はクリックを受けない
                            onClick = null
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * 文字サイズに対応する表示名。段階にない値は sp で表す
 */
private fun textSizeLabel(textSize: Int): String =
    TEXT_SIZE_PRESETS.firstOrNull { it.first == textSize }?.second ?: "${textSize}sp"

/**
 * スライダー付きの設定行
 *
 * ドラッグ中は内部状態のみを更新し、指を離した時点で [onValueChangeFinished] に通知する。
 * （保存とサービスへの反映が連続で走るのを防ぐため）
 *
 * トラック上に目盛りが並ぶのを避けるため、刻み（`steps`）は設けず連続値で扱う。
 */
@Composable
private fun SliderSettingRow(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Int) -> String,
    onValueChangeFinished: (Int) -> Unit,
    enabled: Boolean = true
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            Text(
                text = valueLabel(sliderValue.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onValueChangeFinished(sliderValue.toInt()) },
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 単一の値を編集するダイアログ
 */
@Composable
private fun EditValueDialog(
    title: String,
    initialValue: String,
    label: String,
    supportingText: String,
    keyboardType: KeyboardType,
    sanitize: (String) -> String,
    isValid: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textFieldValue by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = sanitize(it) },
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(textFieldValue) },
                enabled = isValid(textFieldValue)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
