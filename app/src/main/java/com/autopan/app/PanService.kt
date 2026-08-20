package com.autopan.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.DataOutputStream

class PanService : Service() {
    
    companion object {
        var isRunning = false
    }
    
    private var panningThread: Thread? = null
    private var currentPosition = 0
    private var currentSpeed = 1000
    private var currentPattern = "smooth"
    private var customPattern = arrayOf(
        "-0.8", "-0.6", "-0.5", "-0.3", "-0.15", "0.0", "0.15",
        "0.3", "0.5", "0.6", "0.5", "0.3", "0.15", "0.0"
    )
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
        
        startForeground(1, createNotification())
        
        panningThread = Thread {
            var currentBalance = 0.0f
            var targetBalance = 0.0f
            
            while (isRunning) {
                try {
                    // Read preferences LIVE on every cycle
                    val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
                    currentSpeed = prefs.getInt("speed", 1000)
                    currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
                    smoothAll = prefs.getBoolean("smooth_all", true)
                    val savedCustom = prefs.getString("custom_pattern", "-0.8,-0.6,-0.5,-0.3,-0.15,0.0,0.15,0.3,0.5,0.6,0.5,0.3,0.15,0.0")
                    customPattern = savedCustom?.split(",")?.toTypedArray() ?: customPattern
                    
                    val pattern = when (currentPattern) {
                        "custom" -> customPattern
                        else -> patterns[currentPattern] ?: patterns["smooth"]!!
                    }
                    
                    targetBalance = pattern[currentPosition % pattern.size].toFloat()
                    
                    if (smoothAll) {
                        val smoothingSteps = when {
                            currentSpeed <= 500 -> 10
                            currentSpeed <= 1000 -> 16
                            currentSpeed <= 2000 -> 24
                            currentSpeed <= 3000 -> 32
                            else -> 40
                        }
                        
                        val stepDelay = currentSpeed / smoothingSteps
                        
                        for (step in 1..smoothingSteps) {
                            if (!isRunning) break
                            currentBalance += (targetBalance - currentBalance) * 0.2f
                            executeRootCommand("settings put system master_balance ${String.format("%.3f", currentBalance)}")
                            try {
                                Thread.sleep(stepDelay.toLong())
                            } catch (e: InterruptedException) {
                                break
                            }
                        }
                    } else {
                        currentBalance = targetBalance
                        executeRootCommand("settings put system master_balance ${String.format("%.3f", currentBalance)}")
                        try {
                            Thread.sleep(currentSpeed.toLong())
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                    
                    currentPosition++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        panningThread?.start()
    }
    
    private fun stopPanning() {
        isRunning = false
        panningThread?.interrupt()
        panningThread = null
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
        try {
            val process = Runtime.getRuntime().exec("su")
            val outputStream = DataOutputStream(process.outputStream)
            outputStream.writeBytes("$command\n")
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            process.waitFor()
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        executeRootCommand("settings put system master_balance 0.0")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
