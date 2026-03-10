package com.tanglycohort.greenflow.data.repository

import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.data.model.Subscription
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class SubscriptionsRepository {

    private val client get() = SupabaseProvider.client

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getActiveSubscription(userId: String): Subscription? = withContext(Dispatchers.IO) {
        val now = Instant.now()
        runCatching {
            client.from("subscriptions").select {
                filter {
                    eq("user_id", userId)
                    or {
                        isIn("status", listOf("active", "trial", "blocked"))
                        and {
                            eq("status", "cancelled")
                            gt("ends_at", now.toString())
                        }
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<Subscription>().firstOrNull()
        }.getOrNull()
    }

    suspend fun hasEverHadSubscription(userId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val list = client.from("subscriptions").select(Columns.list("id")) {
                filter { eq("user_id", userId) }
                limit(1)
            }.decodeList<Subscription>()
            list.isNotEmpty()
        }.getOrDefault(false)
    }

    suspend fun insertTrial(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val endsAt = now.plus(7, ChronoUnit.DAYS)
        val payload = mapOf(
            "user_id" to userId,
            "status" to "trial",
            "starts_at" to now.toString(),
            "ends_at" to endsAt.toString()
        )
        runCatching {
            client.from("subscriptions").insert(payload)
        }.map { }
    }

    /** Create active subscription for a plan (direct insert, e.g. for testing). Prefer verifyPlayPurchase for real payments. */
    suspend fun subscribeToPlan(userId: String, planId: String, durationDays: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val endsAt = durationDays?.let { now.plus(it.toLong(), ChronoUnit.DAYS) }
        val payload = mutableMapOf<String, String>(
            "user_id" to userId,
            "plan_id" to planId,
            "status" to "active",
            "starts_at" to now.toString()
        )
        if (endsAt != null) payload["ends_at"] = endsAt.toString()
        runCatching {
            client.from("subscriptions").insert(payload)
        }.map { }
    }

    /**
     * After a successful Google Play purchase, verify with backend and let server update subscriptions.
     * Server derives user from JWT and plan from product_id; only after this succeeds should the app refresh dashboard.
     */
    suspend fun verifyPlayPurchase(
        accessToken: String,
        purchaseToken: String,
        productId: String,
        packageName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("purchase_token", purchaseToken)
            put("product_id", productId)
            put("package_name", packageName)
        }
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL}/functions/v1/verify-play-purchase")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("Verify purchase failed: ${response.code} $errBody")
            }
        }
    }
}
