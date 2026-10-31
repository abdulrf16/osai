package com.fronx.smartlauncher.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.launcherDataStore by preferencesDataStore(name = "fronx_launcher")

/** Local-only persistence (DataStore). No account, no sync. */
class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.launcherDataStore

    private object K {
        val clock = stringPreferencesKey("clock_format")
        val dark = booleanPreferencesKey("dark_theme")
        val accent = stringPreferencesKey("accent")
        val wallpaper = stringPreferencesKey("wallpaper")
        val wallpaperUri = stringPreferencesKey("wallpaper_uri")
        val favorites = stringPreferencesKey("favorites")
        val showWeather = booleanPreferencesKey("show_weather")
        val showMedia = booleanPreferencesKey("show_media")
        val showQuick = booleanPreferencesKey("show_quick_apps")
        val layout = stringPreferencesKey("layout")
        val grid = intPreferencesKey("grid_columns")
        val tempUnit = stringPreferencesKey("temp_unit")
        val city = stringPreferencesKey("weather_city")
        val navPkg = stringPreferencesKey("nav_package")
        val destinations = stringPreferencesKey("destinations")
        val weatherCache = stringPreferencesKey("weather_cache")
    }

    private val safeData: Flow<Preferences> = store.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    val flow: Flow<LauncherPreferences> = safeData.map { it.toModel() }

    val weatherCache: Flow<String?> = safeData.map { it[K.weatherCache] }

    suspend fun update(transform: (LauncherPreferences) -> LauncherPreferences) {
        store.edit { p -> p.write(transform(p.toModel())) }
    }

    suspend fun saveWeatherCache(json: String) {
        store.edit { it[K.weatherCache] = json }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun Preferences.toModel(): LauncherPreferences {
        val d = LauncherPreferences()
        return LauncherPreferences(
            clockFormat = this[K.clock].toEnum(d.clockFormat),
            darkTheme = this[K.dark] ?: d.darkTheme,
            accent = this[K.accent].toEnum(d.accent),
            wallpaper = this[K.wallpaper].toEnum(d.wallpaper),
            customWallpaperUri = this[K.wallpaperUri],
            favorites = this[K.favorites]?.let { PreferenceCodec.decodeList(it) },
            showWeather = this[K.showWeather] ?: d.showWeather,
            showMedia = this[K.showMedia] ?: d.showMedia,
            showQuickApps = this[K.showQuick] ?: d.showQuickApps,
            layout = this[K.layout].toEnum(d.layout),
            gridColumns = (this[K.grid] ?: d.gridColumns).takeIf { it in LauncherPreferences.GRID_SIZES }
                ?: d.gridColumns,
            temperatureUnit = this[K.tempUnit].toEnum(d.temperatureUnit),
            weatherCity = this[K.city] ?: d.weatherCity,
            preferredNavPackage = this[K.navPkg],
            destinations = this[K.destinations]?.let { PreferenceCodec.decodeDestinations(it) } ?: emptyList()
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(m: LauncherPreferences) {
        this[K.clock] = m.clockFormat.name
        this[K.dark] = m.darkTheme
        this[K.accent] = m.accent.name
        this[K.wallpaper] = m.wallpaper.name
        if (m.customWallpaperUri != null) this[K.wallpaperUri] = m.customWallpaperUri else remove(K.wallpaperUri)
        if (m.favorites != null) this[K.favorites] = PreferenceCodec.encodeList(m.favorites) else remove(K.favorites)
        this[K.showWeather] = m.showWeather
        this[K.showMedia] = m.showMedia
        this[K.showQuick] = m.showQuickApps
        this[K.layout] = m.layout.name
        this[K.grid] = m.gridColumns
        this[K.tempUnit] = m.temperatureUnit.name
        this[K.city] = m.weatherCity
        if (m.preferredNavPackage != null) this[K.navPkg] = m.preferredNavPackage else remove(K.navPkg)
        this[K.destinations] = PreferenceCodec.encodeDestinations(m.destinations)
    }
}
