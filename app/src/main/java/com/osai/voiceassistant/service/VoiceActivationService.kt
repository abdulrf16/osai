package com.osai.voiceassistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.osai.voiceassistant.MainActivity
import com.osai.voiceassistant.R
import com.osai.voiceassistant.audio.TextToSpeechManager
import com.osai.voiceassistant.bluetooth.BluetoothDeviceManager
import com.osai.voiceassistant.command.CommandExecutor
import com.osai.voiceassistant.command.CommandParser
import com.osai.voiceassistant.media.AudioVolumeManager
import com.osai.voiceassistant.media.MediaControlManager
import com.osai.voiceassistant.settings.SharedPreferencesManager
import com.osai.voiceassistant.utils.Constants
import com.osai.voiceassistant.utils.Logger
import com.osai.voiceassistant.voice.BluetoothAudioRouter
import com.osai.voiceassistant.voice.CommandSpeechRecognizer
import com.osai.voiceassistant.voice.SpeechResult
import com.osai.voiceassistant.voice.WakeWordDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Rider-facing state shown by [com.osai.voiceassistant.ui.screens.RidingModeScreen]. */
enum class ServiceStatus {
    STOPPED,
    LISTENING_WAKE_WORD,
    LISTENING_COMMAND,
    PROCESSING,
    SUCCESS,
    ERROR
}

/**
 * Foreground service that owns the full hands-free pipeline: continuous
 * wake-word detection -> speech recognition -> command parsing -> action
 * execution -> spoken feedback. Declares
 * FOREGROUND_SERVICE_MICROPHONE (Android 14+ requirement) and keeps a
 * persistent notification for the lifetime of Riding Mode.
 */
class VoiceActivationService : Service(), WakeWordDetector.WakeWordListener {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob)
    private var commandLoopJob: Job? = null

    private lateinit var bluetoothDeviceManager: BluetoothDeviceManager
    private lateinit var bluetoothAudioRouter: BluetoothAudioRouter
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var speechRecognizer: CommandSpeechRecognizer
    private lateinit var commandParser: CommandParser
    private lateinit var commandExecutor: CommandExecutor
    private lateinit var textToSpeechManager: TextToSpeechManager
    private lateinit var settingsManager: SharedPreferencesManager

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "VoiceActivationService created")
        settingsManager = SharedPreferencesManager.getInstance(applicationContext)
        textToSpeechManager = TextToSpeechManager(applicationContext)
        activeTextToSpeechManager = textToSpeechManager

        bluetoothDeviceManager = BluetoothDeviceManager(applicationContext)
        bluetoothDeviceManager.start()
        bluetoothAudioRouter = BluetoothAudioRouter(applicationContext, bluetoothDeviceManager)

        speechRecognizer = CommandSpeechRecognizer(applicationContext)
        commandParser = CommandParser()
        commandExecutor = CommandExecutor(
            mediaControlManager = MediaControlManager(applicationContext),
            audioVolumeManager = AudioVolumeManager(applicationContext),
            textToSpeechManager = textToSpeechManager,
            verbosityProvider = { settingsManager.current().feedbackVerbosity }
        )
        wakeWordDetector = WakeWordDetector(applicationContext, this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START_RIDING_MODE -> startRidingMode()
            Constants.ACTION_STOP_RIDING_MODE -> stopRidingMode()
            Constants.ACTION_TEST_MIC -> runMicTest()
            Constants.ACTION_TEST_SPEAKER -> runSpeakerTest()
            else -> {
                // Service restarted by the system (e.g. after process death)
                // with a null intent; resume riding mode only if it was
                // previously enabled.
                if (settingsManager.current().ridingModeEnabled) startRidingMode()
                else stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Logger.i(TAG, "VoiceActivationService destroyed")
        commandLoopJob?.cancel()
        wakeWordDetector.stop()
        bluetoothAudioRouter.releaseAfterListening()
        bluetoothDeviceManager.stop()
        if (activeTextToSpeechManager === textToSpeechManager) activeTextToSpeechManager = null
        textToSpeechManager.shutdown()
        serviceJob.cancel()
        _status.value = ServiceStatus.STOPPED
        super.onDestroy()
    }

    // region Riding mode lifecycle

    private fun startRidingMode() {
        startForeground(Constants.FOREGROUND_NOTIFICATION_ID, buildNotification(listening = true))
        settingsManager.setRidingModeEnabled(true)
        _status.value = ServiceStatus.LISTENING_WAKE_WORD
        wakeWordDetector.start()
        Logger.i(TAG, "Riding Mode started")
    }

    private fun stopRidingMode() {
        settingsManager.setRidingModeEnabled(false)
        wakeWordDetector.stop()
        commandLoopJob?.cancel()
        _status.value = ServiceStatus.STOPPED
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Logger.i(TAG, "Riding Mode stopped")
    }

    // endregion

    // region WakeWordDetector.WakeWordListener

    override fun onWakeWordDetected() {
        if (commandLoopJob?.isActive == true) return
        commandLoopJob = serviceScope.launch {
            handleWakeWordTriggered()
        }
    }

    override fun onError(message: String) {
        Logger.e(TAG, "Wake word engine error: $message")
        _status.value = ServiceStatus.ERROR
        updateNotification(listening = false)
    }

    // endregion

    private suspend fun handleWakeWordTriggered() {
        wakeWordDetector.pause()
        _status.value = ServiceStatus.LISTENING_COMMAND
        updateNotification(listening = true, activelyListeningForCommand = true)

        val micSource = bluetoothAudioRouter.prepareForListening()
        Logger.d(TAG, "Listening for command via $micSource")
        textToSpeechManager.speakAndAwait("Yes?", timeoutMs = 2000L)

        val result = speechRecognizer.listenOnce()
        bluetoothAudioRouter.releaseAfterListening()

        _status.value = ServiceStatus.PROCESSING
        when (result) {
            is SpeechResult.Success -> {
                val command = commandParser.parse(result.transcript)
                Logger.i(TAG, "Parsed command: ${command.type}")
                commandExecutor.execute(command)
                _status.value = ServiceStatus.SUCCESS
            }
            is SpeechResult.NoSpeechDetected -> {
                Logger.w(TAG, "No speech detected after wake word")
                textToSpeechManager.speak("I didn't hear a command")
                _status.value = ServiceStatus.ERROR
            }
            is SpeechResult.Error -> {
                Logger.w(TAG, "Speech recognition error: ${result.reason}")
                textToSpeechManager.speak("Sorry, something went wrong")
                _status.value = ServiceStatus.ERROR
            }
        }

        delay(Constants.WAKE_WORD_REARM_DELAY_MS)
        updateNotification(listening = true, activelyListeningForCommand = false)
        _status.value = ServiceStatus.LISTENING_WAKE_WORD
        wakeWordDetector.resume()
    }

    // region Debug test actions

    private fun runMicTest() {
        val wasForeground = settingsManager.current().ridingModeEnabled
        startForeground(Constants.FOREGROUND_NOTIFICATION_ID, buildNotification(listening = false, testing = true))
        serviceScope.launch {
            wakeWordDetector.pause()
            val micSource = bluetoothAudioRouter.prepareForListening()
            textToSpeechManager.speakAndAwait("Say something to test the microphone", timeoutMs = 3000L)
            val result = speechRecognizer.listenOnce()
            bluetoothAudioRouter.releaseAfterListening()
            when (result) {
                is SpeechResult.Success -> textToSpeechManager.speakAndAwait("I heard: ${result.transcript}")
                is SpeechResult.NoSpeechDetected -> textToSpeechManager.speakAndAwait("I didn't hear anything, mic source was $micSource")
                is SpeechResult.Error -> textToSpeechManager.speakAndAwait("Microphone test failed: ${result.reason}")
            }
            finishTransientForegroundIfNeeded(wasForeground)
        }
    }

    private fun runSpeakerTest() {
        val wasForeground = settingsManager.current().ridingModeEnabled
        startForeground(Constants.FOREGROUND_NOTIFICATION_ID, buildNotification(listening = false, testing = true))
        serviceScope.launch {
            val sink = bluetoothAudioRouter.currentOutputSink()
            textToSpeechManager.speakAndAwait("This is a speaker test, routed to $sink", timeoutMs = 6000L)
            finishTransientForegroundIfNeeded(wasForeground)
        }
    }

    private fun finishTransientForegroundIfNeeded(keepRunning: Boolean) {
        if (keepRunning) {
            updateNotification(listening = true)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // endregion

    // region Notification

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun buildNotification(listening: Boolean, activelyListeningForCommand: Boolean = false, testing: Boolean = false): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = when {
            testing -> "Riding Assistant is testing audio"
            listening && activelyListeningForCommand -> getString(R.string.status_listening_command)
            listening -> getString(R.string.notification_title_listening)
            else -> getString(R.string.notification_title_paused)
        }
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(listening: Boolean, activelyListeningForCommand: Boolean = false) {
        val notification = buildNotification(listening, activelyListeningForCommand)
        getSystemService(NotificationManager::class.java)
            ?.notify(Constants.FOREGROUND_NOTIFICATION_ID, notification)
    }

    // endregion

    companion object {
        private const val TAG = "VoiceActivationService"

        private val _status = MutableStateFlow(ServiceStatus.STOPPED)
        val status: StateFlow<ServiceStatus> = _status.asStateFlow()

        /**
         * Exposes the running service's TTS engine so short-lived components
         * (e.g. [CallStateListener]) can reuse the already-routed engine
         * instead of spinning up a competing one. Null when the service is
         * not running.
         */
        @Volatile
        var activeTextToSpeechManager: TextToSpeechManager? = null
            private set
    }
}
