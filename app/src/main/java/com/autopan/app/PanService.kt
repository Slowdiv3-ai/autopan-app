package com.autopan.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class PanService : Service() {
    
    companion object {
        var isRunning = false
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentPosition = 0
    private var currentSpeed = 1000
    private var currentPattern = "smooth"
    private var customPattern = arrayOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25")
    private var smoothAll = true
    
    private val patterns = mapOf(
        "smooth" to arrayOf(
            "-0.8", "-0.6", "-0.4", "-0.2", "0.0",
            "0.2", "0.4", "0.6", "0.8", "0.6",
            "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6"
        ),
        "hard" to arrayOf("-0.8", "0.0", "0.8", "0.0"),
        "wave" to arrayOf(
            "-0.8", "-0.4", "0.0", "0.4", "0.8",
            "0.4", "0.0", "-0.4"
        ),
        "subtle" to arrayOf(
            "-0.3", "-0.15", "0.0", "0.15", "0.3",
            "0.15", "0.0", "-0.15"
        ),
        "crazy" to arrayOf(
            "-0.8", "0.8", "-0.4", "0.4", "-0.8",
            "0.8", "-0.2", "0.2", "0.0"
        )
    )
    
    private val panRunnable = object : Runnable {
        private var currentBalance = 0.0f
        private var targetBalance = 0.0f
        private var smoothingSteps = 10
        private var stepCount = 0
        
        override fun run() {
            if (isRunning) {
                if (smoothAll && stepCount < smoothingSteps && stepCount > 0) {
                    currentBalance += (targetBalance - currentBalance) / (smoothingSteps - stepCount)
                    stepCount++
                    executeRootCommand("settings put system master_balance ${String.format("%.2f", currentBalance)}")
                    handler.postDelayed(this, (currentSpeed / smoothingSteps).toLong())
                } else {
                    val pattern = when (currentPattern) {
                        "custom" -> customPattern
                        else -> patterns[currentPattern] ?: patterns["smooth"]!!
                    }
                    targetBalance = pattern[currentPosition % pattern.size].toFloat()
                    
                    if (smoothAll) {
                        smoothingSteps = when {
                            currentSpeed <= 500 -> 5
                            currentSpeed <= 1000 -> 8
                            currentSpeed <= 2000 -> 12
                            else -> 16
                        }
                        stepCount = 1
                        currentBalance += (targetBalance - currentBalance) * 0.3f
                        executeRootCommand("settings put system master_balance ${String.format("%.2f", currentBalance)}")
                    } else {
                        currentBalance = targetBalance
                        executeRootCommand("settings put system master_balance ${String.format("%.2f", currentBalance)}")
                    }
                    
                    currentPosition++
                    handler.postDelayed(this, (currentSpeed / (if (smoothAll) smoothingSteps else 1)).toLong())
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startPanning()
            "STOP" -> stopPanning()
        }
        return START_STICKY
    }
    
    private fun startPanning() {
        if (isRunning) return
        
        isRunning = true
        currentPosition = 0
        
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        currentSpeed = prefs.getInt("speed", 1000)
        currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        smoothAll = prefs.getBoolean("smooth_all", true)
        val savedCustom = prefs.getString("custom_pattern", "-0.5,-0.25,0.0,0.25,0.5,0.25,0.0,-0.25")
        customPattern = savedCustom?.split(",")?.toTypedArray() ?: customPattern
        
        startForeground(1, createNotification())
        handler.post(panRunnable)
    }
    
    private fun stopPanning() {
        isRunning = false
        handler.removeCallbacks(panRunnable)
        executeRootCommand("settings put system master_balance 0.0")
        stopForeground(true)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "pan_service",
                "Auto Pan Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps auto-pan running"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return Notification.Builder(this, "pan_service")
            .setContentTitle("Auto Pan Active")
            .setContentText("Panning: $currentPattern")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun executeRootCommand(command: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                process.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(panRunnable)
        executeRootCommand("settings put system master_balance 0.0")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
