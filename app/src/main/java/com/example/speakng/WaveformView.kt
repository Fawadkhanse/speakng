package com.example.speakng

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random

/**
 * WaveformView
 *
 * A custom View that displays an animated audio waveform
 * while eSpeak NG is synthesizing / playing audio.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barCount   = 32
    private val barWidth   = 8f
    private val barGap     = 4f
    private val cornerRad  = 4f

    private var animating  = false
    private var tick       = 0f

    // Bar heights (0f–1f, normalized)
    private val heights    = FloatArray(barCount) { 0.05f }
    private val targetH    = FloatArray(barCount) { 0.05f }

    private val updateRunnable = Runnable {
        tick += 0.15f
        for (i in 0 until barCount) {
            // Smooth interpolation toward target
            heights[i] += (targetH[i] - heights[i]) * 0.3f
            // Randomize target when animating
            if (animating) {
                val wave = (sin(tick + i * 0.4f) * 0.3f + 0.5f).toFloat()
                targetH[i] = wave * Random.nextFloat().coerceAtLeast(0.2f)
            } else {
                targetH[i] = 0.05f
            }
        }
        invalidate()
        if (animating || heights.any { it > 0.08f }) {
            postDelayed(updateRunnable, 60)
        }
    }

    fun startAnimation() {
        animating = true
        removeCallbacks(updateRunnable)
        post(updateRunnable)
    }

    fun stopAnimation() {
        animating = false
        // Let bars settle to zero naturally via updateRunnable
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        val totalWidth = barCount * (barWidth + barGap) - barGap
        val startX     = cx - totalWidth / 2f

        for (i in 0 until barCount) {
            val barH = (heights[i] * h * 0.9f).coerceAtLeast(4f)
            val x    = startX + i * (barWidth + barGap)
            val top  = (h - barH) / 2f
            val bot  = top + barH

            // Gradient: purple → cyan
            paint.shader = LinearGradient(
                x, top, x, bot,
                Color.parseColor("#7c3aed"),
                Color.parseColor("#00d4ff"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(x, top, x + barWidth, bot, cornerRad, cornerRad, paint)
        }
    }
}
