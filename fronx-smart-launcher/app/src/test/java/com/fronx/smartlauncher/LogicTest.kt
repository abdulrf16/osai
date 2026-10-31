package com.fronx.smartlauncher

import com.fronx.smartlauncher.applications.AppInfo
import com.fronx.smartlauncher.applications.QuickTile
import com.fronx.smartlauncher.applications.QuickTiles
import com.fronx.smartlauncher.applications.filterApps
import com.fronx.smartlauncher.preferences.Destination
import com.fronx.smartlauncher.preferences.PreferenceCodec
import com.fronx.smartlauncher.preferences.TemperatureUnit
import com.fronx.smartlauncher.utils.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun app(pkg: String, label: String) = AppInfo(pkg, "$pkg.Main", label, null)
    private val apps = listOf(app("a.maps", "Maps"), app("b.music", "Music Player"), app("c.radio", "FM Radio"))

    @Test fun durationFormatting() {
        assertEquals("0:00", Format.duration(-5))
        assertEquals("1:23", Format.duration(83_000))
        assertEquals("1:02:03", Format.duration(3_723_000))
    }

    @Test fun temperatureUnits() {
        assertEquals("21°C", Format.temperature(21.2, TemperatureUnit.CELSIUS))
        assertEquals("70°F", Format.temperature(21.2, TemperatureUnit.FAHRENHEIT))
    }

    @Test fun livePositionAdvancesOnlyWhilePlayingAndClamps() {
        assertEquals(10_000, Format.livePosition(10_000, 1000, 1f, false, 6000, 200_000))
        assertEquals(15_000, Format.livePosition(10_000, 1000, 1f, true, 6000, 200_000))
        assertEquals(20_000, Format.livePosition(10_000, 1000, 1f, true, 50_000, 20_000))
        assertEquals(30_000, Format.livePosition(10_000, 1000, 2f, true, 11_000, 0))
    }

    @Test fun listCodecRoundTrip() {
        val l = listOf("com.a", "com.b")
        assertEquals(l, PreferenceCodec.decodeList(PreferenceCodec.encodeList(l)))
        assertEquals(emptyList<String>(), PreferenceCodec.decodeList(""))
    }

    @Test fun destinationCodecSanitises() {
        val d = listOf(Destination("Home", "1 Main St"), Destination("Wo\trk", "Some\nPlace"))
        val back = PreferenceCodec.decodeDestinations(PreferenceCodec.encodeDestinations(d))
        assertEquals(2, back.size)
        assertEquals("Wo rk", back[1].name)
        assertEquals("Some Place", back[1].query)
    }

    @Test fun searchFiltersCaseInsensitive() {
        assertEquals(listOf("Music Player"), filterApps(apps, "MUS", false, emptySet()).map { it.label })
        assertTrue(filterApps(apps, "zzz", false, emptySet()).isEmpty())
        assertEquals(listOf("Maps"), filterApps(apps, "", true, setOf("a.maps")).map { it.label })
    }

    @Test fun quickTilesDropUninstalledAndKeepAllAppsLast() {
        val custom = QuickTiles.build(apps, listOf("a.maps", "gone.pkg"), emptyList())
        assertEquals(listOf("a.maps", "action:all_apps"), custom.map { it.key })
        val defaults = QuickTiles.build(apps, null, listOf("b.music"))
        assertEquals(listOf("b.music", "action:bluetooth", "action:all_apps"), defaults.map { it.key })
        assertTrue(defaults.last() is QuickTile.AllApps)
    }
}
