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
import com.tanglycohort.greenflow.data.repository.AuthRepository
import com.tanglycohort.greenflow.service.WebhookService

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            AppLog.d(TAG, "onReceive: action=${intent.action}, ignoring")
            return
        }
        AppLog.d(TAG, "onReceive: SMS_RECEIVED_ACTION received")
        if (!WebhookService.isServiceEnabled(context)) {
            AppLog.d(TAG, "Service disabled, ignoring SMS")
            return
        }

        val rawMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (rawMessages == null) {
            AppLog.d(TAG, "getMessagesFromIntent returned null, ignoring")
            return
        }
        val parts = rawMessages.filterNotNull()
        if (parts.isEmpty()) {
            AppLog.d(TAG, "No message parts, ignoring")
            return
        }

        // Reassemble multipart SMS into one message (same sender, combined body)
        val sender = parts.first().originatingAddress
        val fullBody = parts.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
        AppLog.d(TAG, "SMS received: from=$sender bodyLength=${fullBody.length} bodyPreview=${fullBody.take(80)}")

        val filters = SmsFilter.getFilters(context)
        if (filters == null || filters.isEmpty()) {
            if (WebhookService.isForwardAllWhenNoFilters(context)) {
                AppLog.d(TAG, "No filters loaded, forwarding all (setting enabled): from=$sender")
                enqueueWebhook(context, sender ?: "", fullBody)
            } else {
                AppLog.d(TAG, "No filters loaded, requesting from server: from=$sender")
                val token = AuthRepository().currentSession()?.accessToken
                FetchFiltersWorker.enqueueOnce(context, sender ?: "", fullBody, System.currentTimeMillis(), token)
            }
            return
        }
        if (!SmsFilter.matches(sender, fullBody, filters)) {
            AppLog.w(TAG, "SMS FILTERED (no matching whitelist): from=$sender bodyPreview=${fullBody.take(80)} filtersCount=${filters.size}")
            return
        }

        AppLog.d(TAG, "SMS PASSED filter, sending to webhook: from=$sender bodyLength=${fullBody.length}")
        enqueueWebhook(context, sender ?: "", fullBody)
    }

    private fun enqueueWebhook(context: Context, sender: String, fullBody: String) {
        AppLog.d(TAG, "enqueueWebhook: from=$sender bodyLength=${fullBody.length}")
        val token = AuthRepository().currentSession()?.accessToken
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<WebhookWorker>()
            .setConstraints(constraints)
            .setInputData(
                workDataOf(
                    WebhookWorker.KEY_ORIGINATING_ADDRESS to sender,
                    WebhookWorker.KEY_MESSAGE_BODY to fullBody,
                    WebhookWorker.KEY_RECEIVED_AT to System.currentTimeMillis(),
                    WebhookWorker.KEY_ACCESS_TOKEN to (token ?: "")
                )
            )
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }
}
