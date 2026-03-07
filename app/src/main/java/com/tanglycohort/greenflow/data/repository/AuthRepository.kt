package com.tanglycohort.greenflow.data.repository

import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.supabase.SupabaseProvider
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AuthRepository {

    private val client get() = SupabaseProvider.client
    private val auth: Auth get() = client.auth

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = BuildConfig.SERVER_BASE_URL
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY

    suspend fun signUp(
        email: String,
        password: String,
        name: String,
        phone: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
            put("data", JSONObject().apply {
                put("name", name)
                put("email", email)
                put("phone", phone)
            })
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/signup")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("Sign up failed: ${response.code} $errBody")
            }
            val responseJson = JSONObject(response.body?.string() ?: "{}")
            val accessToken = responseJson.optString("access_token")
            val refreshToken = responseJson.optString("refresh_token")
            val expiresIn = responseJson.optLong("expires_in", 3600)
            if (accessToken.isNotBlank() && refreshToken.isNotBlank()) {
                auth.importSession(
                    UserSession(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = expiresIn,
                        tokenType = "Bearer",
                        user = null
                    )
                )
            }
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val request = Request.Builder()
            .url("$baseUrl/auth/v1/token?grant_type=password")
            .addHeader("apikey", anonKey)
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("Sign in failed: ${response.code} $errBody")
            }
            val responseJson = JSONObject(response.body?.string() ?: "{}")
            val accessToken = responseJson.optString("access_token")
            val refreshToken = responseJson.optString("refresh_token")
            val expiresIn = responseJson.optLong("expires_in", 3600)
            if (accessToken.isBlank() || refreshToken.isBlank()) throw Exception("Missing tokens in response")
            auth.importSession(
                UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresIn = expiresIn,
                    tokenType = "Bearer",
                    user = null
                )
            )
        }
    }

    fun getUserIdFromSession(): String? {
        val session = auth.currentSessionOrNull() ?: return null
        return decodeSubFromJwt(session.accessToken)
    }

    private fun decodeSubFromJwt(accessToken: String): String? {
        return try {
            val parts = accessToken.split(".")
            if (parts.size != 3) return null
            val payload = parts[1]
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            val json = JSONObject(String(decoded, Charsets.UTF_8))
            json.optString("sub").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signOut(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun currentSession() = auth.currentSessionOrNull()
    fun currentUser() = auth.currentUserOrNull()

    fun sessionStatus(): Flow<*> = auth.sessionStatus
}
