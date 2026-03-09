package com.tanglycohort.greenflow

import android.app.Application
import android.os.SystemClock
import com.tanglycohort.greenflow.FetchFiltersWorker
import com.tanglycohort.greenflow.debug.DebugAgentLog
import com.tanglycohort.greenflow.supabase.SupabaseProvider

class GreenFlowApplication : Application() {

    override fun onCreate() {
        // #region agent log
        DebugAgentLog.log("GreenFlowApplication.kt:onCreate", "App onCreate start", emptyMap(), "H1")
        // #endregion
        super.onCreate()
        processStartTimeMs = SystemClock.elapsedRealtime()
        // Initialize Supabase client (singleton)
        @Suppress("UNUSED_EXPRESSION")
        SupabaseProvider.client
        // Schedule periodic filter fetch from server (hourly)
        FetchFiltersWorker.enqueue(applicationContext)
        // #region agent log
        DebugAgentLog.log("GreenFlowApplication.kt:onCreate", "App onCreate after Supabase", emptyMap(), "H1")
        // #endregion
    }

    companion object {
        @Volatile
        var processStartTimeMs: Long = 0L
            private set
    }
}
