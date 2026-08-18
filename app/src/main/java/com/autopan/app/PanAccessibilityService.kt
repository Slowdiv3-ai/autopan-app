package com.autopan.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button

class PanAccessibilityService : AccessibilityService() {
    
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var floatingButton: Button? = null
    private var isPanning = false
    private var isDragging = false
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for toggle functionality
    }
    
    override fun onInterrupt() {
        // Not needed
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        createFloatingButton()
    }
    
    private fun createFloatingButton() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        floatingButton = floatingView?.findViewById(R.id.floatingToggleButton)
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager?.addView(floatingView, params)
        
        floatingButton?.setOnClickListener {
            if (!isDragging) {
                togglePanning()
            }
        }
        
        floatingButton?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var moved = false
            
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true
                            moved = true
                        }
                        
                        if (isDragging) {
                            params.x = initialX + deltaX.toInt()
                            params.y = initialY + deltaY.toInt()
                            windowManager?.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved && isDragging) {
                            floatingButton?.postDelayed({ isDragging = false }, 200)
                        } else {
                            isDragging = false
                            floatingButton?.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }
    
    private fun togglePanning() {
        isPanning = !isPanning
        
        if (isPanning) {
            floatingButton?.setBackgroundColor(Color.GREEN)
            floatingButton?.text = "◉"
            
            val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
            val speed = prefs.getInt("speed", 1000)
            val pattern = prefs.getString("pattern", "smooth") ?: "smooth"
            val smoothAll = prefs.getBoolean("smooth_all", true)
            val customPattern = prefs.getString("custom_pattern", "-0.5,-0.25,0.0,0.25,0.5,0.25,0.0,-0.25")
            
            startPanningProcess(speed, pattern, smoothAll, customPattern)
        } else {
            floatingButton?.setBackgroundColor(Color.RED)
            floatingButton?.text = "○"
            stopPanningProcess()
        }
    }
    
    private fun startPanningProcess(speed: Int, pattern: String, smoothAll: Boolean, customPattern: String?) {
        Thread {
            val values = when (pattern) {
                "smooth" -> listOf("-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4", "0.6", "0.8", "0.6", "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6")
                "hard" -> listOf("-0.8", "0.0", "0.8", "0.0")
                "wave" -> listOf("-0.8", "-0.4", "0.0", "0.4", "0.8", "0.4", "0.0", "-0.4")
                "subtle" -> listOf("-0.3", "-0.15", "0.0", "0.15", "0.3", "0.15", "0.0", "-0.15")
                "crazy" -> listOf("-0.8", "0.8", "-0.4", "0.4", "-0.8", "0.8", "-0.2", "0.2", "0.0")
                "custom" -> customPattern?.split(",") ?: listOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25")
                else -> listOf("-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4", "0.6", "0.8", "0.6", "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6")
            }
            
            try {
                while (isPanning) {
                    for (i in values.indices) {
                        if (!isPanning) break
                        
                        val current = values[i].toFloat()
                        val next = values[(i + 1) % values.size].toFloat()
                        val steps = if (smoothAll) 5 else 1
                        
                        for (s in 1..steps) {
                            if (!isPanning) break
                            
                            val interpolated = if (smoothAll) {
                                current + (next - current) * (s.toFloat() / steps.toFloat())
                            } else {
                                current
                            }
                            
                            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system master_balance ${String.format("%.2f", interpolated)}"))
                            process.waitFor()
                            
                            Thread.sleep((speed / steps).toLong())
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    private fun stopPanningProcess() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system master_balance 0.0"))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isPanning = false
        stopPanningProcess()
        if (floatingView != null) windowManager?.removeView(floatingView)
    }
}
