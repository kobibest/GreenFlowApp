package com.tanglycohort.greenflow.data.repository

import com.tanglycohort.greenflow.data.model.Subscription
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit

class SubscriptionsRepository {

    private val client get() = SupabaseProvider.client

    suspend fun getActiveSubscription(userId: String): Subscription? = withContext(Dispatchers.IO) {
        runCatching {
            client.from("subscriptions").select {
                filter {
                    eq("user_id", userId)
                    isIn("status", listOf("active", "trial", "blocked"))
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
        runCatching {
            client.from("subscriptions").insert(
                mapOf(
                    "user_id" to userId,
                    "status" to "trial",
                    "starts_at" to now.toString(),
                    "ends_at" to endsAt.toString()
                )
            )
        }.map { }
    }

    /** Create active subscription for a plan. ends_at = now + plan.duration_days if plan has duration. */
    suspend fun subscribeToPlan(userId: String, planId: String, durationDays: Int?): Result<Unit> = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val endsAt = durationDays?.let { now.plus(it.toLong(), ChronoUnit.DAYS) }
        val payload = mutableMapOf<String, Any?>(
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
}
