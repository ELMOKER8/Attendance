package com.example.service

import android.content.Context
import android.content.SharedPreferences

data class StudentProfile(
    val fullName: String,
    val studentId: String,
    val nationalId: String,
    val deviceUuid: String,
    val isEnrolled: Boolean
)

object StorageService {
    private const val PREFS_NAME = "attendance_prefs"
    private const val KEY_IS_ENROLLED = "is_enrolled"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_STUDENT_ID = "student_id"
    private const val KEY_NATIONAL_ID = "national_id"
    private const val KEY_DEVICE_UUID = "device_uuid"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnrolled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_ENROLLED, false)
    }

    fun saveEnrollment(
        context: Context,
        fullName: String,
        studentId: String,
        nationalId: String,
        deviceUuid: String
    ) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_ENROLLED, true)
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_STUDENT_ID, studentId)
            .putString(KEY_NATIONAL_ID, nationalId)
            .putString(KEY_DEVICE_UUID, deviceUuid)
            .apply()
    }

    fun getStudentProfile(context: Context): StudentProfile {
        val prefs = getPrefs(context)
        return StudentProfile(
            fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
            studentId = prefs.getString(KEY_STUDENT_ID, "") ?: "",
            nationalId = prefs.getString(KEY_NATIONAL_ID, "") ?: "",
            deviceUuid = prefs.getString(KEY_DEVICE_UUID, "") ?: "",
            isEnrolled = prefs.getBoolean(KEY_IS_ENROLLED, false)
        )
    }
}
