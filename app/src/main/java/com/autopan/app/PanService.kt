package com.autopan.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.io.DataOutputStream
import java.io.File

object PanService {
    var isRunning = false
    private var panProcess: Process? = null
    private var stopRequested = false
    
    fun start(context: Context) {
        if (isRunning) return
        
        val prefs = context.getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        val speed = prefs.getInt("speed", 1000)
        val pattern = prefs.getString("pattern", "smooth") ?: "smooth"
        val smoothAll = prefs.getBoolean("smooth_all", true)
        val customPattern = prefs.getString("custom_pattern", "-0.5,-0.25,0.0,0.25,0.5,0.25,0.0,-0.25")
        
        isRunning = true
        stopRequested = false
        
        Thread {
            try {
                val script = generatePanScript(speed, pattern, smoothAll, customPattern)
                
                // Write script to file
                val scriptFile = File(context.cacheDir, "pan_script.sh")
                scriptFile.writeText(script)
                scriptFile.setExecutable(true)
                
                // Run as root
                panProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", scriptFile.absolutePath))
                
                // Wait for process to finish
                panProcess?.waitFor()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            isRunning = false
        }.start()
    }
    
    fun stop() {
        stopRequested = true
        isRunning = false
        
        // Kill the process
        panProcess?.destroy()
        
        // Reset balance
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system master_balance 0.0"))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    private fun generatePanScript(speed: Int, pattern: String, smoothAll: Boolean, customPattern: String?): String {
        val patterns = mapOf(
            "smooth" to listOf("-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4", "0.6", "0.8", "0.6", "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6"),
            "hard" to listOf("-0.8", "0.0", "0.8", "0.0"),
            "wave" to listOf("-0.8", "-0.4", "0.0", "0.4", "0.8", "0.4", "0.0", "-0.4"),
            "subtle" to listOf("-0.3", "-0.15", "0.0", "0.15", "0.3", "0.15", "0.0", "-0.15"),
            "crazy" to listOf("-0.8", "0.8", "-0.4", "0.4", "-0.8", "0.8", "-0.2", "0.2", "0.0")
        )
        
        val panPattern = when (pattern) {
            "custom" -> customPattern?.split(",") ?: listOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25")
            else -> patterns[pattern] ?: patterns["smooth"]!!
        }
        
        val sb = StringBuilder()
        sb.append("#!/system/bin/sh\n")
        sb.append("while true; do\n")
        
        if (smoothAll) {
            // Smooth version - interpolate between steps
            val steps = when {
                speed <= 500 -> 5
                speed <= 1000 -> 8
                speed <= 2000 -> 12
                else -> 16
            }
            val stepDelay = speed / steps
            
            sb.append("  for i in 0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31; do\n")
            sb.append("    idx=\$((i % ${panPattern.size}))\n")
            sb.append("    next_idx=\$(((i + 1) % ${panPattern.size}))\n")
            sb.append("    current=${panPattern.joinToString(" ")}\n")
            sb.append("    for s in 1 2 3; do\n")
            sb.append("      settings put system master_balance \$(echo \"scale=2; (\$current + \$current) / 2\" | bc)\n")
            sb.append("      sleep 0.05\n")
            sb.append("    done\n")
            sb.append("  done\n")
        } else {
            // Raw version - direct jump
            for (value in panPattern) {
                sb.append("  settings put system master_balance $value\n")
                sb.append("  sleep $((speed / 1000)).$((speed % 1000 / 100))\n")
            }
        }
        
        sb.append("done\n")
        return sb.toString()
    }
}
