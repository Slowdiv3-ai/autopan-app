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

    // --- THIS WAS MISSING ---
    interface OnCustomPatternChangedListener {
        fun onPatternChanged(angles: List<Float>)
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var numDots = 10
    private var startAngle = 180f
    private var sweepAngle = 180f
    private var dotRadius = 40f
    private var ringRadius = 300f

    private var activeDotIndex = -1
    private var isAnimating = false
    private var animator: ValueAnimator? = null

    var isCustomMode = false
    val customDotAngles = mutableListOf<Float>()
    
    // --- THIS WAS MISSING ---
    var patternChangeListener: OnCustomPatternChangedListener? = null

    private val activeColor = Color.parseColor("#FFA07A")
    private val inactiveColor = Color.parseColor("#E0E0E0")
    private val glowColor = Color.parseColor("#FF6347")

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ringRadius = (minOf(w, h) * 0.35f)
        dotRadius = ringRadius * 0.12f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Force perfect center
        val centerX = width / 2f
        
        // We only push the ring down slightly so it doesn't overlap the top bar
        // If it looks too high or too low, you can adjust this 0.1f multiplier
        val centerY = height / 2f + (ringRadius * 0.15f) 

        if (isCustomMode) {
            drawCustomEditor(canvas, centerX, centerY)
        } else {
            drawSegmentedRing(canvas, centerX, centerY)
        }
    }
    private fun drawSegmentedRing(canvas: Canvas, cx: Float, cy: Float) {
        val angleStep = sweepAngle / (numDots - 1)
        for (i in 0 until numDots) {
            val angle = startAngle + (i * angleStep)
            val rad = Math.toRadians(angle.toDouble())
            val x = cx + ringRadius * cos(rad).toFloat()
            val y = cy + ringRadius * sin(rad).toFloat()

            if (i == activeDotIndex) {
                paint.color = glowColor
                paint.setShadowLayer(20f, 0f, 0f, activeColor)
                canvas.drawCircle(x, y, dotRadius * 1.2f, paint)
                paint.clearShadowLayer()
            } else if (i < activeDotIndex) {
                paint.color = activeColor
                canvas.drawCircle(x, y, dotRadius, paint)
            } else {
                paint.color = inactiveColor
                canvas.drawCircle(x, y, dotRadius, paint)
            }
        }
    }

    private fun drawCustomEditor(canvas: Canvas, cx: Float, cy: Float) {
        paint.color = Color.parseColor("#22000000")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dotRadius * 1.5f
        paint.strokeCap = Paint.Cap.ROUND
        val rect = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
        canvas.drawArc(rect, 180f, 180f, false, paint)
        paint.style = Paint.Style.FILL

        for (angle in customDotAngles) {
            val canvasAngle = angle + 180f
            val rad = Math.toRadians(canvasAngle.toDouble())
            val x = cx + ringRadius * cos(rad).toFloat()
            val y = cy + ringRadius * sin(rad).toFloat()
            paint.color = activeColor
            canvas.drawCircle(x, y, dotRadius, paint)
        }
    }

    fun startPanAnimation(speedMs: Long = 1000L) {
        if (isAnimating) return
        isAnimating = true
        animator = ValueAnimator.ofInt(0, numDots - 1).apply {
            duration = (numDots - 1) * speedMs
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            // CHANGE THIS LINE:
            repeatMode = ValueAnimator.REVERSE 
            
            addUpdateListener {
                activeDotIndex = it.animatedValue as Int
                invalidate()
            }
        }
        animator?.start()
    }
    fun stopPanAnimation() {
        isAnimating = false
        animator?.cancel()
        activeDotIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && isCustomMode) {
            val cx = width / 2f
            val cy = height / 2f + (ringRadius * 0.1f) // Match onDraw
            // ... rest of the code
            val x = event.x
            val y = event.y
            val distance = sqrt((x - cx).pow(2) + (y - cy).pow(2))
            
            if (abs(distance - ringRadius) < dotRadius * 2) {
                var angle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                if (angle < 0) angle += 360
                
                if (angle in 180f..360f) {
                    val mappedAngle = angle - 180f
                    val existingIndex = customDotAngles.indexOfFirst { abs(it - mappedAngle) < 15f }
                    if (existingIndex != -1) {
                        customDotAngles.removeAt(existingIndex)
                    } else {
                        customDotAngles.add(mappedAngle)
                        customDotAngles.sort()
                    }
                    invalidate()
                    
                    // --- THIS WAS MISSING ---
                    patternChangeListener?.onPatternChanged(customDotAngles)
                }
            }
        }
        return true
    }
}
