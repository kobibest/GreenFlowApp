package com.tanglycohort.greenflow

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.service.WebhookService
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.runBlocking
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
        val tokenFromInput = inputData.getString(KEY_ACCESS_TOKEN)
        // Refresh session so we use a non-expired token (avoids 401 Invalid JWT)
        val refreshOk = runBlocking {
            runCatching { SupabaseProvider.client.auth.refreshCurrentSession() }
                .onFailure { AppLog.d(TAG, "Session refresh before fetch filters failed: ${it.message}") }
                .isSuccess
        }
        val accessToken = SupabaseProvider.client.auth.currentSessionOrNull()?.accessToken
            ?: tokenFromInput?.takeIf { it.isNotBlank() }
        if (accessToken.isNullOrBlank()) {
            AppLog.d(TAG, "No user session, skipping filter fetch")
            return Result.failure()
        }
        if (!refreshOk && tokenFromInput.isNullOrBlank()) {
            AppLog.d(TAG, "Session refresh failed and no token in input, retrying later")
            return Result.retry()
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url(getFiltersUrl())
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                AppLog.e("FetchFiltersWorker", "Failed to fetch filters: ${response.code} body=$errBody")
                // 401 Invalid JWT: token may be expired; retry so next run can refresh again
                if (response.code == 401) return Result.retry()
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
            val receivedAt = inputData.getLong(WebhookWorker.KEY_RECEIVED_AT, 0L)
            if (pendingSender != null && pendingBody != null) {
                if (!WebhookService.isServiceEnabled(applicationContext)) {
                    AppLog.d(TAG, "Service disabled, not sending pending message to webhook: from=$pendingSender")
                } else if (SmsFilter.matches(pendingSender, pendingBody, filters)) {
                    AppLog.d(TAG, "Pending message passes new filters, sending to webhook: from=$pendingSender")
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                    val webhookData = workDataOf(
                        WebhookWorker.KEY_ORIGINATING_ADDRESS to pendingSender,
                        WebhookWorker.KEY_MESSAGE_BODY to pendingBody,
                        WebhookWorker.KEY_RECEIVED_AT to receivedAt,
                        WebhookWorker.KEY_ACCESS_TOKEN to (accessToken ?: "")
                    )
                    val webhookRequest = OneTimeWorkRequestBuilder<WebhookWorker>()
                        .setConstraints(constraints)
                        .setInputData(webhookData)
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
        return "${com.tanglycohort.greenflow.BuildConfig.SERVER_BASE_URL}/functions/v1/sms-filters"
    }

    companion object {
        private const val TAG = "FetchFiltersWorker"

        /** Key for passing JWT access token to the worker (optional). When set, request includes Authorization header. */
        const val KEY_ACCESS_TOKEN = "access_token"

        /** Unique work name for one-time filter fetch; use this to observe completion in the UI. */
        const val ONE_TIME_WORK_NAME = "FetchFiltersOnce"

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
         * re-check this message and send it to the webhook if it passes.
         * @param accessToken optional JWT; when provided, the request includes Authorization for the Edge Function.
         * @param receivedAtMillis time when the SMS was received (for 3-minute freshness window). */
        fun enqueueOnce(
            context: Context,
            pendingSender: String? = null,
            pendingBody: String? = null,
            receivedAtMillis: Long = 0L,
            accessToken: String? = null
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val pairs = mutableListOf<Pair<String, Any?>>()
            if (!accessToken.isNullOrBlank()) {
                pairs.add(KEY_ACCESS_TOKEN to accessToken)
            }
            if (pendingSender != null && pendingBody != null) {
                pairs.add(WebhookWorker.KEY_ORIGINATING_ADDRESS to pendingSender)
                pairs.add(WebhookWorker.KEY_MESSAGE_BODY to pendingBody)
                pairs.add(WebhookWorker.KEY_RECEIVED_AT to receivedAtMillis)
            }
            val builder = OneTimeWorkRequestBuilder<FetchFiltersWorker>()
                .setConstraints(constraints)
            if (pairs.isNotEmpty()) {
                builder.setInputData(workDataOf(*pairs.toTypedArray()))
            }
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                builder.build()
            )
        }
    }
}
