package com.tanglycohort.greenflow.debug

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends one NDJSON log line to the debug server (host 10.0.2.2 from emulator).
 * Fire-and-forget; never throws. Used only for debug-session instrumentation.
 */
object DebugAgentLog {
    private const val ENDPOINT = "http://10.0.2.2:7526/ingest/bab0f495-4331-472e-8b0f-117b8c801a0f"
    private const val SESSION_ID = "f49a62"

    fun log(location: String, message: String, data: Map<String, Any?> = emptyMap(), hypothesisId: String? = null) {
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("sessionId", SESSION_ID)
                    put("location", location)
                    put("message", message)
                    put("timestamp", System.currentTimeMillis())
                    if (hypothesisId != null) put("hypothesisId", hypothesisId)
                    if (data.isNotEmpty()) put("data", JSONObject(data.mapValues { (_, v) -> v?.toString() ?: "null" }))
                }
                val url = URL(ENDPOINT)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                conn.doOutput = true
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            } catch (_: Throwable) { }
        }.start()
    }
}
