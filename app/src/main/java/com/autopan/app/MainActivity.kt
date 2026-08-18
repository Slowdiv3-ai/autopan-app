package com.autopan.app

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

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
    private val customSeekBars = mutableListOf<SeekBar>()
    private val sliderLabels = mutableListOf<TextView>()
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentSpeed = 1000
    private var currentPattern = "smooth"
    private var customPattern = arrayOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25")
    private var bluetoothAutoPan = false
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
    
    private val presets = mapOf(
        "smooth" to arrayOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25"),
        "bounce" to arrayOf("-0.8", "0.0", "-0.4", "0.0", "0.4", "0.0", "0.8", "0.0"),
        "circle" to arrayOf("-0.8", "-0.4", "0.0", "0.4", "0.8", "0.4", "0.0", "-0.4")
    )
    
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (bluetoothAutoPan && !PanService.isRunning) {
                        startPanService()
                        Toast.makeText(this@MainActivity, "Bluetooth connected - Panning started", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (bluetoothAutoPan && PanService.isRunning) {
                        stopPanService()
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
        
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        currentSpeed = prefs.getInt("speed", 1000)
        bluetoothAutoPan = prefs.getBoolean("bluetooth_auto_pan", false)
        smoothAll = prefs.getBoolean("smooth_all", true)
        val savedCustom = prefs.getString("custom_pattern", "-0.5,-0.25,0.0,0.25,0.5,0.25,0.0,-0.25")
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
        
        findViewById<Button>(R.id.presetSmooth).setOnClickListener { applyPreset("smooth") }
        findViewById<Button>(R.id.presetBounce).setOnClickListener { applyPreset("bounce") }
        findViewById<Button>(R.id.presetCircle).setOnClickListener { applyPreset("circle") }
        
        toggleButton.setOnClickListener {
            if (PanService.isRunning) {
                stopPanService()
            } else {
                startPanService()
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        registerReceiver(bluetoothReceiver, filter)
        
        updateUI()
        handler.postDelayed({ updateVisual() }, 100)
    }
    
    private fun createCustomSliders() {
        slidersContainer.removeAllViews()
        customSeekBars.clear()
        sliderLabels.clear()
        
        for (i in 0 until 8) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
            }
            
            val label = TextView(this).apply {
                text = "${i + 1}"
                textSize = 14f
                setTextColor(Color.WHITE)
                width = 30
            }
            
            val seekBar = SeekBar(this).apply {
                max = 160
                progress = ((customPattern[i].toFloat() + 0.8) * 100).toInt()
                tag = i
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val valueLabel = TextView(this).apply {
                text = customPattern[i]
                textSize = 12f
                setTextColor(Color.CYAN)
                width = 45
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
        
        for (i in 0 until 8) {
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
                textSize = 12f
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
                    val leftMargin = (position * (graphWidth - 40)).toInt()
                    
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
    
    private fun startPanService() {
        PanService.start(this)
        updateUI()
    }
    
    private fun stopPanService() {
        PanService.stop()
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
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
        }
    }
}
