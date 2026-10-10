package com.example

import com.example.service.DeviceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DeviceServiceTest {

    @Test
    fun testDeviceUuidGenerationIsDeterministicAndNonNull() {
        val context = RuntimeEnvironment.getApplication()
        val uuid1 = DeviceService.getDeviceUuid(context)
        assertNotNull(uuid1)
        assertEquals(36, uuid1.length) // Standard UUID length

        // Second call should return the exact same persisted UUID
        val uuid2 = DeviceService.getDeviceUuid(context)
        assertEquals(uuid1, uuid2)
    }
}
