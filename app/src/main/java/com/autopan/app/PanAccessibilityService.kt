package com.autopan.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class PanAccessibilityService : AccessibilityService() {
    
    companion object {
        var isAccessibilityEnabled = false
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed - just keeps app alive
    }
    
    override fun onInterrupt() {
        // Not needed
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        isAccessibilityEnabled = true
        
        // Auto-start panning if it was running before
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("was_running", false)
        
        if (wasRunning) {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.putExtra("auto_start", true)
            startActivity(intent)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isAccessibilityEnabled = false
        
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system master_balance 0.0"))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
