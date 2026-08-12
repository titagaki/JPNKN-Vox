package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.titagaki.jpnknvox.config.AppConfig

/**
 * デバッグ画面 — 連投テスト（デバッグビルドのみ表示）
 *
 * MQTT を経由せずダミーメッセージを流し込み、
 * 読み上げ間隔やキューの詰まり具合を実機で確認する。
 */
@Composable
fun DebugScreen(
    isServiceRunning: Boolean,
    onStartTestBurst: (Int, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var countText by remember { mutableStateOf(AppConfig.TestBurst.DEFAULT_COUNT.toString()) }
    var intervalText by remember { mutableStateOf(AppConfig.TestBurst.DEFAULT_INTERVAL_MS.toString()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TestBurstCard(
            countText = countText,
            onCountTextChange = { countText = it },
            intervalText = intervalText,
            onIntervalTextChange = { intervalText = it },
            isServiceRunning = isServiceRunning,
            onStartTestBurst = onStartTestBurst
        )

        SpeechIntervalCard()
    }
}

@Composable
private fun TestBurstCard(
    countText: String,
    onCountTextChange: (String) -> Unit,
    intervalText: String,
    onIntervalTextChange: (String) -> Unit,
    isServiceRunning: Boolean,
    onStartTestBurst: (Int, Long) -> Unit
) {
    val count = countText.toIntOrNull()
    val intervalMs = intervalText.toLongOrNull()
    val isValid = count != null && count > 0 && intervalMs != null && intervalMs >= 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "連投テスト",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "ダミーのコメントを指定した件数・間隔で流し込み、読み上げが追いつくかを確認します。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = countText,
                    onValueChange = { onCountTextChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("件数") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { onIntervalTextChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("送信間隔 (ms)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // よく使う組み合わせのプリセット
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PRESETS.forEach { preset ->
                    OutlinedButton(
                        onClick = {
                            onCountTextChange(preset.count.toString())
                            onIntervalTextChange(preset.intervalMs.toString())
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(preset.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Button(
                onClick = {
                    if (count != null && intervalMs != null) {
                        onStartTestBurst(count, intervalMs)
                    }
                },
                enabled = isServiceRunning && isValid,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("連投テスト開始")
            }

            if (!isServiceRunning) {
                Text(
                    text = "サービス稼働中のみ実行できます。上部のスイッチで開始してください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 現在の読み上げ間隔を確認するためのカード
 *
 * 値の変更はコード側（[AppConfig.Tts.SPEECH_INTERVAL_MS]）で行う。
 */
@Composable
private fun SpeechIntervalCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "現在の読み上げ間隔: ${AppConfig.Tts.SPEECH_INTERVAL_MS} ms",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1 件読み上げ終わってから次を読み始めるまでの待ち時間です。" +
                    "変更するには AppConfig.Tts.SPEECH_INTERVAL_MS を書き換えてビルドし直してください。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class BurstPreset(val label: String, val count: Int, val intervalMs: Long)

private val PRESETS = listOf(
    BurstPreset("軽め", 10, 2000L),
    BurstPreset("標準", 20, 500L),
    BurstPreset("激しい", 50, 100L)
)
