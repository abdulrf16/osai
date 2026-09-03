package com.osai.voiceassistant.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.osai.voiceassistant.utils.Logger

/**
 * Resolves what to say for an incoming call and de-duplicates repeated
 * broadcasts for the same ringing event. [buildAnnouncement] is pure and
 * unit tested directly; [resolveContactName] needs a [Context] to query the
 * contacts provider and is only exercised on-device.
 *
 * MVP 1 only requests READ_PHONE_STATE (see AndroidManifest) - it does not
 * request READ_CONTACTS, so [resolveContactName] will normally return null
 * and the announcement gracefully falls back to reading the phone number,
 * or a generic phrase when no number is available (e.g. private numbers).
 */
class CallStateMonitor(private val context: Context) {

    private var lastAnnouncedNumber: String? = null

    fun shouldAnnounce(phoneNumber: String?): Boolean {
        if (phoneNumber == lastAnnouncedNumber) return false
        lastAnnouncedNumber = phoneNumber
        return true
    }

    fun resetDebounce() {
        lastAnnouncedNumber = null
    }

    fun buildCallAnnouncement(phoneNumber: String?): String {
        val contactName = phoneNumber?.let { resolveContactName(it) }
        return buildAnnouncement(phoneNumber, contactName)
    }

    private fun resolveContactName(phoneNumber: String): String? {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPermission) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Contact lookup failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "CallStateMonitor"

        /** Pure - testable without Android framework classes. */
        fun buildAnnouncement(phoneNumber: String?, contactName: String?): String {
            val who = when {
                !contactName.isNullOrBlank() -> contactName
                !phoneNumber.isNullOrBlank() -> phoneNumber
                else -> "unknown number"
            }
            return "Incoming call from $who"
        }
    }
}
