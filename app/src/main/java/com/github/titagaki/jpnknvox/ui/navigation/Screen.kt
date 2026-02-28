package com.github.titagaki.jpnknvox.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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

    companion object {
        val items = listOf(Home, Log, Settings)
    }
}

