package com.tanglycohort.greenflow.data.repository

import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.data.model.ActivationCode
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ActivationCodesRepository {

    private val client get() = SupabaseProvider.client
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun getLatestActivationCode(userId: String): ActivationCode? = withContext(Dispatchers.IO) {
        runCatching {
            client.from("activation_codes").select {
                filter { eq("user_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<ActivationCode>().firstOrNull()
        }.getOrNull()
    }

    suspend fun insertActivationCode(userId: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.from("activation_codes").insert(
                mapOf(
                    "user_id" to userId,
                    "code" to code
                )
            )
        }.map { }
    }

    suspend fun invalidateLatestUnusedCode(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val latest = getLatestActivationCode(userId) ?: return@runCatching
            if (latest.status != "unused") return@runCatching
            val id = latest.id ?: return@runCatching
            client.from("activation_codes").update(
                mapOf(
                    "status" to "used",
                    "used_at" to java.time.Instant.now().toString()
                )
            ) {
                filter { eq("id", id) }
            }
            client.from("profiles").update(mapOf("device_id" to null)) {
                filter { eq("user_id", userId) }
            }
        }.map { }
    }

    suspend fun generateActivationCodeRpc(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val session = client.auth.currentSessionOrNull()
            val token = session?.accessToken
            val request = Request.Builder()
                .url("${BuildConfig.SERVER_BASE_URL}/rest/v1/rpc/generate_activation_code")
                .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("RPC failed: ${response.code}")
            val body = response.body?.string()?.trim() ?: throw Exception("Empty RPC response")
            body.trim('"')
        }
    }

    suspend fun sendCodeWhatsApp(userId: String, code: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("user_id", userId)
            put("code", code)
        }
        val request = Request.Builder()
            .url("${BuildConfig.SERVER_BASE_URL}/functions/v1/send-code-whatsapp")
            .addHeader("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("send-code-whatsapp failed: ${response.code}")
        }
    }
}
