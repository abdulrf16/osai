package com.fronx.smartlauncher.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fronx.smartlauncher.preferences.AccentOption

object Fronx {
    val colors: FronxColors
        @Composable get() = LocalFronxColors.current

    // Spec: cards 16-22dp, buttons 12-16dp.
    val cardShape = RoundedCornerShape(20.dp)
    val buttonShape = RoundedCornerShape(14.dp)
    val smallShape = RoundedCornerShape(12.dp)
}

private val Sans = FontFamily.SansSerif

val FronxTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 72.sp, letterSpacing = (-1).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp)
)

private val FronxShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun FronxTheme(dark: Boolean, accent: AccentOption, content: @Composable () -> Unit) {
    val c = remember(dark, accent) { fronxColors(dark, accent) }
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, secondary = c.accentSecondary,
        background = c.bgPrimary, onBackground = c.textPrimary,
        surface = c.card, onSurface = c.textPrimary,
        surfaceVariant = c.elevated, onSurfaceVariant = c.textSecondary, outline = c.divider
    ) else lightColorScheme(
        primary = c.accent, onPrimary = c.onAccent, secondary = c.accentSecondary,
        background = c.bgPrimary, onBackground = c.textPrimary,
        surface = c.card, onSurface = c.textPrimary,
        surfaceVariant = c.elevated, onSurfaceVariant = c.textSecondary, outline = c.divider
    )
    CompositionLocalProvider(LocalFronxColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = FronxTypography, shapes = FronxShapes, content = content)
    }
}
