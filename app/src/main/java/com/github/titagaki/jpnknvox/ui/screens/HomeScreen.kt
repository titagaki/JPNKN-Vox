package com.github.titagaki.jpnknvox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.titagaki.jpnknvox.data.MessageManager
import com.github.titagaki.jpnknvox.data.MessageLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ホーム画面 — 受信したポスト（投稿）一覧を表示
 *
 * MessageManager.messageLogs を collectAsState して描画する。
 * 最新を表示中は新着が来ても先頭に留まり、読み返している最中は
 * 位置を動かさず、先頭へ戻るボタンを出す。
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
        val scope = rememberCoroutineScope()

        // 新着が上に来るよう、受信順とは逆に並べる
        val newestFirst = messageLogs.asReversed()

        // 先頭に張り付くかどうか。新着を先頭に差し込むとリストの位置は
        // それまでの項目に固定されて先頭からずれるため、位置の値だけでは
        // 「最新を表示中」なのか「読み返し中」なのか区別できない。
        // そこでスクロール操作の結果としての位置だけを見て更新する。
        // rememberLazyListState と同じく rememberSaveable で持つ。remember だと
        // タブの切り替えや画面回転で true に戻り、読み返し中の位置を失う
        var stickToTop by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress to listState.isAtTop() }
                .collect { (scrolling, atTop) ->
                    // スクロール中は今の位置をそのまま反映する。
                    // 止まっているときは先頭に着いた場合だけ張り付きを戻す
                    // （新着の差し込みでずれた分を操作と取り違えないため）
                    if (scrolling || atTop) {
                        stickToTop = atTop
                    }
                }
        }

        // 張り付き中はアニメーションなしで先頭に引き戻す。
        // 見た目には位置が最上位のまま、コメントだけが下に流れる。
        // 件数を鍵にすると MessageManager の上限（500 件）に達したあとは
        // 常に 500 のままで発火しなくなるので、先頭のポストの id を見る
        LaunchedEffect(newestFirst.firstOrNull()?.id) {
            if (stickToTop) {
                listState.scrollToItem(0)
            }
        }

        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
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

            // バックスクロール中だけ、一覧の上端に先頭へ戻るボタンを重ねる
            AnimatedVisibility(
                visible = !stickToTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                ScrollToTopButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                            stickToTop = true
                        }
                    }
                )
            }
        }
    }
}

/** 一覧の先頭（最新のポスト）へ戻る丸ボタン */
@Composable
private fun ScrollToTopButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "最新へスクロール",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** 一覧が先頭（最新のポストが上端）にあるか */
private fun LazyListState.isAtTop(): Boolean =
    firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

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
