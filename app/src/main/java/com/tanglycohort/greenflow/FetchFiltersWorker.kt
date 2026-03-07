package com.tanglycohort.greenflow

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Server response format: { "rules": [...], "version": 1 } */
data class RulesResponse(
    @SerializedName("rules") val rules: List<ServerRule>?,
    @SerializedName("version") val version: Int? = null
)

/** Single rule from server: match="any", words=[...], sender="Leumi" */
data class ServerRule(
    @SerializedName("match") val match: String?,
    @SerializedName("words") val words: List<String>?,
    @SerializedName("sender") val sender: String?
)

class FetchFiltersWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(getFiltersUrl())
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLog.e("FetchFiltersWorker", "Failed to fetch filters: ${response.code}")
                return Result.failure()
            }

            val responseBody = response.body?.string()
            if (responseBody == null) {
                AppLog.e("FetchFiltersWorker", "Failed to fetch filters: empty response")
                return Result.failure()
            }

            AppLog.d(TAG, "Filters API raw response (${responseBody.length} chars): ${responseBody.take(1000)}")

            val rulesResponse = Gson().fromJson(responseBody, RulesResponse::class.java)
            val rules = rulesResponse.rules ?: emptyList()
            AppLog.d(TAG, "Parsed rules: ${rules.size}")

            // Convert server rules to internal SmsFilter: support both sender and body (words) matching
            val filters = mutableListOf<SmsFilter>()
            rules.forEachIndexed { index, rule ->
                val ruleId = rule.sender ?: "rule_$index"
                if (!rule.sender.isNullOrBlank()) {
                    filters.add(SmsFilter(id = "${ruleId}_sender", priority = 0, type = "sender", values = listOf(rule.sender!!)))
                }
                val words = rule.words ?: emptyList()
                if (words.isNotEmpty()) {
                    filters.add(SmsFilter(id = "${ruleId}_body", priority = 0, type = "body", values = words))
                }
            }
            AppLog.d(TAG, "Converted to ${filters.size} filters (sender + body)")
            SmsFilter.saveFilters(applicationContext, filters)
            FilterSystemState.setLastUpdated(applicationContext, System.currentTimeMillis().toString())

            // If this run had a pending message, re-check it with the new filters and send if it passes
            val pendingSender = inputData.getString(WebhookWorker.KEY_ORIGINATING_ADDRESS)
            val pendingBody = inputData.getString(WebhookWorker.KEY_MESSAGE_BODY)
            if (pendingSender != null && pendingBody != null) {
                if (SmsFilter.matches(pendingSender, pendingBody, filters)) {
                    AppLog.d(TAG, "Pending message passes new filters, sending to webhook: from=$pendingSender")
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val webhookRequest = OneTimeWorkRequestBuilder<WebhookWorker>()
                        .setConstraints(constraints)
                        .setInputData(workDataOf(
                            WebhookWorker.KEY_ORIGINATING_ADDRESS to pendingSender,
                            WebhookWorker.KEY_MESSAGE_BODY to pendingBody
                        ))
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(webhookRequest)
                } else {
                    AppLog.d(TAG, "Pending message does not pass new filters, not sending: from=$pendingSender (filters count=${filters.size})")
                }
            }

            return Result.success()
        } catch (e: Exception) {
            AppLog.e("FetchFiltersWorker", "Failed to fetch filters", e)
            return Result.retry()
        }
    }

    private fun getFiltersUrl(): String {
        return "https://dzqrjfxsgfymwqiabgwl.supabase.co/functions/v1/sms-filters"
    }

    companion object {
        private const val TAG = "FetchFiltersWorker"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<FetchFiltersWorker>(
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG, ExistingPeriodicWorkPolicy.KEEP, workRequest
            )
        }

        /** Enqueue a one-time fetch (e.g. when app has no filters and needs to load them).
         * If pendingSender and pendingBody are provided, after loading filters the worker will
         * re-check this message and send it to the webhook if it passes. */
        fun enqueueOnce(
            context: Context,
            pendingSender: String? = null,
            pendingBody: String? = null
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val builder = androidx.work.OneTimeWorkRequestBuilder<FetchFiltersWorker>()
                .setConstraints(constraints)
            if (pendingSender != null && pendingBody != null) {
                builder.setInputData(
                    androidx.work.workDataOf(
                        WebhookWorker.KEY_ORIGINATING_ADDRESS to pendingSender,
                        WebhookWorker.KEY_MESSAGE_BODY to pendingBody
                    )
                )
            }
            WorkManager.getInstance(context).enqueue(builder.build())
        }
    }
}
