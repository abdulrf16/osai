package com.fronx.smartlauncher.app

import android.app.Application
import com.fronx.smartlauncher.applications.AppRepository
import com.fronx.smartlauncher.connectivity.ConnectivityMonitor
import com.fronx.smartlauncher.media.MediaRepository
import com.fronx.smartlauncher.preferences.PreferencesRepository
import com.fronx.smartlauncher.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** Manual dependency container: no DI framework needed for an app this size. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val preferences = PreferencesRepository(app)
    val apps = AppRepository(app, scope)
    val media = MediaRepository(app)
    val connectivity = ConnectivityMonitor(app)
    val weather = WeatherRepository(app, preferences, connectivity, scope)
}

class FronxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashRecorder()
        container = AppContainer(this)
    }

    /** Records the last crash to a private file (shown in Settings) then defers to the system handler. */
    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { File(filesDir, CRASH_FILE).writeText("${java.util.Date()}\n${e.stackTraceToString().take(4000)}") }
            previous?.uncaughtException(t, e)
        }
    }

    companion object { const val CRASH_FILE = "last_crash.txt" }
}
