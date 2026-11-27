package com.djape1.player

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

/** Μικρός κινούμενος equalizer (5 μπάρες) που «χορεύει» όσο παίζει ο σταθμός. */
class EqView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var phase = 0f
    private var c1 = 0xFF9B5CFF.toInt()
    private var c2 = 0xFFC08BFF.toInt()
    private val anim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    private var active = false

    fun setColors(top: Int, bottom: Int) { c1 = top; c2 = bottom; paint.shader = null; invalidate() }

    fun setActive(on: Boolean) {
        active = on
        if (on && isAttachedToWindow) { if (!anim.isRunning) anim.start() } else { anim.cancel(); invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (active && !anim.isRunning) anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        if (paint.shader == null) paint.shader = LinearGradient(0f, 0f, 0f, h, c1, c2, Shader.TileMode.CLAMP)
        val n = 5; val gap = w * 0.08f; val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val f = if (active) 0.25f + 0.75f * abs(sin(phase + i * 1.3f)) else 0.22f
            val bh = h * f
            val x = i * (bw + gap)
            rect.set(x, h - bh, x + bw, h)
            canvas.drawRoundRect(rect, bw / 2, bw / 2, paint)
        }
    }
}
