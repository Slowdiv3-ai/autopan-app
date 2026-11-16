package com.autopan.app

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.DataOutputStream
import kotlin.math.*

class MainActivity : Activity(), PanRingView.OnCustomPatternChangedListener {
    
    // UI Components
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var patternGroup: RadioGroup
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView
    private lateinit var customContainer: LinearLayout
    private lateinit var bluetoothSwitch: Switch
    private lateinit var smoothSwitch: Switch
    private lateinit var movieModeSwitch: Switch
    private lateinit var accessibilityButton: Button
    private lateinit var mainRingView: PanRingView
    private lateinit var customRingView: PanRingView
    
    // Logic Variables
    private val handler = Handler(Looper.getMainLooper())
    private var currentSpeed = 1000
    private var currentPattern = "smooth"
    private var bluetoothAutoPan = false
    private var smoothAll = true
    private var movieModeEnabled = false
    
    // The Frequency Engine
    private var frequencyEngine: FrequencyPanEngine? = null
    
    // The custom pattern is now derived from the angles tapped on the ring
    private var customPattern = arrayOf(
        "-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4", "0.6", "0.8"
    )

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (bluetoothAutoPan && !PanService.isRunning) {
                        startPanning()
                        Toast.makeText(this@MainActivity, "Bluetooth connected - Panning started", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (bluetoothAutoPan && PanService.isRunning) {
                        stopPanning()
                        Toast.makeText(this@MainActivity, "Bluetooth disconnected - Panning stopped", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize Views
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        patternGroup = findViewById(R.id.patternGroup)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedText = findViewById(R.id.speedText)
        customContainer = findViewById(R.id.customContainer)
        bluetoothSwitch = findViewById(R.id.bluetoothSwitch)
        smoothSwitch = findViewById(R.id.smoothSwitch)
        movieModeSwitch = findViewById(R.id.movieModeSwitch)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        mainRingView = findViewById(R.id.panRingView)
        customRingView = findViewById(R.id.customPanRingView)
        
        // Setup Custom Ring Listener
        customRingView.patternChangeListener = this
        customRingView.isCustomMode = true
        
        // Load Preferences
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        currentSpeed = prefs.getInt("speed", 1000)
        bluetoothAutoPan = prefs.getBoolean("bluetooth_auto_pan", false)
        smoothAll = prefs.getBoolean("smooth_all", true)
        movieModeEnabled = prefs.getBoolean("movie_mode", false)
        
        // Load Custom Angles from prefs
        val savedAngles = prefs.getString("custom_angles", "0,45,90,135,180")
        val angleList = savedAngles?.split(",")?.mapNotNull { it.toFloatOrNull() } ?: listOf(0f,45f,90f,135f,180f)
        customRingView.customDotAngles.clear()
        customRingView.customDotAngles.addAll(angleList)
        
        // Convert loaded angles to the float array for PanService
        updatePatternFromAngles(angleList)

        // Update UI State
        when (currentPattern) {
            "hard" -> findViewById<RadioButton>(R.id.radioHard).isChecked = true
            "wave" -> findViewById<RadioButton>(R.id.radioWave).isChecked = true
            "subtle" -> findViewById<RadioButton>(R.id.radioSubtle).isChecked = true
            "crazy" -> findViewById<RadioButton>(R.id.radioCrazy).isChecked = true
            "custom" -> findViewById<RadioButton>(R.id.radioCustom).isChecked = true
            else -> findViewById<RadioButton>(R.id.radioSmooth).isChecked = true
        }
        
        speedSeekBar.progress = currentSpeed / 100
        speedText.text = "Speed: ${currentSpeed}ms"
        bluetoothSwitch.isChecked = bluetoothAutoPan
        smoothSwitch.isChecked = smoothAll
        movieModeSwitch.isChecked = movieModeEnabled
        customContainer.visibility = if (currentPattern == "custom") View.VISIBLE else View.GONE
        
        // Setup Listeners
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentSpeed = (progress + 1) * 100 // Min 100ms
                speedText.text = "Speed: ${currentSpeed}ms"
                prefs.edit().putInt("speed", currentSpeed).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        patternGroup.setOnCheckedChangeListener { _, checkedId ->
            currentPattern = when (checkedId) {
                R.id.radioHard -> "hard"
                R.id.radioWave -> "wave"
                R.id.radioSubtle -> "subtle"
                R.id.radioCrazy -> "crazy"
                R.id.radioCustom -> "custom"
                else -> "smooth"
            }
            prefs.edit().putString("pattern", currentPattern).apply()
            
            // Toggle UI
            if (currentPattern == "custom") {
                customContainer.visibility = View.VISIBLE
                mainRingView.visibility = View.GONE
            } else {
                customContainer.visibility = View.GONE
                mainRingView.visibility = View.VISIBLE
            }
        }
        
        bluetoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            bluetoothAutoPan = isChecked
            prefs.edit().putBoolean("bluetooth_auto_pan", isChecked).apply()
        }
        
        smoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            smoothAll = isChecked
            prefs.edit().putBoolean("smooth_all", isChecked).apply()
        }
        
        movieModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            movieModeEnabled = isChecked
            prefs.edit().putBoolean("movie_mode", isChecked).apply()
            
            if (isChecked) {
                // 1. Stop standard volume panning
                if (PanService.isRunning) {
                    stopPanning()
                }
                executeRootCommand("settings put system master_balance 0.0")

                // 2. Grant Audio Record permission via root (SELinux bypass for REMOTE_SUBMIX)
                executeRootCommand("appops set com.autopan.app RECORD_AUDIO allow")

                // 3. Start the frequency engine!
                frequencyEngine = FrequencyPanEngine()
                frequencyEngine?.start()
                
                // Disable UI elements that don't apply to Movie Mode
                patternGroup.isEnabled = false
                toggleButton.isEnabled = false
                toggleButton.alpha = 0.5f
                
                Toast.makeText(this, "Movie Mode ON (Frequency Pan Active)", Toast.LENGTH_SHORT).show()
                
            } else {
                // 1. Stop the frequency engine
                frequencyEngine?.stop()
                frequencyEngine = null
                
                // 2. Re-enable UI
                patternGroup.isEnabled = true
                toggleButton.isEnabled = true
                toggleButton.alpha = 1.0f
                
                Toast.makeText(this, "Movie Mode OFF", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Presets
        findViewById<Button>(R.id.presetSmooth).setOnClickListener { applyPreset(listOf(0f, 45f, 90f, 135f, 180f)) }
        findViewById<Button>(R.id.presetBounce).setOnClickListener { applyPreset(listOf(0f, 90f, 180f)) }
        findViewById<Button>(R.id.presetCircle).setOnClickListener { applyPreset(listOf(30f, 90f, 150f)) }
        
        toggleButton.setOnClickListener {
            if (PanService.isRunning) stopPanning() else startPanning()
        }
        
        accessibilityButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Enable Auto Pan Toggle in Accessibility settings", Toast.LENGTH_LONG).show()
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
        
        updateUI()
        
        // Re-apply movie mode if it was left on
        if (movieModeEnabled) {
            handler.postDelayed({
                if (PanService.isRunning) stopPanning()
                executeRootCommand("settings put system master_balance 0.0")
                executeRootCommand("appops set com.autopan.app RECORD_AUDIO allow")
                frequencyEngine = FrequencyPanEngine()
                frequencyEngine?.start()
                patternGroup.isEnabled = false
                toggleButton.isEnabled = false
                toggleButton.alpha = 0.5f
            }, 1000)
        }
    }
    
    override fun onPatternChanged(angles: List<Float>) {
        updatePatternFromAngles(angles)
        saveCustomAngles(angles)
    }

    private fun updatePatternFromAngles(angles: List<Float>) {
        if (angles.isEmpty()) return
        
        val newPattern = mutableListOf<String>()
        for (angle in angles) {
            // Map 0 -> -0.8 (Left), 90 -> 0.0 (Center), 180 -> +0.8 (Right)
            val value = ((angle / 180f) * 1.6f - 0.8f)
            newPattern.add(String.format("%.2f", value))
        }
        customPattern = newPattern.toTypedArray()
    }

    private fun saveCustomAngles(angles: List<Float>) {
        val angleString = angles.joinToString(",")
        getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_angles", angleString)
            .apply()
    }

    private fun applyPreset(angles: List<Float>) {
        customRingView.customDotAngles.clear()
        customRingView.customDotAngles.addAll(angles)
        customRingView.invalidate()
        onPatternChanged(angles)
    }
    
    private fun startPanning() {
        val intent = Intent(this, PanService::class.java)
        intent.action = "START"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        handler.postDelayed({
            toggleButton.text = "STOP PAN"
            statusText.text = "Status: ACTIVE"
            statusText.setTextColor(Color.parseColor("#4CAF50")) 
            toggleButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336"))
            
            // Start the new wave animation
            mainRingView.startPanAnimation()
            
        }, 500)
    }
    
    private fun stopPanning() {
        val intent = Intent(this, PanService::class.java)
        intent.action = "STOP"
        startService(intent)
        executeRootCommand("settings put system master_balance 0.0")
        handler.postDelayed({
            toggleButton.text = "START PAN"
            statusText.text = "Status: OFF"
            statusText.setTextColor(Color.parseColor("#FF5252"))
            toggleButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E2E2E"))
            
            // Stop the wave animation
            mainRingView.stopPanAnimation()
            
        }, 500)
    }
    
    private fun updateUI() {
        if (PanService.isRunning) {
            toggleButton.text = "STOP PAN"
            statusText.text = "Status: ACTIVE"
            statusText.setTextColor(Color.parseColor("#4CAF50"))
            toggleButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336"))
        } else {
            toggleButton.text = "START PAN"
            statusText.text = "Status: OFF"
            statusText.setTextColor(Color.parseColor("#FF5252"))
            toggleButton.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2E2E2E"))
        }
    }
    
    private fun executeRootCommand(command: String) {
        Thread {
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
        }.start()
    }
    
    override fun onResume() {
        super.onResume()
        // Restore UI state and restart animation if service is running
        handler.postDelayed({ 
            updateUI() 
            if (PanService.isRunning) {
                mainRingView.startPanAnimation()
            }
        }, 300)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Make sure the audio engine stops if the app is closed
        frequencyEngine?.stop()
        frequencyEngine = null
        
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
        }
    }
}
