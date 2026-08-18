package com.autopan.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var patternGroup: RadioGroup
    private lateinit var speedSeekBar: SeekBar
    private lateinit var speedText: TextView
    private lateinit var customContainer: LinearLayout
    private val customSeekBars = mutableListOf<SeekBar>()
    
    private var isPanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var currentPosition = 0
    private var currentSpeed = 1000
    private var currentPattern = "smooth"
    private var customPattern = arrayOf("-0.5", "-0.25", "0.0", "0.25", "0.5", "0.25", "0.0", "-0.25")
    
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
        override fun run() {
            if (isPanning) {
                val pattern = when (currentPattern) {
                    "custom" -> customPattern
                    else -> patterns[currentPattern] ?: patterns["smooth"]!!
                }
                val balance = pattern[currentPosition % pattern.size]
                executeRootCommand("settings put system master_balance $balance")
                currentPosition++
                handler.postDelayed(this, currentSpeed.toLong())
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
        
        val prefs = getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
        currentPattern = prefs.getString("pattern", "smooth") ?: "smooth"
        currentSpeed = prefs.getInt("speed", 1000)
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
        
        createCustomSliders()
        
        // Show custom sliders if custom pattern selected
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
            currentPosition = 0
            
            // Show/hide custom sliders
            customContainer.visibility = if (currentPattern == "custom") LinearLayout.VISIBLE else LinearLayout.GONE
        }
        
        toggleButton.setOnClickListener {
            if (isPanning) {
                stopPanning()
            } else {
                startPanning()
            }
        }
        
        updateUI()
    }
    
    private fun createCustomSliders() {
        customContainer.removeAllViews()
        customSeekBars.clear()
        
        for (i in 0 until 8) {
            val label = TextView(this).apply {
                text = "Step ${i + 1}: ${customPattern[i]}"
                textSize = 12f
                setTextColor(Color.WHITE)
            }
            
            val seekBar = SeekBar(this).apply {
                max = 160  // -0.8 to 0.8
                progress = ((customPattern[i].toFloat() + 0.8) * 100).toInt()
                tag = i
            }
            
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val index = seekBar?.tag as Int
                    val value = ((progress - 80) / 100f)
                    customPattern[index] = String.format("%.2f", value)
                    label.text = "Step ${index + 1}: ${customPattern[index]}"
                    saveCustomPattern()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            
            customContainer.addView(label)
            customContainer.addView(seekBar)
            customSeekBars.add(seekBar)
        }
    }
    
    private fun saveCustomPattern() {
        val values = customPattern.joinToString(",")
        getSharedPreferences("pan_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_pattern", values)
            .apply()
    }
    
    private fun startPanning() {
        isPanning = true
        currentPosition = 0
        toggleButton.text = "STOP PAN"
        statusText.text = "Status: ACTIVE - ${currentPattern.uppercase()}"
        statusText.setTextColor(Color.GREEN)
        handler.post(panRunnable)
        Toast.makeText(this, "Panning started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopPanning() {
        isPanning = false
        handler.removeCallbacks(panRunnable)
        executeRootCommand("settings put system master_balance 0.0")
        toggleButton.text = "START PAN"
        statusText.text = "Status: OFF"
        statusText.setTextColor(Color.RED)
        Toast.makeText(this, "Panning stopped", Toast.LENGTH_SHORT).show()
    }
    
    private fun updateUI() {
        toggleButton.text = if (isPanning) "STOP PAN" else "START PAN"
        statusText.text = if (isPanning) "Status: ACTIVE" else "Status: OFF"
        statusText.setTextColor(if (isPanning) Color.GREEN else Color.RED)
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
        if (isPanning) {
            stopPanning()
        }
    }
}
