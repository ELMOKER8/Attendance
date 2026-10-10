package com.example.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

sealed class AttendanceResult {
    data class Success(val fullName: String, val message: String) : AttendanceResult()
    data class AntiProxyConflict(val message: String) : AttendanceResult()
    data class ExpiredToken(val message: String) : AttendanceResult()
    data class NetworkError(val message: String) : AttendanceResult()
    data class InvalidPayload(val message: String) : AttendanceResult()
}

object ApiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun submitAttendance(
        serverUrl: String,
        token: String,
        studentId: String,
        nationalId: String,
        fullName: String,
        deviceUuid: String
    ): AttendanceResult = withContext(Dispatchers.IO) {
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) {
            return@withContext AttendanceResult.InvalidPayload("Invalid server URL scheme. Expected http:// or https://")
        }

        val jsonBody = JSONObject().apply {
            put("token", token)
            put("student_id", studentId)
            put("national_id", nationalId)
            put("full_name", fullName)
            put("device_uuid", deviceUuid)
        }.toString()

        val request = Request.Builder()
            .url(serverUrl)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> AttendanceResult.Success(
                        fullName = fullName,
                        message = "Attendance recorded successfully."
                    )
                    409 -> AttendanceResult.AntiProxyConflict(
                        message = "Anti-Proxy Alert: This device has already logged attendance for another student!"
                    )
                    401 -> AttendanceResult.ExpiredToken(
                        message = "Expired QR token. Please scan the current code on screen."
                    )
                    else -> AttendanceResult.NetworkError(
                        message = "Server returned code ${response.code}: ${response.message.ifEmpty { "Unexpected response" }}"
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            AttendanceResult.NetworkError(
                message = "Cannot reach server. Ensure you are connected to the lecture Wi-Fi."
            )
        } catch (e: IOException) {
            AttendanceResult.NetworkError(
                message = "Cannot reach server. Ensure you are connected to the lecture Wi-Fi."
            )
        } catch (e: Exception) {
            AttendanceResult.NetworkError(
                message = "Cannot reach server. Ensure you are connected to the lecture Wi-Fi."
            )
        }
    }
}
