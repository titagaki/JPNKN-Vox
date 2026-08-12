package com.github.titagaki.jpnknvox.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.TextView
import com.github.titagaki.jpnknvox.config.AppConfig
import kotlin.math.ceil

/**
 * 黒い縁取りと影を付けて太字で描画する TextView
 *
 * 配信映像の上に重ねるため、下地の明暗に関わらず文字が読める必要がある。
 * 縁取りだけでは明るい背景で輪郭が背景に溶けるので、影で下地との段差も作る。
 *
 * 縁取り幅と影の大きさは文字サイズに対する割合（[AppConfig.Overlay]）で決めるため、
 * 文字サイズを変えても見た目の比率は崩れない。
 */
class OutlinedTextView(context: Context) : TextView(context) {

    /** 縁取りの幅 */
    private val outlineWidth: Float
        get() = textSize * AppConfig.Overlay.TEXT_STROKE_RATIO

    /** 影のぼかし半径（TextView 標準の影とは別物なので名前を分けている） */
    private val outlineShadowRadius: Float
        get() = textSize * AppConfig.Overlay.TEXT_SHADOW_RADIUS_RATIO

    /** 影を下にずらす量 */
    private val outlineShadowDy: Float
        get() = textSize * AppConfig.Overlay.TEXT_SHADOW_DY_RATIO

    init {
        setTypeface(typeface, Typeface.BOLD)
        updateOutlinePadding()
    }

    override fun setTextSize(unit: Int, size: Float) {
        super.setTextSize(unit, size)
        // 縁取りと影の大きさが文字サイズに追従するため、余白も取り直す
        updateOutlinePadding()
    }

    override fun onDraw(canvas: Canvas) {
        // レイアウト未確定時は通常描画に任せる
        val textLayout = layout ?: run {
            super.onDraw(canvas)
            return
        }

        val textPaint = paint
        val fillColor = currentTextColor

        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())

        // 1 パス目: 影を落としながら輪郭をなぞる
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = outlineWidth
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.color = AppConfig.Overlay.TEXT_STROKE_COLOR
        textPaint.setShadowLayer(
            outlineShadowRadius,
            0f,
            outlineShadowDy,
            AppConfig.Overlay.TEXT_SHADOW_COLOR
        )
        textLayout.draw(canvas)

        // 2 パス目: 本来の文字を重ねる（影は 1 パス目で描き終えている）
        textPaint.clearShadowLayer()
        textPaint.style = Paint.Style.FILL
        textPaint.color = fillColor
        textLayout.draw(canvas)

        canvas.restore()
    }

    /**
     * 縁取りと影がビューの端で切れないよう、文字サイズに応じた余白を確保する
     *
     * 縁取りは文字の輪郭から外側に半分はみ出し、影はさらに下へ広がる。
     */
    private fun updateOutlinePadding() {
        val outset = ceil(outlineWidth / 2f).toInt()
        val bottom = ceil(outlineWidth / 2f + outlineShadowRadius + outlineShadowDy).toInt()
        setPadding(outset, outset, outset, bottom)
    }
}
