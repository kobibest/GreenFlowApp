package com.tanglycohort.greenflow

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single log entry: one SMS forward attempt with timestamp and server response.
 */
data class SmsActivityEntry(
    val timeMillis: Long,
    val from: String,
    val bodyPreview: String,
    val responseStatus: String,
    val responseDetail: String
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
}

/**
 * Persists and retrieves SMS activity log (last N entries).
 */
object SmsActivityLog {
    private const val PREFS_NAME = "sms_activity_log_prefs"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 200

    private val gson = Gson()
    private val type = object : TypeToken<List<SmsActivityEntry>>() {}.type

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun add(
        context: Context,
        from: String,
        body: String,
        responseStatus: String,
        responseDetail: String
    ) {
        val preview = if (body.length > 80) body.take(80) + "…" else body
        val entry = SmsActivityEntry(
            timeMillis = System.currentTimeMillis(),
            from = from,
            bodyPreview = preview,
            responseStatus = responseStatus,
            responseDetail = responseDetail
        )
        val list = getAll(context).toMutableList().apply { add(0, entry) }
        val trimmed = list.take(MAX_ENTRIES)
        prefs(context).edit().putString(KEY_ENTRIES, gson.toJson(trimmed)).apply()
    }

    fun getAll(context: Context): List<SmsActivityEntry> {
        val json = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            gson.fromJson<List<SmsActivityEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
