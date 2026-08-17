package com.autopan.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.app.Activity
import android.graphics.Color
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : Activity() {
    
    private lateinit var toggleButton: Button
    private lateinit var statusText: TextView
    private var isPanning = false
    private val handler = Handler(Looper.getMainLooper())
    
    private val panPositions = arrayOf(
        "-0.8", "-0.6", "-0.4", "-0.2", "0.0", 
        "0.2", "0.4", "0.6", "0.8", "0.6", 
        "0.4", "0.2", "0.0", "-0.2", "-0.4", "-0.6"
    )
    private var currentPosition = 0
    
    private val panRunnable = object : Runnable {
        override fun run() {
            if (isPanning) {
                val balance = panPositions[currentPosition]
                executeRootCommand("settings put system master_balance $balance")
                currentPosition = (currentPosition + 1) % panPositions.size
                handler.postDelayed(this, 1000)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        toggleButton = findViewById(R.id.toggleButton)
        statusText = findViewById(R.id.statusText)
        
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
        statusText.text = "Status: ACTIVE"
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
