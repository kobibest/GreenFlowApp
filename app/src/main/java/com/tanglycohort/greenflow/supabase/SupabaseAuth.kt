package com.tanglycohort.greenflow.supabase

import com.tanglycohort.greenflow.AppLog
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Synchronized auth helper to avoid race conditions when multiple callers
 * (e.g. UI refresh and FetchFiltersWorker) need a valid access token.
 * Uses a single lock so all callers wait until one refresh completes.
 */
object SupabaseAuth {

    private const val TAG = "SupabaseAuth"
    /** Refresh proactively when token expires within this many seconds. */
    private const val EXPIRY_BUFFER_SECONDS = 30L

    private val lock = ReentrantLock()

    /**
     * Returns a valid access token, blocking until one is available.
     * - Uses [ReentrantLock] so only one refresh runs at a time; others wait.
     * - Double-check: after acquiring lock, if current token is still valid (with buffer), use it.
     * - If [forceRefresh] is true, always triggers a refresh before returning.
     * @return access token or null if no session / refresh failed
     */
    fun getValidAccessToken(forceRefresh: Boolean = false): String? = lock.withLock {
        val auth = SupabaseProvider.client.auth
        // Wait for session to finish loading from storage before reading/refreshing
        runBlocking {
            auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
        }
        if (!forceRefresh) {
            val session = auth.currentSessionOrNull()
            if (session == null) {
                val out = doRefresh(auth)
                if (out != null) logTokenReturned(out)
                return@withLock out
            }
            val token = session.accessToken
            if (token.isNotBlank() && !isExpiredWithin(token, EXPIRY_BUFFER_SECONDS)) {
                logTokenReturned(token)
                return@withLock token
            }
        }
        val refreshed = doRefresh(auth)
        if (refreshed != null) logTokenReturned(refreshed)
        refreshed
    }

    private fun logTokenReturned(token: String) {
        val exp = getExpFromJwt(token)
        val secsLeft = exp?.let { it - (System.currentTimeMillis() / 1000) } ?: -1L
        AppLog.d(TAG, "Returning token, exp in ${secsLeft}s, length=${token.length}")
    }

    private fun doRefresh(auth: io.github.jan.supabase.gotrue.Auth): String? {
        return runCatching {
            runBlocking { auth.refreshCurrentSession() }
        }.onFailure {
            AppLog.d(TAG, "Session refresh failed: ${it.message}")
        }.fold(
            onSuccess = {
                val token = auth.currentSessionOrNull()?.accessToken?.takeIf { t -> t.isNotBlank() }
                if (token != null && isExpiredWithin(token, EXPIRY_BUFFER_SECONDS)) {
                    AppLog.d(TAG, "Token after refresh still expired or within buffer, returning null")
                    return@fold null
                }
                token
            },
            onFailure = { null }
        )
    }

    private fun isExpiredWithin(accessToken: String, bufferSeconds: Long): Boolean {
        val exp = getExpFromJwt(accessToken) ?: return true
        return (exp - bufferSeconds) <= (System.currentTimeMillis() / 1000)
    }

    private fun getExpFromJwt(accessToken: String): Long? {
        return try {
            val parts = accessToken.split(".")
            if (parts.size != 3) return null
            val payload = parts[1]
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            val json = JSONObject(String(decoded, Charsets.UTF_8))
            if (json.has("exp")) json.getLong("exp") else null
        } catch (_: Exception) {
            null
        }
    }
}
