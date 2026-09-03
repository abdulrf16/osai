package com.osai.voiceassistant.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.osai.voiceassistant.utils.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists [AppSettings] to [SharedPreferences] and exposes the current
 * value as a [StateFlow] so UI and services can react to changes without
 * polling.
 */
class SharedPreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun current(): AppSettings = _settings.value

    fun updateWakePhrase(phrase: String) = update { it.copy(wakePhrase = phrase) }

    fun updateFeedbackVerbosity(verbosity: FeedbackVerbosity) =
        update { it.copy(feedbackVerbosity = verbosity) }

    fun updateEnabledMessagingPackages(packages: Set<String>) =
        update { it.copy(enabledMessagingPackages = packages) }

    fun setRidingModeEnabled(enabled: Boolean) = update { it.copy(ridingModeEnabled = enabled) }

    fun setPermissionWizardCompleted(completed: Boolean) =
        update { it.copy(hasCompletedPermissionWizard = completed) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persist(updated)
    }

    private fun persist(settings: AppSettings) {
        prefs.edit {
            putString(KEY_WAKE_PHRASE, settings.wakePhrase)
            putString(KEY_VERBOSITY, settings.feedbackVerbosity.name)
            putStringSet(KEY_MESSAGING_PACKAGES, settings.enabledMessagingPackages)
            putBoolean(KEY_RIDING_MODE, settings.ridingModeEnabled)
            putBoolean(KEY_WIZARD_DONE, settings.hasCompletedPermissionWizard)
        }
    }

    private fun loadSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            wakePhrase = prefs.getString(KEY_WAKE_PHRASE, defaults.wakePhrase) ?: defaults.wakePhrase,
            feedbackVerbosity = runCatching {
                FeedbackVerbosity.valueOf(
                    prefs.getString(KEY_VERBOSITY, defaults.feedbackVerbosity.name)
                        ?: defaults.feedbackVerbosity.name
                )
            }.getOrDefault(defaults.feedbackVerbosity),
            enabledMessagingPackages = prefs.getStringSet(KEY_MESSAGING_PACKAGES, defaults.enabledMessagingPackages)
                ?: defaults.enabledMessagingPackages,
            ridingModeEnabled = prefs.getBoolean(KEY_RIDING_MODE, defaults.ridingModeEnabled),
            hasCompletedPermissionWizard = prefs.getBoolean(KEY_WIZARD_DONE, defaults.hasCompletedPermissionWizard)
        )
    }

    companion object {
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val KEY_VERBOSITY = "feedback_verbosity"
        private const val KEY_MESSAGING_PACKAGES = "enabled_messaging_packages"
        private const val KEY_RIDING_MODE = "riding_mode_enabled"
        private const val KEY_WIZARD_DONE = "permission_wizard_done"

        @Volatile
        private var instance: SharedPreferencesManager? = null

        fun getInstance(context: Context): SharedPreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: SharedPreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
