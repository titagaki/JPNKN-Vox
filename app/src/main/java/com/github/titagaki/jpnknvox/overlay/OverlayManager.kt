package com.github.titagaki.jpnknvox.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.github.titagaki.jpnknvox.R
import com.github.titagaki.jpnknvox.config.AppConfig

/**
 * オーバーレイウィンドウ管理クラス
 *
 * - オーバーレイの作成と削除
 * - ドラッグ移動の処理
 * - ステータスとメッセージの表示更新
 */
class OverlayManager(private val context: Context) {

    companion object {
        private const val TAG = "OverlayManager"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var statusTextView: TextView? = null
    private var messageTextView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // ドラッグ用の変数
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
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
     * @return 作成に成功した場合 true
     */
    fun create(): Boolean {
        if (!hasOverlayPermission()) {
            Log.w(TAG, "Overlay permission not granted")
            return false
        }

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            // オーバーレイビューを作成
            overlayView = LayoutInflater.from(context).inflate(
                android.R.layout.simple_list_item_2,
                null
            ).apply {
                setBackgroundColor(Color.argb(200, 0, 0, 0))
                setPadding(16, 8, 16, 8)
            }

            // TextView を取得
            statusTextView = overlayView?.findViewById(android.R.id.text1)
            messageTextView = overlayView?.findViewById(android.R.id.text2)

            statusTextView?.apply {
                text = "初期化中..."
                setTextColor(Color.WHITE)
                textSize = 14f
            }

            messageTextView?.apply {
                text = "起動しました"
                setTextColor(Color.LTGRAY)
                textSize = 12f
            }

            // ウィンドウパラメータを設定
            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
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
     */
    @Suppress("ClickableViewAccessibility")
    private fun setupTouchListener() {
        overlayView?.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * ステータス表示を更新
     *
     * @param status ステータステキスト
     * @param color ステータスの色
     */
    fun updateStatus(status: String, color: Int) {
        mainHandler.post {
            val appName = context.getString(R.string.app_name)
            statusTextView?.text = "$appName: $status"
            statusTextView?.setTextColor(color)
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
     * 接続済み状態を表示
     */
    fun showConnected() {
        updateStatus("接続済み", Color.GREEN)
    }

    /**
     * 切断状態を表示
     */
    fun showDisconnected() {
        updateStatus("切断", Color.YELLOW)
    }

    /**
     * 未接続状態を表示
     */
    fun showNotConnected() {
        updateStatus("未接続", Color.RED)
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

