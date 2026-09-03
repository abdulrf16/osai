package com.osai.voiceassistant.utils

import android.util.Log

/**
 * Thin wrapper around [Log] that centralizes tagging and guarantees that
 * sensitive data (message bodies, phone numbers, contact names, raw speech
 * transcripts) is never written to logcat. Callers should use [redacted]
 * whenever a value might contain user content.
 */
object Logger {

    private const val GLOBAL_TAG = "RidingAssistant"

    fun d(tag: String, message: String) {
        Log.d("$GLOBAL_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$GLOBAL_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$GLOBAL_TAG:$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$GLOBAL_TAG:$tag", message, throwable)
    }

    /**
     * Replaces a potentially sensitive value with a length-only marker so that
     * logs remain useful for debugging control flow without leaking user data
     * such as message text, caller names, or transcripts.
     */
    fun redacted(value: String?): String {
        if (value == null) return "<null>"
        if (value.isEmpty()) return "<empty>"
        return "<redacted:${value.length}chars>"
    }
}
