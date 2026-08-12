package com.github.titagaki.jpnknvox.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.titagaki.jpnknvox.BuildConfig

/**
 * ボトムナビゲーションの画面定義
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "ホーム",
        icon = Icons.Default.Home
    )

    data object Log : Screen(
        route = "log",
        title = "ログ",
        icon = Icons.AutoMirrored.Filled.List
    )

    data object Settings : Screen(
        route = "settings",
        title = "設定",
        icon = Icons.Default.Settings
    )

    /** デバッグ画面（連投テスト）。デバッグビルドでのみタブに表示される */
    data object Debug : Screen(
        route = "debug",
        title = "デバッグ",
        icon = Icons.Default.BugReport
    )

    companion object {
        val items: List<Screen> = buildList {
            add(Home)
            add(Log)
            add(Settings)
            if (BuildConfig.DEBUG) add(Debug)
        }
    }
}

