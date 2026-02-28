package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.titagaki.jpnknvox.MessageManager

/**
 * ログ画面 — システムログを表示
 *
 * MessageManager.systemLogs を collectAsState して描画する。
 */
@Composable
fun LogScreen(
    logMessages: List<String>,
    modifier: Modifier = Modifier
) {
    // MessageManager からも取得し、両方をマージ
    val systemLogs by MessageManager.systemLogs.collectAsState()
    val allLogs = if (systemLogs.isNotEmpty()) systemLogs else logMessages

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(
                color = Color(0xFF1E1E1E),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        val listState = rememberLazyListState()

        // 自動スクロール: 新しいログが追加されたら末尾へ
        LaunchedEffect(allLogs.size) {
            if (allLogs.isNotEmpty()) {
                listState.animateScrollToItem(maxOf(0, allLogs.size - 1))
            }
        }

        if (allLogs.isEmpty()) {
            Text(
                text = "ログはまだありません",
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(allLogs) { message ->
                    Text(
                        text = message,
                        color = Color(0xFF00FF00),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
