package com.osai.voiceassistant

import android.app.Application
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.utils.Logger

class VoiceAssistantApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Warm up the settings singleton so every component reads a
        // consistent snapshot from the very first frame.
        SharedPreferencesManager.getInstance(this)
        Logger.i(TAG, "Application initialized")
    }

    companion object {
        private const val TAG = "VoiceAssistantApplication"
    }
}
