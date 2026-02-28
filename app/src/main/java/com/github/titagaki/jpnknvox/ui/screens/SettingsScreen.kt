package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 設定画面 — 板 ID 設定と権限状態
 */
@Composable
fun SettingsScreen(
    boardId: String,
    onBoardIdChange: (String) -> Unit,
    isServiceRunning: Boolean,
    hasNotificationPermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 板 ID 設定セクション
        BoardIdSettingCard(
            boardId = boardId,
            onBoardIdChange = onBoardIdChange,
            isServiceRunning = isServiceRunning
        )

        // 権限状態セクション
        PermissionStatusCard(
            hasNotificationPermission = hasNotificationPermission,
            hasOverlayPermission = hasOverlayPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestOverlayPermission = onRequestOverlayPermission
        )
    }
}

@Composable
private fun BoardIdSettingCard(
    boardId: String,
    onBoardIdChange: (String) -> Unit,
    isServiceRunning: Boolean
) {
    var textFieldValue by remember(boardId) { mutableStateOf(boardId) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "板 ID 設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "読み上げる板の ID を入力してください (例: mamiko, sumire)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isLetterOrDigit() || it == '_' }
                    textFieldValue = filtered
                },
                label = { Text("板 ID") },
                placeholder = { Text("mamiko") },
                singleLine = true,
                enabled = !isServiceRunning,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    if (isServiceRunning) {
                        Text(
                            text = "サービス稼働中は変更できません",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text("トピック: bbs/$textFieldValue")
                    }
                }
            )

            Button(
                onClick = { onBoardIdChange(textFieldValue) },
                enabled = !isServiceRunning && textFieldValue.isNotBlank() && textFieldValue != boardId,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    hasNotificationPermission: Boolean,
    hasOverlayPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "権限状態",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            PermissionItem(
                permissionName = "通知権限",
                isGranted = hasNotificationPermission,
                onRequestPermission = onRequestNotificationPermission
            )

            HorizontalDivider()

            PermissionItem(
                permissionName = "オーバーレイ権限",
                isGranted = hasOverlayPermission,
                onRequestPermission = onRequestOverlayPermission
            )
        }
    }
}

@Composable
private fun PermissionItem(
    permissionName: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) Color.Green else Color.Red,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = permissionName,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (!isGranted) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("設定", fontSize = 12.sp)
            }
        }
    }
}

