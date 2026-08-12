package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.titagaki.jpnknvox.data.MessageManager
import com.github.titagaki.jpnknvox.data.MessageLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面 — 受信したポスト（投稿）一覧を表示
 *
 * MessageManager.messageLogs を collectAsState して描画し、
 * 新着メッセージ受信時に自動スクロールする。
 */
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier
) {
    val messageLogs by MessageManager.messageLogs.collectAsState()

    if (messageLogs.isEmpty()) {
        // 投稿がない場合のプレースホルダー
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "📭",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "受信した投稿はまだありません",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "サービスを開始すると、新着投稿がここに表示されます",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        val listState = rememberLazyListState()

        // 新着が上に来るよう、受信順とは逆に並べる
        val newestFirst = messageLogs.asReversed()

        // ユーザーが先頭にいるかどうかを判定。読み返している最中に
        // 先頭へ引き戻さないため、新着で自動スクロールするのは先頭にいるときだけにする
        val isAtTop by remember {
            derivedStateOf { listState.firstVisibleItemIndex == 0 }
        }

        LaunchedEffect(newestFirst.size) {
            if (isAtTop) {
                listState.animateScrollToItem(0)
            }
        }

        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = newestFirst,
                key = { it.id }
            ) { log ->
                PostCard(log = log)
            }
        }
    }
}

@Composable
private fun PostCard(log: MessageLog) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 上段: レス番号 + 名前
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 取得先は色だけで示す。どこから来たかは見分けられればよく、
                // ID を並べても読む情報が増えるわけではないため
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(log.sourceColor))
                )
                if (log.no.isNotBlank()) {
                    Text(
                        text = "[${log.no}]",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = log.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 中段: 本文
            Text(
                text = log.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 下段右: タイムスタンプ (HH:mm:ss)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = formatTimestamp(log.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * ミリ秒タイムスタンプを HH:mm:ss 形式に変換
 */
private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}
