package com.osai.voiceassistant.permissions

/** A single permission the app cares about, plus rider-facing copy. */
data class PermissionItem(
    val id: PermissionId,
    val manifestPermissions: List<String>,
    val isSpecialAccess: Boolean = false
)

/** Logical identifiers, decoupled from raw manifest permission strings. */
enum class PermissionId {
    MICROPHONE,
    BLUETOOTH,
    PHONE_STATE,
    NOTIFICATIONS,
    NOTIFICATION_LISTENER
}

/** Aggregate, immutable snapshot of every permission's grant state. */
data class PermissionStatus(
    val granted: Map<PermissionId, Boolean>
) {
    val allGranted: Boolean get() = granted.values.all { it }
    val allCriticalGranted: Boolean get() =
        granted.filterKeys { it != PermissionId.NOTIFICATION_LISTENER }.values.all { it }

    fun isGranted(id: PermissionId): Boolean = granted[id] == true
}
