package com.fronx.smartlauncher.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fronx.smartlauncher.app.FronxApplication
import com.fronx.smartlauncher.applications.AppInfo
import com.fronx.smartlauncher.applications.QuickTile
import com.fronx.smartlauncher.applications.QuickTiles
import com.fronx.smartlauncher.connectivity.ConnectivityState
import com.fronx.smartlauncher.media.MediaUiState
import com.fronx.smartlauncher.preferences.Destination
import com.fronx.smartlauncher.preferences.LauncherPreferences
import com.fronx.smartlauncher.weather.WeatherUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Thin aggregation of the repositories; all business logic lives in the repositories. */
class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as FronxApplication).container

    /** null until DataStore has delivered the first value (a few ms). */
    val prefs: StateFlow<LauncherPreferences?> =
        c.preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null while the first app scan runs. */
    val apps: StateFlow<List<AppInfo>?> = c.apps.apps

    val quickTiles: StateFlow<List<QuickTile>> = combine(c.apps.apps, c.preferences.flow) { apps, p ->
        val list = apps ?: emptyList()
        QuickTiles.build(list, p.favorites, c.apps.defaultFavorites(list))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val connectivity: StateFlow<ConnectivityState> =
        c.connectivity.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), c.connectivity.snapshot())

    val media: StateFlow<MediaUiState> = c.media.state
    val weather: StateFlow<WeatherUiState> = c.weather.state

    val mediaController get() = c.media

    fun update(transform: (LauncherPreferences) -> LauncherPreferences) {
        viewModelScope.launch { c.preferences.update(transform) }
    }

    /** First tap on a star "customises" favourites, seeded from what is currently shown. */
    fun toggleFavorite(pkg: String) {
        val list = c.apps.apps.value ?: emptyList()
        update { p ->
            val current = p.favorites ?: c.apps.defaultFavorites(list)
            p.copy(favorites = if (pkg in current) current - pkg else current + pkg)
        }
    }

    fun currentFavorites(p: LauncherPreferences): List<String> =
        p.favorites ?: c.apps.defaultFavorites(c.apps.apps.value ?: emptyList())

    fun navigationApps(): List<AppInfo> = c.apps.navigationApps(c.apps.apps.value ?: emptyList())
    fun musicApps(): List<AppInfo> = c.apps.musicApps(c.apps.apps.value ?: emptyList())

    fun addDestination(d: Destination) = update { it.copy(destinations = it.destinations + d) }
    fun removeDestination(d: Destination) = update { it.copy(destinations = it.destinations - d) }

    suspend fun refreshWeather(force: Boolean = false) {
        val city = prefs.value?.weatherCity.orEmpty()
        c.weather.refreshIfNeeded(city, c.weather.cachedAt(), force)
    }

    fun onVisible() { c.apps.attach(); c.media.attach() }
    fun onHidden() { c.apps.detach(); c.media.detach() }

    fun lastCrash(): String? =
        File(getApplication<Application>().filesDir, com.fronx.smartlauncher.app.FronxApplication.CRASH_FILE)
            .takeIf { it.exists() }?.readText()

    fun clearCrash() {
        File(getApplication<Application>().filesDir, com.fronx.smartlauncher.app.FronxApplication.CRASH_FILE).delete()
    }
}
