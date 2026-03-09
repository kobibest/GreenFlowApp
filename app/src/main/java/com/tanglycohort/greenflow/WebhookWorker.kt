package com.tanglycohort.greenflow

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.service.WebhookService
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class WebhookWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val originatingAddress = inputData.getString(KEY_ORIGINATING_ADDRESS)
        val messageBody = inputData.getString(KEY_MESSAGE_BODY)
        val receivedAt = inputData.getLong(KEY_RECEIVED_AT, 0L)

        if (originatingAddress == null || messageBody == null) {
            AppLog.e(TAG, "Missing originating_address or message_body")
            return Result.failure()
        }

        val now = System.currentTimeMillis()
        if (receivedAt == 0L || now - receivedAt > RECENT_WINDOW_MS) {
            AppLog.d(TAG, "Skipping old message (received_at=$receivedAt, window=${RECENT_WINDOW_MS}ms)")
            return Result.failure()
        }
        if (runAttemptCount >= MAX_RETRIES) {
            AppLog.d(TAG, "Max retries ($MAX_RETRIES) reached, giving up")
            return Result.failure()
        }
        if (!WebhookService.isServiceEnabled(applicationContext)) {
            AppLog.d(TAG, "Service disabled, skipping webhook send: from=$originatingAddress")
            return Result.success()
        }

        val webhookUrl = WebhookService.getWebhookUrl(applicationContext)
        if (webhookUrl == null) {
            AppLog.e(TAG, "Webhook URL not set – open Dashboard after login to set URL, or enable 'forward all when no filters' after setting URL")
            return Result.failure()
        }
        // Refresh session so we use a non-expired token (avoids 401 Invalid JWT)
        runBlocking {
            runCatching { SupabaseProvider.client.auth.refreshCurrentSession() }
                .onFailure { AppLog.d(TAG, "Session refresh before webhook failed: ${it.message}") }
        }
        val tokenFromInput = inputData.getString(KEY_ACCESS_TOKEN)
        val accessToken = SupabaseProvider.client.auth.currentSessionOrNull()?.accessToken
            ?: tokenFromInput?.takeIf { it.isNotBlank() }
        AppLog.d(TAG, "Sending SMS to webhook: from=$originatingAddress url=${webhookUrl.take(60)}… hasAccessToken=${!accessToken.isNullOrBlank()}")

        val userId = WebhookService.getUserId(applicationContext)
        val payload = WebhookPayload(
            from = originatingAddress,
            body = messageBody,
            userId = userId
        )
        val jsonBody = gson.toJson(payload)
            .toRequestBody("application/json".toMediaType())

        val requestBuilder = Request.Builder()
            .url(webhookUrl)
            .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .post(jsonBody)
        if (!accessToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $accessToken")
        }
        val request = requestBuilder.build()

        return try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                val webhookResponse = parseWebhookResponse(responseBody)
                if (webhookResponse?.filtered == true) {
                    AppLog.d(TAG, "Server filtered message: ${webhookResponse.reason}")
                    SmsActivityLog.add(
                        applicationContext,
                        from = originatingAddress,
                        body = messageBody,
                        responseStatus = "filtered",
                        responseDetail = webhookResponse.reason ?: "נחסם על ידי השרת"
                    )
                    val filters = webhookResponse.updateFilters
                    if (!filters.isNullOrEmpty()) {
                        SmsFilter.saveFilters(applicationContext, filters)
                        FilterSystemState.setLastUpdated(
                            applicationContext,
                            System.currentTimeMillis().toString()
                        )
                        AppLog.d(TAG, "Updated local filters from server: ${filters.size} filters")
                    }
                } else {
                    AppLog.d(TAG, "SMS sent to webhook successfully: from=$originatingAddress")
                    SmsActivityLog.add(
                        applicationContext,
                        from = originatingAddress,
                        body = messageBody,
                        responseStatus = "success",
                        responseDetail = "נשלח בהצלחה לשרת"
                    )
                    WebhookService.incrementMessagesSentCount(applicationContext)
                }
                Result.success()
            } else {
                AppLog.e(TAG, "Webhook returned ${response.code}: $responseBody")
                SmsActivityLog.add(
                    applicationContext,
                    from = originatingAddress,
                    body = messageBody,
                    responseStatus = "error",
                    responseDetail = "שגיאה ${response.code}: $responseBody"
                )
                if (runAttemptCount + 1 >= MAX_RETRIES || System.currentTimeMillis() - receivedAt > RECENT_WINDOW_MS) {
                    return Result.failure()
                }
                return Result.retry()
            }
        } catch (e: IOException) {
            AppLog.e(TAG, "Failed to send SMS to webhook", e)
            SmsActivityLog.add(
                applicationContext,
                from = originatingAddress,
                body = messageBody,
                responseStatus = "error",
                responseDetail = "שגיאת רשת: ${e.message}"
            )
            if (runAttemptCount + 1 >= MAX_RETRIES || System.currentTimeMillis() - receivedAt > RECENT_WINDOW_MS) {
                return Result.failure()
            }
            return Result.retry()
        }
    }

    private fun parseWebhookResponse(body: String): WebhookResponse? {
        if (body.isBlank()) return null
        return try {
            gson.fromJson(body, WebhookResponse::class.java)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to parse webhook response", e)
            null
        }
    }

    companion object {
        private const val TAG = "WebhookWorker"
        const val KEY_ORIGINATING_ADDRESS = "originating_address"
        const val KEY_MESSAGE_BODY = "message_body"
        const val KEY_RECEIVED_AT = "received_at"
        const val KEY_ACCESS_TOKEN = "access_token"

        private const val MAX_RETRIES = 3
        private const val RECENT_WINDOW_MS = 3 * 60 * 1000L

        private val httpClient = OkHttpClient()
        private val gson = Gson()

        private data class WebhookPayload(
            val from: String,
            val body: String,
            @SerializedName("user_id") val userId: String? = null
        )

        private data class WebhookResponse(
            @SerializedName("filtered") val filtered: Boolean? = null,
            @SerializedName("reason") val reason: String? = null,
            @SerializedName("update_filters") val updateFilters: List<SmsFilter>? = null
        )
    }
}
