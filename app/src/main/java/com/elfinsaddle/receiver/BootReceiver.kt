package com.elfinsaddle.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.elfinsaddle.worker.TokenCheckWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        const val TOKEN_CHECK_INTERVAL_HOURS = 24L
        const val TOKEN_MONITOR_WORK_NAME = "token_monitor_work"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, "android.intent.action.QUICKBOOT_POWERON", Intent.ACTION_MY_PACKAGE_REPLACED)) {
            return
        }

        Log.i(TAG, "Boot or App Update detected. Action: ${intent.action}. Ensuring workers are scheduled.")
        enqueueTokenMonitor(context)
    }

    private fun enqueueTokenMonitor(context: Context) {
        try {
            val tokenCheckWork = PeriodicWorkRequestBuilder<TokenCheckWorker>(
                TOKEN_CHECK_INTERVAL_HOURS, TimeUnit.HOURS
            ).build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TOKEN_MONITOR_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                tokenCheckWork
            )
            Log.d(TAG, "Token monitoring worker has been enqueued.")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling token monitor worker", e)
        }
    }
}
