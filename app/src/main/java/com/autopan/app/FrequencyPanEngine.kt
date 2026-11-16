package com.autopan.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.*

class FrequencyPanEngine {

    private var isPlaying = false
    private var audioThread: Thread? = null
    private val sampleRate = 44100

    // DSP Variables
    private var filterStateL = 0.0f
    private var filterStateR = 0.0f
    private var panLfo = 0.0f 

    // We use REMOTE_SUBMIX to capture system audio (Requires Root)
    private val audioSource = MediaRecorder.AudioSource.REMOTE_SUBMIX

    fun start() {
        if (isPlaying) return
        isPlaying = true

        audioThread = thread {
            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            // 1. Setup the System Audio Capture
            val audioRecord = AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize
            )

            // 2. Setup the Output Audio Track
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                audioRecord.startRecording()
                audioTrack.play()

                val buffer = ShortArray(minBufferSize / 2) 

                               while (isPlaying) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // --- TEST TONE GENERATION ---
                        val freqLow = 440.0 // Low pitch
                        val freqHigh = 2000.0 // High pitch
                        var time = System.currentTimeMillis() / 1000.0
                        
                        // Advance LFO
                        panLfo += 0.5f / sampleRate // 0.5Hz sweep (2 seconds per cycle)
                        if (panLfo > 1.0f) panLfo -= 1.0f

                        val cutoffL = 200f + (panLfo * 4800f) 
                        val cutoffR = 5000f - (panLfo * 4800f) 

                        val alphaL = (2.0 * PI * cutoffL / sampleRate).toFloat().coerceIn(0.01f, 0.99f)
                        val alphaR = (2.0 * PI * cutoffR / sampleRate).toFloat().coerceIn(0.01f, 0.99f)

                        for (i in 0 until read step 2) {
                            // Generate a mix of low and high tones
                            val signal = (sin(2.0 * PI * freqLow * time) * 0.5 + sin(2.0 * PI * freqHigh * time) * 0.5).toFloat()
                            
                            // Apply filters to our generated tone
                            filterStateL += alphaL * (signal - filterStateL)
                            filterStateR += alphaR * (signal - filterStateR)

                            buffer[i] = (filterStateL * 32767).toInt().toShort()
                            buffer[i + 1] = (filterStateR * 32767).toInt().toShort()
                            
                            time += 1.0 / sampleRate
                        }
                        audioTrack.write(buffer, 0, read)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // ONLY stop/release if the state is initialized
                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord.stop()
                }
                audioRecord.release()
                
                try {
                    if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.stop()
                    }
                } catch (e: Exception) {}
                audioTrack.release()
                
                isPlaying = false
            }
        }
    } // <--- THIS WAS MISSING

    fun stop() {
        isPlaying = false
        audioThread?.join()
        audioThread = null
    }
}
