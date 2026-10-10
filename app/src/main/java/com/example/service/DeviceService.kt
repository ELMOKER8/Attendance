package com.example.service

import android.content.Context
import android.os.Build
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

object DeviceService {
    private const val PREFS_NAME = "device_security_prefs"
    private const val KEY_DEVICE_UUID = "device_uuid"

    // RFC 4122 OID Namespace UUID: 6ba7b812-9dad-11d1-80b4-00c04fd430c8
    private val NAMESPACE_OID = UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8")

    /**
     * Extracts or derives a permanent, hardware-bound device UUID v5.
     * Enforces a strict 1:1 student-to-device relationship (Anti-Proxy).
     */
    fun getDeviceUuid(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_UUID, null)
        if (!existing.isNullOrEmpty()) {
            return existing
        }

        val brand = Build.BRAND ?: "generic"
        val hardware = Build.HARDWARE ?: "hardware"
        val id = Build.ID ?: "unknown_id"
        val hardwareSeed = "$brand:$hardware:$id"

        val derivedUuid = generateUuidV5(NAMESPACE_OID, hardwareSeed)
        prefs.edit().putString(KEY_DEVICE_UUID, derivedUuid).apply()
        return derivedUuid
    }

    private fun generateUuidV5(namespace: UUID, name: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val nsBuffer = ByteBuffer.wrap(ByteArray(16))
        nsBuffer.putLong(namespace.mostSignificantBits)
        nsBuffer.putLong(namespace.leastSignificantBits)
        md.update(nsBuffer.array())
        md.update(name.toByteArray(Charsets.UTF_8))
        val hash = md.digest()

        var msb: Long = 0
        var lsb: Long = 0
        for (i in 0..7) {
            msb = (msb shl 8) or (hash[i].toLong() and 0xff)
        }
        for (i in 8..15) {
            lsb = (lsb shl 8) or (hash[i].toLong() and 0xff)
        }

        // Set version 5 (0101)
        msb = (msb and 0x000000000000F000L.inv()) or 0x0000000000005000L
        // Set variant RFC 4122 (10xx)
        lsb = (lsb and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE

        return UUID(msb, lsb).toString()
    }
}
