package com.github.titagaki.jpnknvox.source

import com.github.titagaki.jpnknvox.config.AppConfig
import com.github.titagaki.jpnknvox.data.SourceType
import com.github.titagaki.jpnknvox.twicas.TwicasClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 取得先の接続テストの結果
 */
sealed interface SourceTestResult {
    data class Success(val message: String) : SourceTestResult
    data class Failure(val message: String) : SourceTestResult
}

/**
 * 取得先を登録する前に、ID が正しいかを確かめる
 *
 * 実際にコメントを受け取るのとは別の、軽い確認だけを行う:
 * - jpnkn: 板の URL が引けるか
 * - ツイキャス: ユーザーが存在するか、いま配信中か
 */
class SourceTester(
    private val twicasClient: TwicasClient = TwicasClient(),
    private val httpClient: OkHttpClient = TwicasClient.sharedHttpClient
) {

    suspend fun test(type: SourceType, sourceId: String): SourceTestResult =
        withContext(Dispatchers.IO) {
            if (sourceId.isBlank()) {
                return@withContext SourceTestResult.Failure("ID を入力してください")
            }

            when (type) {
                SourceType.JPNKN -> testJpnkn(sourceId)
                SourceType.TWICAS -> testTwicas(sourceId)
            }
        }

    /**
     * 板が実在するかを確認する
     *
     * MQTT はトピックの購読に成功しても、その板が実在するかまでは分からない。
     * 板の URL が引けるかどうかで確かめる。
     */
    private fun testJpnkn(boardId: String): SourceTestResult {
        val request = Request.Builder()
            .url("${AppConfig.Jpnkn.BOARD_BASE_URL}$boardId/")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> SourceTestResult.Success("板を確認しました（新着レスを待ちます）")
                    response.code == 404 -> SourceTestResult.Failure("板が見つかりません")
                    else -> SourceTestResult.Failure("板を確認できませんでした (${response.code})")
                }
            }
        } catch (e: IOException) {
            SourceTestResult.Failure("接続に失敗しました (${e.message})")
        }
    }

    private fun testTwicas(userId: String): SourceTestResult {
        return try {
            val movie = twicasClient.fetchMovie(userId)
            when {
                movie == null -> SourceTestResult.Failure("ユーザーが見つかりません")
                movie.isLive -> SourceTestResult.Success("配信中です。コメントを読み上げられます")
                else -> SourceTestResult.Success("ユーザーを確認しました（配信の開始を待ちます）")
            }
        } catch (e: IOException) {
            SourceTestResult.Failure("接続に失敗しました (${e.message})")
        }
    }
}
