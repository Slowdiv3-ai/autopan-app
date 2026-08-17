package com.autopan.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.app.Activity
import android.graphics.Color
import android.widget.RadioGroup
import android.widget.RadioButton
import android.widget.LinearLayout
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private lateinit var patternGroup: RadioGroup
    private var isPanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Different panning patterns
    private val patterns = mapOf(
        "smooth" to arrayOf(
            "-0.8", "-0.6", "-0.4", "-0.2", "0.0", 
            "0.2", "0.4", "0.6", "0.8", "0.6", 
            "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6"
        ),
        "hard" to arrayOf(
            "-0.8", "0.0", "0.8", "0.0", "-0.8"
        ),
        "wave" to arrayOf(
            "-0.8", "-0.4", "0.0", "0.4", "0.8", 
            "0.4", "0.0", "-0.4", "-0.8", "-0.4",
            "0.0", "0.4", "0.8"
        ),
        "subtle" to arrayOf(
            "-0.3", "-0.15", "0.0", "0.15", "0.3", 
            "0.15", "0.0", "-0.15", "-0.3"
        ),
        "crazy" to arrayOf(
            "-0.8", "0.8", "-0.4", "0.4", "-0.8",
            "0.8", "-0.2", "0.2", "0.0", "-0.8"
        )
    )
    
    private var currentPattern = "smooth"
    private var currentPosition = 0
    
    private val panRunnable = object : Runnable {
        override fun run() {
            if (isPanning) {
                val pattern = patterns[currentPattern] ?: patterns["smooth"]!!
                val balance = pattern[currentPosition % pattern.size]
                executeRootCommand("settings put system master_balance $balance")
                currentPosition++
                handler.postDelayed(this, 1000)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        patternGroup = findViewById(R.id.patternGroup)
        
        // Set default pattern
        val smoothRadio = findViewById<RadioButton>(R.id.radioSmooth)
        smoothRadio.isChecked = true
        
        patternGroup.setOnCheckedChangeListener { _, checkedId ->
            currentPattern = when (checkedId) {
                R.id.radioSmooth -> "smooth"
                R.id.radioHard -> "hard"
                R.id.radioWave -> "wave"
                R.id.radioSubtle -> "subtle"
                R.id.radioCrazy -> "crazy"
                else -> "smooth"
            }
            currentPosition = 0
        }
        
        toggleButton.setOnClickListener {
            if (isPanning) {
                stopPanning()
            } else {
                startPanning()
            }
        }
    }
    
    private fun startPanning() {
        isPanning = true
        currentPosition = 0
        toggleButton.text = "STOP PAN"
        statusText.text = "Status: ACTIVE - ${currentPattern.uppercase()}"
        statusText.setTextColor(Color.GREEN)
        handler.post(panRunnable)
    }
    
    private fun stopPanning() {
        isPanning = false
        executeRootCommand("settings put system master_balance 0.0")
        toggleButton.text = "START PAN"
        statusText.text = "Status: OFF"
        statusText.setTextColor(Color.RED)
    }
    
    private fun executeRootCommand(command: String) {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                reader.readLine()
                errorReader.readLine()
                
                process.waitFor()
                reader.close()
                errorReader.close()
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
