package com.example.smilesphere

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt

class HotspotOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // UPDATED: added colorHex — optional per-dot color (used by lessons 4 & 5
    // to color-code topics like cavity/plaque/gum disease). Null = default blue.
    data class ScreenDot(
        val label: String,
        val x: Float,
        val y: Float,
        val colorHex: String? = null
    )

    private var dots: List<ScreenDot> = emptyList()

    val currentDots: List<ScreenDot>
        get() = dots

    private var expectedLabel: String? = null

    private val dotPaint = Paint().apply {
        color = Color.parseColor("#4FC3F7")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val ringPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val highlightDotPaint = Paint().apply {
        color = Color.parseColor("#FFD700")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val highlightRingPaint = Paint().apply {
        color = Color.parseColor("#FF8F00")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val dotRadiusPx = 28f
    private val highlightRadiusPx = 34f

    fun updateDots(newDots: List<ScreenDot>, expectedLabel: String? = null) {
        dots = newDots
        this.expectedLabel = expectedLabel
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        dots.forEach { dot ->
            if (dot.label == expectedLabel) {
                canvas.drawCircle(dot.x, dot.y, highlightRadiusPx, highlightDotPaint)
                canvas.drawCircle(dot.x, dot.y, highlightRadiusPx, highlightRingPaint)
            } else if (dot.colorHex != null) {
                // NEW: use the hotspot's own color if provided (lesson 4 & 5)
                val customPaint = Paint().apply {
                    color = Color.parseColor(dot.colorHex)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(dot.x, dot.y, dotRadiusPx, customPaint)
                canvas.drawCircle(dot.x, dot.y, dotRadiusPx, ringPaint)
            } else {
                canvas.drawCircle(dot.x, dot.y, dotRadiusPx, dotPaint)
                canvas.drawCircle(dot.x, dot.y, dotRadiusPx, ringPaint)
            }
        }
    }

    fun findTappedDot(tapX: Float, tapY: Float): String? {
        return dots.firstOrNull { dot ->
            val dx = tapX - dot.x
            val dy = tapY - dot.y
            val distance = sqrt(dx * dx + dy * dy)
            val radius = if (dot.label == expectedLabel) highlightRadiusPx else dotRadiusPx
            distance <= radius * 1.5f
        }?.label
    }
}