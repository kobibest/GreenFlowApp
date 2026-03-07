package com.tanglycohort.greenflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tanglycohort.greenflow.service.WebhookService

/**
 * Starts the foreground service when the device boots, if the user has enabled
 * persistent background mode. Ensures SMS hooks keep working after restart.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action.isNullOrBlank()) return
        if (!WebhookService.isPersistentBackgroundEnabled(context)) return

        val serviceIntent = Intent(context, GreenFlowForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
