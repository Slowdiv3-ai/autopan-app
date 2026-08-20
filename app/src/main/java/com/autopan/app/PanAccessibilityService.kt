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
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isAccessibilityEnabled = false
    }
}
