package com.tanglycohort.greenflow.bugreport

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import com.tanglycohort.greenflow.AppLog
import com.tanglycohort.greenflow.BuildConfig
import com.tanglycohort.greenflow.DeviceId
import com.tanglycohort.greenflow.GreenFlowApplication
import com.tanglycohort.greenflow.service.WebhookService
import android.os.SystemClock

object BugReportCollector {

    fun collect(
        context: Context,
        description: String? = null,
        errorMessage: String? = null,
        stackTrace: String? = null
    ): BugReportPayload {
        val deviceId = DeviceId.get(context)
        val userId = WebhookService.getUserId(context)
        val appVersion = BuildConfig.VERSION_NAME
        val androidVersion = Build.VERSION.RELEASE
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
        val logs = AppLog.getLogsForReport()
        val metadata = collectMetadata(context)

        return BugReportPayload(
            deviceId = deviceId,
            userId = userId,
            appVersion = appVersion,
            androidVersion = androidVersion,
            deviceModel = deviceModel,
            description = description?.takeIf { it.isNotBlank() },
            errorMessage = errorMessage?.takeIf { it.isNotBlank() },
            stackTrace = stackTrace?.takeIf { it.isNotBlank() },
            logs = logs,
            metadata = metadata
        )
    }

    private fun collectMetadata(context: Context): BugReportMetadata {
        val batteryLevel: Int? = try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && bm != null) {
                bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                @Suppress("DEPRECATION")
                val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
            }
        } catch (_: Exception) { null }

        val isCharging: Boolean? = try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bm != null) {
                bm.isCharging
            } else {
                @Suppress("DEPRECATION")
                val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == android.os.BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (_: Exception) { null }

        val networkType: String = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = if (cm != null && network != null) cm.getNetworkCapabilities(network) else null
            when {
                caps == null -> "none"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                else -> "none"
            }
        } catch (_: Exception) { "none" }

        return BugReportMetadata(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            networkType = networkType,
            freeMemoryMb = freeMemoryMb(),
            uptimeMinutes = uptimeMinutes()
        )
    }

    private fun freeMemoryMb(): Double {
        return try {
            Runtime.getRuntime().freeMemory() / (1024.0 * 1024.0)
        } catch (_: Exception) { 0.0 }
    }

    private fun uptimeMinutes(): Long? {
        val start = GreenFlowApplication.processStartTimeMs
        if (start == 0L) return null
        return (SystemClock.elapsedRealtime() - start) / 60
    }
}
