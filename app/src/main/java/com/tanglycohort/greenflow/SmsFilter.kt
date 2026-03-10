package com.tanglycohort.greenflow

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken

data class SmsFilter(
    @SerializedName("id") val id: String,
    @SerializedName("priority") val priority: Int,
    @SerializedName("type") val type: String,
    @SerializedName("values") val values: List<String>
) {
    companion object {
        private const val FILTERS_KEY = "sms_filters"
        private val gson = Gson()

        fun saveFilters(context: Context, filters: List<SmsFilter>) {
            AppLog.d("SmsFilter", "Saving ${filters.size} filters")

            val sharedPreferences = context.getSharedPreferences(FILTERS_KEY, Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            val json = gson.toJson(filters)
            editor.putString(FILTERS_KEY, json)
            editor.apply()
        }

        fun getFilters(context: Context): List<SmsFilter>? {
            val sharedPreferences = context.getSharedPreferences(FILTERS_KEY, Context.MODE_PRIVATE)
            val json = sharedPreferences.getString(FILTERS_KEY, null)

            if (json != null) {
                val type = object : TypeToken<List<SmsFilter>>() {}.type
                return gson.fromJson(json, type)
            }

            return null
        }

        /**
         * Returns true if the message should be sent to webhook (passes at least one rule).
         * Within each rule: AND – sender must match AND body must contain at least one of the words.
         * Between rules: OR – one matching rule is enough.
         * When filters is null or empty, returns false (do not send – whitelist only).
         */
        fun matches(
            originatingAddress: String?,
            messageBody: String?,
            filters: List<SmsFilter>?
        ): Boolean {
            if (filters == null || filters.isEmpty()) return false
            val bodyStr = messageBody ?: ""
            // Group by rule id (id without _sender / _body suffix)
            val rules = filters.groupBy { f ->
                when {
                    f.id.endsWith("_sender") -> f.id.removeSuffix("_sender")
                    f.id.endsWith("_body") -> f.id.removeSuffix("_body")
                    else -> f.id
                }
            }
            return rules.values.any { ruleFilters ->
                val senderFilter = ruleFilters.find { it.type == "sender" }
                val bodyFilter = ruleFilters.find { it.type == "body" }
                val senderMatches = senderFilter?.values?.any { it == originatingAddress } == true
                val bodyMatches = bodyFilter == null ||
                    bodyFilter.values.any { bodyStr.contains(it) }
                senderMatches && bodyMatches
            }
        }
    }
}
