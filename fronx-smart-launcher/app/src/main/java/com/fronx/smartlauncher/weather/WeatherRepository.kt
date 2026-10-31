package com.fronx.smartlauncher.weather

import android.content.Context
import com.fronx.smartlauncher.BuildConfig
import com.fronx.smartlauncher.connectivity.ConnectivityMonitor
import com.fronx.smartlauncher.preferences.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class WeatherData(val tempC: Double, val description: String, val place: String, val city: String, val fetchedAt: Long)

sealed interface WeatherUiState {
    /** No API key was provided at build time. */
    data object NotConfigured : WeatherUiState
    data object NoCity : WeatherUiState
    data object Loading : WeatherUiState
    data object Unavailable : WeatherUiState
    /** [stale] = showing the cached value because the latest refresh failed / device is offline. */
    data class Ready(val data: WeatherData, val stale: Boolean) : WeatherUiState
}

/**
 * OpenWeatherMap "current weather" (documented public API). Used only when a key was supplied at build
 * time via local.properties or FRONX_WEATHER_API_KEY - the key never lives in source control.
 * The last response is cached in DataStore so the card still shows something offline. Never blocks startup.
 */
class WeatherRepository(
    context: Context,
    private val prefs: PreferencesRepository,
    private val connectivity: ConnectivityMonitor,
    scope: CoroutineScope
) {
    private enum class Fetch { IDLE, RUNNING, FAILED }

    private val fetch = MutableStateFlow(Fetch.IDLE)
    private val apiKey = BuildConfig.WEATHER_API_KEY

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    val state: StateFlow<WeatherUiState> = combine(
        prefs.weatherCache, prefs.flow, fetch
    ) { cacheJson, p, f ->
        val city = p.weatherCity.trim()
        when {
            apiKey.isBlank() -> WeatherUiState.NotConfigured
            city.isEmpty() -> WeatherUiState.NoCity
            else -> {
                val cached = cacheJson?.let { parse(it) }?.takeIf { it.city.equals(city, ignoreCase = true) }
                when {
                    cached != null -> WeatherUiState.Ready(cached, stale = f == Fetch.FAILED)
                    f == Fetch.RUNNING -> WeatherUiState.Loading
                    f == Fetch.FAILED -> WeatherUiState.Unavailable
                    else -> WeatherUiState.Loading
                }
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, WeatherUiState.Loading)

    /** Fetches only if configured, online, and the cache is older than [MAX_AGE_MS]. */
    suspend fun refreshIfNeeded(city: String, cachedAt: Long?, force: Boolean = false) {
        val c = city.trim()
        if (apiKey.isBlank() || c.isEmpty()) return
        if (!force && cachedAt != null && System.currentTimeMillis() - cachedAt < MAX_AGE_MS) return
        if (!connectivity.isOnline()) { fetch.value = Fetch.FAILED; return }
        fetch.value = Fetch.RUNNING
        val json = withContext(Dispatchers.IO) { runCatching { download(c) }.getOrNull() }
        if (json == null) { fetch.value = Fetch.FAILED; return }
        prefs.saveWeatherCache(json)
        fetch.value = Fetch.IDLE
    }

    fun cachedAt(): Long? = (state.value as? WeatherUiState.Ready)?.data?.fetchedAt

    private fun download(city: String): String? {
        val url = URL("https://api.openweathermap.org/data/2.5/weather?q=" +
            URLEncoder.encode(city, "UTF-8") + "&units=metric&appid=" + URLEncoder.encode(apiKey, "UTF-8"))
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            JSONObject().apply {
                put("temp", o.getJSONObject("main").getDouble("temp"))
                put("desc", o.getJSONArray("weather").getJSONObject(0).getString("description"))
                put("place", o.optString("name", city))
                put("city", city)
                put("at", System.currentTimeMillis())
            }.toString()
        } finally { conn.disconnect() }
    }

    private fun parse(json: String): WeatherData? = runCatching {
        val o = JSONObject(json)
        WeatherData(o.getDouble("temp"), o.getString("desc"), o.getString("place"), o.getString("city"), o.getLong("at"))
    }.getOrNull()

    companion object { const val MAX_AGE_MS = 30 * 60 * 1000L }
}
