package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.github.titagaki.jpnknvox.config.AppConfig

/**
 * 設定画面
 *
 * `docs/references/jpnkn-vox-settings.html` のモックアップに沿った
 * セクション見出し＋行リスト形式のレイアウト。
 */
@Composable
fun SettingsScreen(
    boardId: String,
    onBoardIdChange: (String) -> Unit,
    isServiceRunning: Boolean,
    hasNotificationPermission: Boolean,
    hasOverlayPermission: Boolean,
    isOverlayEnabled: Boolean,
    onOverlayEnabledChange: (Boolean) -> Unit,
    overlayAlpha: Int,
    onOverlayAlphaChange: (Int) -> Unit,
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
    var showBoardIdDialog by remember { mutableStateOf(false) }
    var showMaxLengthDialog by remember { mutableStateOf(false) }

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

        // ========== 接続 ==========
        SectionHeader(title = "接続", showDivider = false)
        SettingRow(
            title = "板 ID",
            subtitle = when {
                isServiceRunning -> "サービス稼働中は変更できません"
                boardId.isBlank() -> "タップして読み上げる板を設定します"
                else -> AppConfig.Mqtt.createTopic(boardId)
            },
            subtitleMonospace = !isServiceRunning && boardId.isNotBlank(),
            value = boardId.ifBlank { "未設定" },
            enabled = !isServiceRunning,
            onClick = { showBoardIdDialog = true }
        )

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

    if (showBoardIdDialog) {
        EditValueDialog(
            title = "板 ID",
            initialValue = boardId,
            label = "板 ID",
            supportingText = "読み上げる板の ID を入力してください (例: mamiko)",
            keyboardType = KeyboardType.Text,
            sanitize = { input -> input.filter { it.isLetterOrDigit() || it == '_' } },
            isValid = { it.isNotBlank() },
            onDismiss = { showBoardIdDialog = false },
            onConfirm = {
                onBoardIdChange(it)
                showBoardIdDialog = false
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
}

// ========================================
// 部品
// ========================================

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
