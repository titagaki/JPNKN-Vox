package com.github.titagaki.jpnknvox.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.github.titagaki.jpnknvox.R
import com.github.titagaki.jpnknvox.config.AppConfig
import kotlin.math.roundToInt

/**
 * オーバーレイウィンドウ管理クラス
 *
 * - オーバーレイの作成と削除
 * - ドラッグ移動の処理
 * - ステータスとメッセージの表示更新
 *
 * 接続状態は 1 行目のアプリ名の色で示し、対処が要る状態のときだけ
 * 同じ行に理由を足す（[ConnectionStatus.showLabel]）。
 */
class OverlayManager(private val context: Context) {

    /**
     * 接続状態
     *
     * 正常（[CONNECTED]）は 99% の時間そうであって変化しない情報なので、
     * 色を落ち着かせて文言も出さない。目立たせるのは対処が要る状態だけにする。
     */
    enum class ConnectionStatus(val label: String, val color: Int, val showLabel: Boolean) {
        /** サービス開始直後。まだ接続を試みていない */
        WAITING("待機中", 0xFF9E9E9E.toInt(), showLabel = false),
        CONNECTED("接続済み", 0xFF66BB6A.toInt(), showLabel = false),
        DISCONNECTED("切断 — 再接続中", 0xFFFFD54F.toInt(), showLabel = true),
        NOT_CONNECTED("未接続", 0xFFFF5252.toInt(), showLabel = true)
    }

    companion object {
        private const val TAG = "OverlayManager"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusTextView: TextView? = null
    private var messageTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // ドラッグ用の変数（上下方向のみ）
    private var initialY = 0
    private var initialTouchY = 0f

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * オーバーレイ権限があるか確認
     */
    fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * オーバーレイウィンドウを作成
     *
     * @param alpha 背景の濃さ（0〜100 の整数 %）
     * @param textSizeSp メッセージの文字サイズ（sp）
     * @return 作成に成功した場合 true
     */
    fun create(
        alpha: Int = AppConfig.Overlay.DEFAULT_ALPHA,
        textSizeSp: Int = AppConfig.Overlay.DEFAULT_TEXT_SIZE
    ): Boolean {
        if (!hasOverlayPermission()) {
            Log.w(TAG, "Overlay permission not granted")
            return false
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // 縁取り付きの TextView を 2 段に並べる
            statusTextView = OutlinedTextView(context).apply {
                textSize = statusTextSizeOf(textSizeSp)
                // フォント由来の上下の余白を落として、コメントとの間を詰める
                includeFontPadding = false
            }

            // 高さが受信内容で伸び縮みしないよう、行数を固定する
            messageTextView = OutlinedTextView(context).apply {
                text = "サービス稼働中"
                setTextColor(AppConfig.Overlay.MESSAGE_TEXT_COLOR)
                textSize = textSizeSp.toFloat()
                minLines = AppConfig.Overlay.MESSAGE_LINES
                maxLines = AppConfig.Overlay.MESSAGE_LINES
                ellipsize = TextUtils.TruncateAt.END
            }

            // オーバーレイビューを作成
            overlayView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.argb(alpha * 255 / 100, 0, 0, 0))
                val paddingHorizontal = dpToPx(AppConfig.Overlay.PADDING_HORIZONTAL_DP)
                val paddingVertical = dpToPx(AppConfig.Overlay.PADDING_VERTICAL_DP)
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                addView(statusTextView, matchWidthLayoutParams())
                addView(messageTextView, matchWidthLayoutParams())
            }

            applyOutlinePaddingOffsets()

            applyStatus(ConnectionStatus.WAITING)

            // ウィンドウパラメータを設定
            // 幅は画面いっぱい固定。メッセージの長さで横幅が伸び縮みしないようにする
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = AppConfig.Overlay.INITIAL_Y_POSITION
            }

            // タッチイベントを設定（ドラッグ可能にする）
            setupTouchListener()

            // ビューを追加
            windowManager?.addView(overlayView, layoutParams)
            Log.d(TAG, "Overlay window created")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create overlay window", e)
            return false
        }
    }

    /**
     * タッチリスナーを設定（ドラッグ移動用）
     *
     * 幅が画面いっぱいのため、横に動かすと端に隙間ができるだけになる。
     * よって上下方向のみ移動できるようにする。
     */
    @Suppress("ClickableViewAccessibility")
    private fun setupTouchListener() {
        overlayView?.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = params.y
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * メッセージの文字サイズから、アプリ名（接続状態）の文字サイズを求める
     */
    private fun statusTextSizeOf(messageTextSizeSp: Int): Float =
        messageTextSizeSp * AppConfig.Overlay.STATUS_TEXT_SIZE_RATIO

    /**
     * 横幅いっぱいに広げる LayoutParams
     */
    private fun matchWidthLayoutParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    /**
     * 縁取り・影のぶんだけ TextView に付いている余白を、負のマージンで相殺する
     *
     * この余白がないと縁取りと影が切れるが、そのままでは
     * 行間が空き、上下の見た目の余白も [AppConfig.Overlay.PADDING_VERTICAL_DP] と食い違う。
     * 相殺してオーバーレイ自身の padding だけが余白として効くようにする。
     *
     * 余白は文字サイズに追従して変わるため、サイズを変えるたびに取り直す。
     */
    private fun applyOutlinePaddingOffsets() {
        statusTextView?.let { status ->
            val params = status.layoutParams as? LinearLayout.LayoutParams ?: return@let
            params.topMargin = -status.paddingTop
            status.layoutParams = params
        }
        messageTextView?.let { message ->
            val params = message.layoutParams as? LinearLayout.LayoutParams ?: return@let
            params.topMargin = -(statusTextView?.paddingBottom ?: 0)
            params.bottomMargin = -message.paddingBottom
            message.layoutParams = params
        }
    }

    /**
     * dp を画面密度に応じた px に変換する
     */
    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).roundToInt()
    }

    /**
     * 接続状態をアプリ名の色に反映する（メインスレッドから呼ぶこと）
     *
     * 正常時はアプリ名だけ。対処が要る状態のときは同じ行に理由を足す。
     */
    private fun applyStatus(status: ConnectionStatus) {
        val appName = context.getString(R.string.app_name)

        statusTextView?.apply {
            text = if (status.showLabel) "$appName — ${status.label}" else appName
            setTextColor(status.color)
        }
    }

    /**
     * メッセージ表示を更新
     *
     * @param message メッセージテキスト
     */
    fun updateMessage(message: String) {
        val displayText = if (message.length > AppConfig.Overlay.MAX_MESSAGE_LENGTH) {
            message.substring(0, AppConfig.Overlay.MAX_MESSAGE_LENGTH) + "..."
        } else {
            message
        }

        mainHandler.post {
            messageTextView?.text = displayText
        }
    }

    /**
     * 接続状態を表示
     */
    fun showStatus(status: ConnectionStatus) {
        mainHandler.post { applyStatus(status) }
    }

    /**
     * オーバーレイ背景の濃さを更新
     *
     * @param alpha 0〜100 の整数（%）
     */
    fun updateAlpha(alpha: Int) {
        mainHandler.post {
            overlayView?.setBackgroundColor(Color.argb(alpha * 255 / 100, 0, 0, 0))
        }
    }

    /**
     * オーバーレイの文字サイズを更新
     *
     * @param textSizeSp メッセージの文字サイズ（sp）
     */
    fun updateTextSize(textSizeSp: Int) {
        mainHandler.post {
            statusTextView?.textSize = statusTextSizeOf(textSizeSp)
            messageTextView?.textSize = textSizeSp.toFloat()
            applyOutlinePaddingOffsets()
        }
    }

    /**
     * オーバーレイウィンドウを削除
     */
    fun remove() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
                layoutParams = null
                Log.d(TAG, "Overlay window removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay window", e)
        }
    }
}

