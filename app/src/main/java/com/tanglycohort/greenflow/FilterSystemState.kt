package com.tanglycohort.greenflow

import android.content.Context
import com.google.gson.Gson

object FilterSystemState {
    private const val PREFS_NAME = "filter_system_state"
    private const val LAST_UPDATED_KEY = "last_updated"

    fun setLastUpdated(context: Context, lastUpdated: String) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString(LAST_UPDATED_KEY, lastUpdated)
        editor.apply()
    }

    fun getLastUpdated(context: Context): String? {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(LAST_UPDATED_KEY, null)
    }
}
