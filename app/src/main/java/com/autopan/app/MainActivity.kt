package com.autopan.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var patternGroup: RadioGroup
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView
    private lateinit var bluetoothSwitch: Switch
    private lateinit var customPatternContainer: LinearLayout
    private lateinit var customPatternSeekBars: MutableList<SeekBar>
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        patternGroup = findViewById(R.id.patternGroup)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedText = findViewById(R.id.speedText)
        bluetoothSwitch = findViewById(R.id.bluetoothSwitch)
        customPatternContainer = findViewById(R.id.customPatternContainer)
        customPatternSeekBars = mutableListOf()
        
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        
        // Load preferences
        val savedPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        val savedSpeed = prefs.getInt("speed", 1000)
        val bluetoothEnabled = prefs.getBoolean("bluetooth_enabled", false)
        val customValues = prefs.getString("custom_pattern", "-0.5,-0.25,0,0.25,0.5,0.25,0,-0.25") ?: "-0.5,-0.25,0,0.25,0.5,0.25,0,-0.25"
        
        // Set saved pattern
        when (savedPattern) {
            "hard" -> findViewById<RadioButton>(R.id.radioHard).isChecked = true
            "wave" -> findViewById<RadioButton>(R.id.radioWave).isChecked = true
            "subtle" -> findViewById<RadioButton>(R.id.radioSubtle).isChecked = true
            "crazy" -> findViewById<RadioButton>(R.id.radioCrazy).isChecked = true
            "custom" -> findViewById<RadioButton>(R.id.radioCustom).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioSmooth).isChecked = true
        }
        
        // Set speed
        speedSeekBar.progress = savedSpeed / 100
        speedText.text = "Speed: ${savedSpeed}ms"
        
        // Set Bluetooth switch
        bluetoothSwitch.isChecked = bluetoothEnabled
        
        // Create custom pattern sliders
        val values = customValues.split(",").map { it.toFloat() }
        createCustomPatternSliders(values)
        
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = progress * 100
                speedText.text = "Speed: ${speed}ms"
                prefs.edit().putInt("speed", speed).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        patternGroup.setOnCheckedChangeListener { _, checkedId ->
            val pattern = when (checkedId) {
                R.id.radioHard -> "hard"
                R.id.radioWave -> "wave"
                R.id.radioSubtle -> "subtle"
                R.id.radioCrazy -> "crazy"
                R.id.radioCustom -> "custom"
                else -> "smooth"
            }
            prefs.edit().putString("pattern", pattern).apply()
            
            // Show/hide custom pattern sliders
            customPatternContainer.visibility = 
                if (pattern == "custom") LinearLayout.VISIBLE else LinearLayout.GONE
        }
        
        bluetoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("bluetooth_enabled", isChecked).apply()
            if (isChecked) {
                Toast.makeText(this, "Bluetooth auto-pan enabled", Toast.LENGTH_SHORT).show()
            }
        }
        
        toggleButton.setOnClickListener {
            if (PanService.isRunning) {
                stopPanning()
            } else {
                startPanning()
            }
        }
        
        // Show/hide custom sliders initially
        customPatternContainer.visibility = 
            if (savedPattern == "custom") LinearLayout.VISIBLE else LinearLayout.GONE
        
        updateUI()
    }
    
    private fun createCustomPatternSliders(values: List<Float>) {
        customPatternContainer.removeAllViews()
        customPatternSeekBars.clear()
        
        for (i in 0 until 8) {
            val label = TextView(this).apply {
                text = "Step ${i + 1}"
                textSize = 12f
                setTextColor(Color.WHITE)
            }
            
            val seekBar = SeekBar(this).apply {
                max = 200
                progress = ((values.getOrElse(i) { 0f } + 1) * 100).toInt()
                tag = i
            }
            
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    saveCustomPattern()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            customPatternContainer.addView(label)
            customPatternContainer.addView(seekBar)
            customPatternSeekBars.add(seekBar)
        }
    }
    
    private fun saveCustomPattern() {
        val values = customPatternSeekBars.map { seekBar ->
            val value = (seekBar.progress - 100) / 100f
            value.toString()
        }.joinToString(",")
        
        getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_pattern", values)
            .apply()
    }
    
    private fun startPanning() {
        try {
            val intent = Intent(this, PanService::class.java)
            intent.action = "START"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            updateUI()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error starting service", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopPanning() {
        val intent = Intent(this, PanService::class.java)
        intent.action = "STOP"
        startService(intent)
        updateUI()
    }
    
    private fun updateUI() {
        if (PanService.isRunning) {
            toggleButton.text = "STOP PAN"
            statusText.text = "Status: ACTIVE"
            statusText.setTextColor(Color.GREEN)
        } else {
            toggleButton.text = "START PAN"
            statusText.text = "Status: OFF"
            statusText.setTextColor(Color.RED)
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
    }
}

class PanService : Service() {
    
    companion object {
        var isRunning = false
        var currentSpeed = 1000
        var currentPattern = "smooth"
        
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
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentPosition = 0
    private var customPatternArray = arrayOf("-0.5", "-0.25", "0", "0.25", "0.5", "0.25", "0", "-0.25")
    
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (isBluetoothEnabled() && !isRunning) {
                        startPanning()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (isBluetoothEnabled() && isRunning) {
                        stopPanning()
                    }
                }
            }
        }
    }
    
    private val panRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val pattern = when (currentPattern) {
                    "custom" -> customPatternArray
                    else -> patterns[currentPattern] ?: patterns["smooth"]!!
                }
                val balance = pattern[currentPosition % pattern.size]
                executeRootCommand("settings put system master_balance $balance")
                currentPosition++
                handler.postDelayed(this, currentSpeed.toLong())
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerBluetoothReceiver()
        loadPreferences()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> startPanning()
            "STOP" -> stopPanning()
        }
        return START_NOT_STICKY
    }
    
    private fun startPanning() {
        isRunning = true
        currentPosition = 0
        loadPreferences()
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
    
    private fun loadPreferences() {
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        currentSpeed = prefs.getInt("speed", 1000)
        currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        val customValues = prefs.getString("custom_pattern", "-0.5,-0.25,0,0.25,0.5,0.25,0,-0.25")
        customPatternArray = customValues?.split(",")?.toTypedArray() ?: customPatternArray
    }
    
    private fun isBluetoothEnabled(): Boolean {
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("bluetooth_enabled", false)
    }
    
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
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
    
    private fun createNotification() = NotificationCompat.Builder(this, "pan_service")
        .setContentTitle("Auto Pan Active")
        .setContentText("Pattern: $currentPattern")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .setOngoing(true)
        .build()
    
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
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            // Receiver already unregistered
        }
        executeRootCommand("settings put system master_balance 0.0")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
}
