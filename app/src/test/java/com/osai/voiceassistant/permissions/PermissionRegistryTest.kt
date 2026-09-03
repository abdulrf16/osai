package com.osai.voiceassistant.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRegistryTest {

    @Test
    fun `bluetooth permissions use BLUETOOTH_CONNECT and SCAN on API 31+`() {
        val permissions = PermissionRegistry.manifestPermissionsFor(PermissionId.BLUETOOTH, sdkInt = 31)
        assertTrue(permissions.contains("android.permission.BLUETOOTH_CONNECT"))
        assertTrue(permissions.contains("android.permission.BLUETOOTH_SCAN"))
        assertFalse(permissions.contains("android.permission.BLUETOOTH"))
    }

    @Test
    fun `bluetooth permissions fall back to legacy strings below API 31`() {
        val permissions = PermissionRegistry.manifestPermissionsFor(PermissionId.BLUETOOTH, sdkInt = 30)
        assertTrue(permissions.contains("android.permission.BLUETOOTH"))
        assertTrue(permissions.contains("android.permission.BLUETOOTH_ADMIN"))
    }

    @Test
    fun `notifications permission only required on API 33+`() {
        val below = PermissionRegistry.manifestPermissionsFor(PermissionId.NOTIFICATIONS, sdkInt = 32)
        val atOrAbove = PermissionRegistry.manifestPermissionsFor(PermissionId.NOTIFICATIONS, sdkInt = 33)

        assertTrue(below.isEmpty())
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), atOrAbove)
    }

    @Test
    fun `microphone permission is always RECORD_AUDIO regardless of sdk`() {
        assertEquals(
            listOf("android.permission.RECORD_AUDIO"),
            PermissionRegistry.manifestPermissionsFor(PermissionId.MICROPHONE, sdkInt = 21)
        )
        assertEquals(
            listOf("android.permission.RECORD_AUDIO"),
            PermissionRegistry.manifestPermissionsFor(PermissionId.MICROPHONE, sdkInt = 35)
        )
    }

    @Test
    fun `notification listener has no runtime manifest permission`() {
        assertTrue(PermissionRegistry.manifestPermissionsFor(PermissionId.NOTIFICATION_LISTENER, sdkInt = 35).isEmpty())
    }

    @Test
    fun `phone state permission is always READ_PHONE_STATE`() {
        assertEquals(
            listOf("android.permission.READ_PHONE_STATE"),
            PermissionRegistry.manifestPermissionsFor(PermissionId.PHONE_STATE, sdkInt = 35)
        )
    }
}
