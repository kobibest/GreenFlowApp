package com.tanglycohort.greenflow.bugreport

import com.tanglycohort.greenflow.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object BugReportSender {

    private val httpClient = OkHttpClient()
    private val gson = Gson()

    fun send(payload: BugReportPayload, callback: (Result<BugReportResponse>) -> Unit) {
        val url = BuildConfig.BUG_REPORT_URL
        val json = gson.toJson(payload)
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val parsed = try {
                        gson.fromJson(responseBody, BugReportResponse::class.java)
                    } catch (_: Exception) {
                        BugReportResponse(success = true, reportId = null)
                    }
                    callback(Result.success(parsed))
                } else {
                    callback(Result.failure(IOException("HTTP ${response.code}: $responseBody")))
                }
            }
        })
    }
}

data class BugReportResponse(
    val success: Boolean? = null,
    @SerializedName("report_id") val reportId: String? = null
)
