package com.fronx.smartlauncher.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.fronx.smartlauncher.preferences.AccentOption

/** Design tokens. Dark values are the specified Premium Dark Automotive palette. */
@Immutable
class FronxColors(
    val isDark: Boolean,
    val bgPrimary: Color,
    val bgSecondary: Color,
    val card: Color,
    val elevated: Color,
    val accent: Color,
    val accentSecondary: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val divider: Color,
    val positive: Color,
    val warning: Color
)

fun fronxColors(dark: Boolean, accent: AccentOption): FronxColors {
    val a = Color(accent.primary)
    val a2 = Color(accent.secondary)
    return if (dark) FronxColors(
        isDark = true,
        bgPrimary = Color(0xFF090D13), bgSecondary = Color(0xFF111823),
        card = Color(0xFF17212D), elevated = Color(0xFF202C3A),
        accent = a, accentSecondary = a2, onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFF5F7FA), textSecondary = Color(0xFF9AA9BA), textMuted = Color(0xFF647487),
        divider = Color(0xFF283646), positive = Color(0xFF4CC38A), warning = Color(0xFFF2A33A)
    ) else FronxColors(
        isDark = false,
        bgPrimary = Color(0xFFF1F4F8), bgSecondary = Color(0xFFE6EBF1),
        card = Color(0xFFFFFFFF), elevated = Color(0xFFE9EEF4),
        accent = a, accentSecondary = a2, onAccent = Color(0xFFFFFFFF),
        textPrimary = Color(0xFF0E1620), textSecondary = Color(0xFF4A5A6C), textMuted = Color(0xFF7A8A9C),
        divider = Color(0xFFD3DBE4), positive = Color(0xFF1F9D63), warning = Color(0xFFC77A0C)
    )
}

val LocalFronxColors = staticCompositionLocalOf { fronxColors(true, AccentOption.BLUE) }
