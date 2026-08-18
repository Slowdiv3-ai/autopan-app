package com.autopan.app

import android.content.Context
import java.io.File

object PanService {
    var isRunning = false
    private var panProcess: Process? = null
    
    fun start(context: Context) {
        if (isRunning) return
        
        val prefs = context.getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        val speed = prefs.getInt("speed", 1000)
        val pattern = prefs.getString("pattern", "smooth") ?: "smooth"
        val smoothAll = prefs.getBoolean("smooth_all", true)
        val customPattern = prefs.getString("custom_pattern", "-0.5,-0.25,0.0,0.25,0.5,0.25,0.0,-0.25")
        
        isRunning = true
        
        Thread {
            try {
                // Build the command directly instead of script file
                val values = getPatternValues(pattern, customPattern)
                
                // Simple shell command with loop
                val command = buildPanCommand(values, speed, smoothAll)
                
                panProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                panProcess?.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            isRunning = false
        }.start()
    }
    
    fun stop() {
        isRunning = false
        panProcess?.destroy()
        
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system master_balance 0.0"))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    private fun getPatternValues(pattern: String, customPattern: String?): List<String> {
        return when (pattern) {
            "smooth" -> listOf("-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4", "0.6", "0.8", "0.6", "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6")
            "hard" -> listOf("-0.8", "0.0", "0.8", "0.0")
            "wave" -> listOf("-0.8", "-0.4", "0.0", "0.4", "0.8", "0.4", "0.0", "-0.4")
            "subtle" -> listOf("-0.3", "-0.15", "0.0", "0.15", "0.3", "0.15", "0.0", "-0.15")
            "crazy" -> listOf("-0.8", "0.8", "-0.4", "0.4", "-0.8", "0.8", "-0.2", "0.2", "0.0")
            "custom" -> customPattern?.split(",") ?: listOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25")
            else -> listOf("-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4", "0.6", "0.8", "0.6", "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6")
        }
    }
    
    private fun buildPanCommand(values: List<String>, speed: Int, smoothAll: Boolean): String {
        val sleepTime = speed / 1000.0
        
        val sb = StringBuilder()
        sb.append("while true; do")
        
        if (smoothAll) {
            // Smooth interpolation
            for (i in values.indices) {
                val current = values[i].toFloat()
                val next = values[(i + 1) % values.size].toFloat()
                val steps = 5
                
                for (s in 1..steps) {
                    val interpolated = current + (next - current) * (s.toFloat() / steps.toFloat())
                    sb.append(" settings put system master_balance ${String.format("%.2f", interpolated)};")
                    sb.append(" sleep ${String.format("%.2f", sleepTime / steps)};")
                }
            }
        } else {
            // Raw jumps
            for (value in values) {
                sb.append(" settings put system master_balance $value;")
                sb.append(" sleep ${String.format("%.2f", sleepTime)};")
            }
        }
        
        sb.append(" done")
        return sb.toString()
    }
}
