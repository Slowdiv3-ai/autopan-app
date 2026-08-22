package com.autopan.app

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.DataOutputStream
import java.util.UUID

class MainActivity : Activity() {
    
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var patternGroup: RadioGroup
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView
    private lateinit var customContainer: LinearLayout
    private lateinit var slidersContainer: LinearLayout
    private lateinit var bluetoothSwitch: Switch
    private lateinit var smoothSwitch: Switch
    private lateinit var movieModeSwitch: Switch
    private lateinit var accessibilityButton: Button
    private val customSeekBars = mutableListOf<SeekBar>()
    private val sliderLabels = mutableListOf<TextView>()
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentSpeed = 1000
    private var currentPattern = "smooth"
    private var customPattern = arrayOf(
        "-0.8", "-0.6", "-0.5", "-0.3", "-0.15", "0.0", "0.15",
        "0.3", "0.5", "0.6", "0.5", "0.3", "0.15", "0.0"
    )
    private var bluetoothAutoPan = false
    private var smoothAll = true
    private var movieModeEnabled = false
    
    private val presets = mapOf(
        "smooth" to arrayOf(
            "-0.8", "-0.6", "-0.4", "-0.2", "0.0", "0.2", "0.4",
            "0.6", "0.8", "0.6", "0.4", "0.2", "0.0", "-0.2"
        ),
        "bounce" to arrayOf(
            "-0.8", "-0.6", "-0.3", "0.0", "0.3", "0.6", "0.8",
            "0.6", "0.3", "0.0", "-0.3", "-0.6", "-0.3", "0.0"
        ),
        "circle" to arrayOf(
            "-0.8", "-0.6", "-0.3", "0.0", "0.3", "0.6", "0.8",
            "0.6", "0.3", "0.0", "-0.3", "-0.6", "-0.8", "-0.6"
        )
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
        
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        patternGroup = findViewById(R.id.patternGroup)
        speedSeekBar = findViewById(R.id.speedSeekBar)
        speedText = findViewById(R.id.speedText)
        customContainer = findViewById(R.id.customContainer)
        slidersContainer = findViewById(R.id.slidersContainer)
        bluetoothSwitch = findViewById(R.id.bluetoothSwitch)
        smoothSwitch = findViewById(R.id.smoothSwitch)
        movieModeSwitch = findViewById(R.id.movieModeSwitch)
        accessibilityButton = findViewById(R.id.accessibilityButton)
        
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        currentSpeed = prefs.getInt("speed", 1000)
        bluetoothAutoPan = prefs.getBoolean("bluetooth_auto_pan", false)
        smoothAll = prefs.getBoolean("smooth_all", true)
        movieModeEnabled = prefs.getBoolean("movie_mode", false)
        val savedCustom = prefs.getString("custom_pattern", "-0.8,-0.6,-0.5,-0.3,-0.15,0.0,0.15,0.3,0.5,0.6,0.5,0.3,0.15,0.0")
        customPattern = savedCustom?.split(",")?.toTypedArray() ?: customPattern
        
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
        
        createCustomSliders()
        createGraphDots()
        customContainer.visibility = if (currentPattern == "custom") LinearLayout.VISIBLE else LinearLayout.GONE
        
        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentSpeed = progress * 100
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
            customContainer.visibility = if (currentPattern == "custom") LinearLayout.VISIBLE else LinearLayout.GONE
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
                sendV4ACommand(0x10040, 1)
                sendV4ACommand(0x10041, 2000)
                Toast.makeText(this, "Movie Mode ON", Toast.LENGTH_SHORT).show()
            } else {
                sendV4ACommand(0x10040, 0)
                Toast.makeText(this, "Movie Mode OFF", Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<Button>(R.id.presetSmooth).setOnClickListener { applyPreset("smooth") }
        findViewById<Button>(R.id.presetBounce).setOnClickListener { applyPreset("bounce") }
        findViewById<Button>(R.id.presetCircle).setOnClickListener { applyPreset("circle") }
        
        toggleButton.setOnClickListener {
            if (PanService.isRunning) {
                stopPanning()
            } else {
                startPanning()
            }
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
        handler.postDelayed({ updateVisual() }, 100)
        
        // Re-apply movie mode if it was enabled
        if (movieModeEnabled) {
            handler.postDelayed({
                sendV4ACommand(0x10040, 1)
                sendV4ACommand(0x10041, 2000)
            }, 1000)
        }
    }
    
    private fun createCustomSliders() {
        slidersContainer.removeAllViews()
        customSeekBars.clear()
        sliderLabels.clear()
        
        for (i in 0 until 14) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 4)
            }
            
            val label = TextView(this).apply {
                text = "${i + 1}"
                textSize = 12f
                setTextColor(Color.WHITE)
                width = 28
            }
            
            val seekBar = SeekBar(this).apply {
                max = 160
                progress = ((customPattern[i].toFloat() + 0.8) * 100).toInt()
                tag = i
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val valueLabel = TextView(this).apply {
                text = customPattern[i]
                textSize = 11f
                setTextColor(Color.CYAN)
                width = 42
            }
            
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val index = seekBar?.tag as Int
                    val value = ((progress - 80) / 100f)
                    customPattern[index] = String.format("%.2f", value)
                    valueLabel.text = customPattern[index]
                    updateVisual()
                    saveCustomPattern()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            row.addView(label)
            row.addView(seekBar)
            row.addView(valueLabel)
            
            slidersContainer.addView(row)
            customSeekBars.add(seekBar)
            sliderLabels.add(valueLabel)
        }
    }
    
    private fun createGraphDots() {
        val graphArea = findViewById<LinearLayout>(R.id.graphArea)
        graphArea.removeAllViews()
        
        for (i in 0 until 14) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            val dot = TextView(this).apply {
                text = "${i + 1}"
                textSize = 10f
                setTextColor(Color.parseColor("#00BCD4"))
                tag = i
            }
            
            row.addView(dot)
            graphArea.addView(row)
        }
    }
    
    private fun applyPreset(presetName: String) {
        val preset = presets[presetName] ?: return
        customPattern = preset
        saveCustomPattern()
        
        for (i in 0 until customSeekBars.size) {
            val value = customPattern[i].toFloat()
            customSeekBars[i].progress = ((value + 0.8) * 100).toInt()
            sliderLabels[i].text = customPattern[i]
        }
        
        updateVisual()
    }
    
    private fun updateVisual() {
        val graphArea = findViewById<LinearLayout>(R.id.graphArea)
        val graphWidth = graphArea.width
        
        if (graphWidth > 0) {
            for (i in 0 until graphArea.childCount) {
                val row = graphArea.getChildAt(i) as LinearLayout
                if (row.childCount > 0) {
                    val dot = row.getChildAt(0) as TextView
                    val value = customPattern[i].toFloat()
                    
                    val position = (value + 0.8) / 1.6
                    val leftMargin = (position * (graphWidth - 35)).toInt()
                    
                    val params = dot.layoutParams as LinearLayout.LayoutParams
                    params.leftMargin = leftMargin
                    dot.layoutParams = params
                    
                    when {
                        value < -0.4 -> dot.setTextColor(Color.parseColor("#FF5722"))
                        value < -0.1 -> dot.setTextColor(Color.parseColor("#FF9800"))
                        value > 0.4 -> dot.setTextColor(Color.parseColor("#4CAF50"))
                        value > 0.1 -> dot.setTextColor(Color.parseColor("#8BC34A"))
                        else -> dot.setTextColor(Color.WHITE)
                    }
                }
            }
        }
    }
    
    private fun saveCustomPattern() {
        val values = customPattern.joinToString(",")
        getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_pattern", values)
            .apply()
    }
    
    private fun sendV4ACommand(param: Int, value: Int) {
        Thread {
            try {
                val viperUuid = UUID.fromString("90380da3-8536-4744-a6a3-5731970e640f")
                val effect = AudioEffect(viperUuid, viperUuid, 0, 0)
                
                val command = ByteArray(4).apply {
                    this[0] = (param and 0xFF).toByte()
                    this[1] = ((param shr 8) and 0xFF).toByte()
                    this[2] = ((param shr 16) and 0xFF).toByte()
                    this[3] = ((param shr 24) and 0xFF).toByte()
                }
                
                val valueBytes = ByteArray(4).apply {
                    this[0] = (value and 0xFF).toByte()
                    this[1] = ((value shr 8) and 0xFF).toByte()
                    this[2] = ((value shr 16) and 0xFF).toByte()
                    this[3] = ((value shr 24) and 0xFF).toByte()
                }
                
                effect.setParameter(command, valueBytes)
                effect.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
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
            statusText.setTextColor(Color.GREEN)
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
            statusText.setTextColor(Color.RED)
        }, 500)
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
        handler.postDelayed({ updateUI() }, 300)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
        }
    }
}
