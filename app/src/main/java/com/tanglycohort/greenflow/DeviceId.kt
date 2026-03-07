package com.tanglycohort.greenflow

import android.content.Context
import java.util.UUID

object DeviceId {
    private const val PREFS_NAME = "device_id_prefs"
    private const val PREF_KEY = "device_id"

    fun get(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = sharedPreferences.getString(PREF_KEY, null)

        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(PREF_KEY, deviceId).apply()
        }

        return deviceId
    }
}
