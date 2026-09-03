package com.osai.voiceassistant.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.osai.voiceassistant.service.CallStateListener
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.utils.Logger

/**
 * Manifest-registered receiver for the legacy ACTION_PHONE_STATE broadcast.
 * Only requires READ_PHONE_STATE (already requested) to observe ringing
 * state; the incoming number extra may be withheld by the platform on some
 * OEM builds without READ_CALL_LOG, in which case the announcement falls
 * back to a generic phrase (see [CallStateMonitor.buildAnnouncement]).
 */
class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val ridingModeEnabled = SharedPreferencesManager.getInstance(context).current().ridingModeEnabled
        if (!ridingModeEnabled) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                Logger.i(TAG, "Incoming call ringing (number present=${incomingNumber != null})")
                CallStateListener.getInstance(context).onIncomingCall(incomingNumber)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                CallStateListener.getInstance(context).onCallEnded()
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Logger.d(TAG, "Call answered / offhook")
            }
        }
    }

    companion object {
        private const val TAG = "CallStateReceiver"
    }
}
