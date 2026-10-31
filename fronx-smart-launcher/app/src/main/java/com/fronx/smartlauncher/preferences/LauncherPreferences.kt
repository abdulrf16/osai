package com.fronx.smartlauncher.preferences

enum class ClockFormat { H12, H24 }
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/** STANDARD = info | media | apps. MIRRORED = apps | media | info (for drivers who prefer it). */
enum class DashboardLayout(val label: String) { STANDARD("Standard"), MIRRORED("Mirrored") }

enum class WallpaperStyle(val label: String) {
    PLAIN("Plain"), CARBON("Carbon"), MIDNIGHT("Midnight"), CUSTOM("My image")
}

enum class AccentOption(val label: String, val primary: Long, val secondary: Long) {
    BLUE("Blue", 0xFF2589F4, 0xFF54B4FF),
    TEAL("Teal", 0xFF1FB8A6, 0xFF5FD8C9),
    AMBER("Amber", 0xFFF2A33A, 0xFFFFC56B),
    VIOLET("Violet", 0xFF8A6CF2, 0xFFB3A0FF),
    ROSE("Rose", 0xFFEB5C7A, 0xFFFF8FA6)
}

/** A saved navigation destination: [query] is an address or place name handed to the nav app. */
data class Destination(val name: String, val query: String)

data class LauncherPreferences(
    val clockFormat: ClockFormat = ClockFormat.H12,
    val darkTheme: Boolean = true,
    val accent: AccentOption = AccentOption.BLUE,
    val wallpaper: WallpaperStyle = WallpaperStyle.CARBON,
    val customWallpaperUri: String? = null,
    /** null = user never customised favourites, so sensible installed defaults are shown. */
    val favorites: List<String>? = null,
    val showWeather: Boolean = true,
    val showMedia: Boolean = true,
    val showQuickApps: Boolean = true,
    val layout: DashboardLayout = DashboardLayout.STANDARD,
    val gridColumns: Int = 5,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val weatherCity: String = "",
    val preferredNavPackage: String? = null,
    val destinations: List<Destination> = emptyList()
) {
    companion object {
        val GRID_SIZES = listOf(4, 5, 6, 7)
    }
}

/** Plain-text encoding of list preferences (kept pure so it can be unit tested). */
object PreferenceCodec {
    private fun clean(s: String) = s.replace('\n', ' ').replace('\t', ' ').trim()

    fun encodeList(items: List<String>): String = items.joinToString("\n") { clean(it) }

    fun decodeList(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun encodeDestinations(items: List<Destination>): String =
        items.joinToString("\n") { "${clean(it.name)}\t${clean(it.query)}" }

    fun decodeDestinations(raw: String): List<Destination> =
        raw.split('\n').mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank())
                Destination(parts[0].trim(), parts[1].trim()) else null
        }
}
