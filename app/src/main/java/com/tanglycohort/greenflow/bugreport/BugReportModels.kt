package com.tanglycohort.greenflow.bugreport

import com.google.gson.annotations.SerializedName

data class BugReportPayload(
    @SerializedName("device_id") val deviceId: String? = null,
    @SerializedName("user_id") val userId: String? = null,
    @SerializedName("app_version") val appVersion: String? = null,
    @SerializedName("android_version") val androidVersion: String? = null,
    @SerializedName("device_model") val deviceModel: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("error_message") val errorMessage: String? = null,
    @SerializedName("stack_trace") val stackTrace: String? = null,
    @SerializedName("logs") val logs: List<BugReportLogEntry> = emptyList(),
    @SerializedName("metadata") val metadata: BugReportMetadata? = null,
    @SerializedName("status") val status: String = "new"
)

data class BugReportLogEntry(
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("level") val level: String,
    @SerializedName("tag") val tag: String,
    @SerializedName("message") val message: String
)

data class BugReportMetadata(
    @SerializedName("battery_level") val batteryLevel: Int? = null,
    @SerializedName("is_charging") val isCharging: Boolean? = null,
    @SerializedName("network_type") val networkType: String? = null,
    @SerializedName("free_memory_mb") val freeMemoryMb: Double? = null,
    @SerializedName("uptime_minutes") val uptimeMinutes: Long? = null
)
