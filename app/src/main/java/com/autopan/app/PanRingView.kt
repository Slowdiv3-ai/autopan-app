package com.autopan.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.*

class PanRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCustomPatternChangedListener {
        fun onPatternChanged(angles: List<Float>)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Ring Settings
    private var numDots = 15 // More dots for a smoother wave
    private var startAngle = 160f
    private var sweepAngle = 220f
    private var dotRadius = 40f
    private var ringRadius = 300f

    // Animation Settings
    private var animator: ValueAnimator? = null
    private var animProgress = 0f 
    private var isAnimating = false

    // Custom Mode
    var isCustomMode = false
    val customDotAngles = mutableListOf<Float>()
    var patternChangeListener: OnCustomPatternChangedListener? = null

    // Colors
    private val activeColor = Color.parseColor("#FFA07A")
    private val inactiveColor = Color.parseColor("#F0F0F0") // Faint grey for inactive dots
    private val glowColor = Color.parseColor("#FF6347")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ringRadius = (minOf(w, h) * 0.35f)
        dotRadius = ringRadius * 0.12f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        // Shift down slightly to frame the center image
        val centerY = height / 2f + (ringRadius * 0.15f) 

        if (isCustomMode) {
            drawCustomEditor(canvas, centerX, centerY)
        } else {
            drawWaveRing(canvas, centerX, centerY)
        }
    }

    private fun drawWaveRing(canvas: Canvas, cx: Float, cy: Float) {
        val angleStep = sweepAngle / (numDots - 1)
        
        for (i in 0 until numDots) {
            val angle = startAngle + (i * angleStep)
            val rad = Math.toRadians(angle.toDouble())
            
            // Base position
            val x = cx + ringRadius * cos(rad).toFloat()
            val y = cy + ringRadius * sin(rad).toFloat()

            // Calculate wave effect based on animation progress and dot index
            // This creates a sine wave that moves across the dots
            val waveValue = sin((i.toFloat() / numDots * 2 * PI) - (animProgress * 2 * PI)).toFloat()
            
            // Scale dot size and color based on wave
            val scale = 1f + (waveValue * 0.4f) // Dots grow and shrink
            val alpha = 100 + ((waveValue + 1) / 2 * 155).toInt() // Fade in and out

            if (isAnimating) {
                paint.color = activeColor
                paint.alpha = alpha
                paint.setShadowLayer(15f * scale, 0f, 0f, glowColor)
                canvas.drawCircle(x, y, dotRadius * scale, paint)
                paint.clearShadowLayer()
            } else {
                // Static state when not panning
                paint.color = inactiveColor
                paint.alpha = 255
                canvas.drawCircle(x, y, dotRadius * 0.8f, paint)
            }
        }
    }

    private fun drawCustomEditor(canvas: Canvas, cx: Float, cy: Float) {
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dotRadius * 1.5f
        paint.strokeCap = Paint.Cap.ROUND
        val rect = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        canvas.drawArc(rect, startAngle, sweepAngle, false, paint)
        paint.style = Paint.Style.FILL

        for (angle in customDotAngles) {
            val canvasAngle = angle + startAngle
            val rad = Math.toRadians(canvasAngle.toDouble())
            val x = cx + ringRadius * cos(rad).toFloat()
            val y = cy + ringRadius * sin(rad).toFloat()
            paint.color = activeColor
            paint.alpha = 255
            canvas.drawCircle(x, y, dotRadius, paint)
        }
    }

    fun startPanAnimation() {
        if (isAnimating) return
        isAnimating = true
        
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L // Speed of the wave
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            
            addUpdateListener {
                animProgress = it.animatedValue as Float
                invalidate()
            }
        }
        animator?.start()
    }

    fun stopPanAnimation() {
        isAnimating = false
        animator?.cancel()
        animProgress = 0f
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && isCustomMode) {
            val cx = width / 2f
            val cy = height / 2f + (ringRadius * 0.15f)
            val x = event.x
            val y = event.y
            val distance = sqrt((x - cx).pow(2) + (y - cy).pow(2))
            
            if (abs(distance - ringRadius) < dotRadius * 2) {
                var angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                if (angle < 0) angle += 360
                
                // We only care about the top half (160 to 380)
                if (angle in 160f..380f) {
                    val mappedAngle = angle - 160f
                    val existingIndex = customDotAngles.indexOfFirst { abs(it - mappedAngle) < 15f }
                    if (existingIndex != -1) {
                        customDotAngles.removeAt(existingIndex)
                    } else {
                        customDotAngles.add(mappedAngle)
                        customDotAngles.sort()
                    }
                    invalidate()
                    patternChangeListener?.onPatternChanged(customDotAngles)
                }
            }
        }
        return true
    }
}
