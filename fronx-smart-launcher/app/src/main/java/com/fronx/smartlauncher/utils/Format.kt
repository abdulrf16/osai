package com.fronx.smartlauncher.utils

import com.fronx.smartlauncher.preferences.TemperatureUnit
import kotlin.math.roundToInt

object Format {
    /** 83000 -> "1:23", 3_723_000 -> "1:02:03". Negative values clamp to 0. */
    fun duration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun temperature(celsius: Double, unit: TemperatureUnit): String = when (unit) {
        TemperatureUnit.CELSIUS -> "${celsius.roundToInt()}°C"
        TemperatureUnit.FAHRENHEIT -> "${(celsius * 9 / 5 + 32).roundToInt()}°F"
    }

    /**
     * Estimated live playback position. [positionMs] was reported at [updatedAt] (elapsedRealtime);
     * while playing it advances at [speed]. Result is clamped to [0, durationMs] when duration is known.
     */
    fun livePosition(
        positionMs: Long, updatedAt: Long, speed: Float, playing: Boolean, now: Long, durationMs: Long
    ): Long {
        var p = positionMs
        if (playing) p += ((now - updatedAt).coerceAtLeast(0) * speed).toLong()
        if (durationMs > 0) p = p.coerceAtMost(durationMs)
        return p.coerceAtLeast(0)
    }
}
