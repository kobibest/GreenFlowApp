package com.tanglycohort.greenflow

import android.app.Application
import android.os.SystemClock
import com.tanglycohort.greenflow.supabase.SupabaseProvider

class GreenFlowApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        processStartTimeMs = SystemClock.elapsedRealtime()
        // Initialize Supabase client (singleton)
        @Suppress("UNUSED_EXPRESSION")
        SupabaseProvider.client
    }

    companion object {
        @Volatile
        var processStartTimeMs: Long = 0L
            private set
    }
}
