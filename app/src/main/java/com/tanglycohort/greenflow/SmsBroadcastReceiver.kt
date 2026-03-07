package com.tanglycohort.greenflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tanglycohort.greenflow.service.WebhookService

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        if (!WebhookService.isServiceEnabled(context)) {
            AppLog.d(TAG, "Service disabled, ignoring SMS")
            return
        }

        val rawMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val parts = rawMessages.filterNotNull()
        if (parts.isEmpty()) return

        // Reassemble multipart SMS into one message (same sender, combined body)
        val sender = parts.first().originatingAddress
        val fullBody = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }

        val filters = SmsFilter.getFilters(context)
        if (filters == null || filters.isEmpty()) {
            if (WebhookService.isForwardAllWhenNoFilters(context)) {
                AppLog.d(TAG, "No filters loaded, forwarding all (setting enabled): from=$sender")
                enqueueWebhook(context, sender ?: "", fullBody)
            } else {
                AppLog.d(TAG, "No filters loaded, requesting from server: from=$sender")
                FetchFiltersWorker.enqueueOnce(context, sender ?: "", fullBody)
            }
            return
        }
        if (!SmsFilter.matches(sender, fullBody, filters)) {
            AppLog.d(TAG, "SMS filtered out (no matching whitelist filter), not sending: from=$sender bodyPreview=${fullBody.take(50)}")
            return
        }

        AppLog.d(TAG, "Received SMS (passed filter), sending to webhook: from=$sender bodyLength=${fullBody.length}")
        enqueueWebhook(context, sender ?: "", fullBody)
    }

    private fun enqueueWebhook(context: Context, sender: String, fullBody: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    WebhookWorker.KEY_ORIGINATING_ADDRESS to sender,
                    WebhookWorker.KEY_MESSAGE_BODY to fullBody
                )
            )
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }
}
