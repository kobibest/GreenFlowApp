package com.tanglycohort.greenflow.service

import android.content.Context

object WebhookService {
    private const val PREFS_NAME = "webhook_prefs"
    private const val WEBHOOK_URL_KEY = "webhook_url"
    private const val USER_ID_KEY = "user_id"
    private const val SERVICE_ENABLED_KEY = "service_enabled"
    private const val PERSISTENT_BACKGROUND_KEY = "persistent_background"
    private const val MESSAGES_SENT_COUNT_KEY = "messages_sent_count"
    private const val FORWARD_ALL_WHEN_NO_FILTERS_KEY = "forward_all_when_no_filters"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getWebhookUrl(context: Context): String? =
        prefs(context).getString(WEBHOOK_URL_KEY, null)

    fun setWebhookUrl(context: Context, url: String) {
        prefs(context).edit().putString(WEBHOOK_URL_KEY, url).apply()
    }

    fun getUserId(context: Context): String? =
        prefs(context).getString(USER_ID_KEY, null)

    fun setUserId(context: Context, userId: String) {
        prefs(context).edit().putString(USER_ID_KEY, userId).apply()
    }

    fun isServiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(SERVICE_ENABLED_KEY, true)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SERVICE_ENABLED_KEY, enabled).apply()
    }

    fun isPersistentBackgroundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(PERSISTENT_BACKGROUND_KEY, false)

    fun setPersistentBackgroundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PERSISTENT_BACKGROUND_KEY, enabled).apply()
    }

    fun getMessagesSentCount(context: Context): Long =
        prefs(context).getLong(MESSAGES_SENT_COUNT_KEY, 0L)

    fun incrementMessagesSentCount(context: Context) {
        val p = prefs(context)
        val current = p.getLong(MESSAGES_SENT_COUNT_KEY, 0L)
        p.edit().putLong(MESSAGES_SENT_COUNT_KEY, current + 1L).apply()
    }

    /** When true, forward every SMS to the webhook when no filters are loaded (bypasses filter fetch). */
    fun isForwardAllWhenNoFilters(context: Context): Boolean =
        prefs(context).getBoolean(FORWARD_ALL_WHEN_NO_FILTERS_KEY, false)

    fun setForwardAllWhenNoFilters(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(FORWARD_ALL_WHEN_NO_FILTERS_KEY, enabled).apply()
    }
}
