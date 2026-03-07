package com.tanglycohort.greenflow

import android.util.Log
import com.tanglycohort.greenflow.bugreport.BugReportLogEntry
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central logger that writes to both android.util.Log and an in-memory buffer
 * used for bug reports. Keeps entries from the last 24 hours.
 */
object AppLog {
    private const val RETENTION_HOURS = 24L

    private data class Entry(
        val timestampMillis: Long,
        val level: String,
        val tag: String,
        val message: String
    )

    private val buffer = CopyOnWriteArrayList<Entry>()

    private fun add(level: String, tag: String, message: String) {
        val now = System.currentTimeMillis()
        buffer.add(Entry(now, level, tag, message))
        evictOlderThan24h()
    }

    private fun evictOlderThan24h() {
        val cutoff = System.currentTimeMillis() - RETENTION_HOURS * 60 * 60 * 1000
        buffer.removeAll { it.timestampMillis < cutoff }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        add("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        add("INFO", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        add("WARN", tag, message)
    }

    fun e(tag: String, message: String) {
        Log.e(tag, message)
        add("ERROR", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
        val fullMessage = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        add("ERROR", tag, fullMessage)
    }

    /**
     * Returns log entries from the last 24 hours for bug report payload.
     * Timestamps in ISO 8601 format.
     */
    fun getLogsForReport(): List<BugReportLogEntry> {
        evictOlderThan24h()
        val cutoff = System.currentTimeMillis() - RETENTION_HOURS * 60 * 60 * 1000
        return buffer.filter { it.timestampMillis >= cutoff }
            .map { e ->
                BugReportLogEntry(
                    timestamp = Instant.ofEpochMilli(e.timestampMillis).toString(),
                    level = e.level,
                    tag = e.tag,
                    message = e.message
                )
            }
    }
}
