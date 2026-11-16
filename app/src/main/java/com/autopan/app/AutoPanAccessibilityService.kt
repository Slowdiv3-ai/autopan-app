package com.autopan.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AutoPanAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
