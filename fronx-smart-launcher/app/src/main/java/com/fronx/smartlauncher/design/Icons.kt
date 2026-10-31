package com.fronx.smartlauncher.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vector-free icon set drawn on a 24x24 grid. Keeps the APK small and avoids the
 * large material-icons-extended dependency.
 */
enum class FronxIcon {
    Home, Apps, Navigation, Music, Phone, Settings, Bluetooth, Wifi, Play, Pause, Next, Previous,
    Search, Star, StarOutline, Close, Back, Check, Plus, Brightness, Volume, Display, Tune,
    Person, Pin, Trash, Image
}

@Composable
fun FronxIconView(icon: FronxIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) { drawFronxIcon(icon, tint) }
}

private fun DrawScope.drawFronxIcon(icon: FronxIcon, c: Color) {
    val s = size.minDimension / 24f
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    val w = 2f * s
    val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = w) =
        drawLine(c, o(x1, y1), o(x2, y2), strokeWidth = width, cap = StrokeCap.Round)
    fun poly(vararg p: Float, close: Boolean = false, fill: Boolean = false) {
        val path = Path().apply {
            moveTo(p[0] * s, p[1] * s)
            var i = 2
            while (i < p.size) { lineTo(p[i] * s, p[i + 1] * s); i += 2 }
            if (close) close()
        }
        drawPath(path, c, style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else st)
    }
    fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, width: Float = w) =
        drawArc(c, start, sweep, false, o(cx - r, cy - r), Size(2 * r * s, 2 * r * s),
            style = Stroke(width = width, cap = StrokeCap.Round))

    when (icon) {
        FronxIcon.Home -> {
            poly(2.5f, 11.5f, 12f, 3.5f, 21.5f, 11.5f)
            poly(5f, 10f, 5f, 20.5f, 19f, 20.5f, 19f, 10f)
            poly(10f, 20.5f, 10f, 14.5f, 14f, 14.5f, 14f, 20.5f)
        }
        FronxIcon.Apps -> {
            val xs = listOf(3f, 10f, 17f)
            for (x in xs) for (y in xs)
                drawRoundRect(c, o(x, y), Size(4f * s, 4f * s), CornerRadius(1.2f * s))
        }
        FronxIcon.Navigation -> poly(12f, 2.5f, 20f, 21f, 12f, 16.8f, 4f, 21f, close = true, fill = true)
        FronxIcon.Music -> {
            drawCircle(c, 3.2f * s, o(7.5f, 18f))
            line(10.7f, 18f, 10.7f, 4.5f)
            poly(10.7f, 4.5f, 18.5f, 7f, 18.5f, 11f, 10.7f, 8.5f)
        }
        FronxIcon.Phone -> {
            arc(19f, 5f, 13.5f, 90f, 90f, width = 3.6f * s)
            line(4.6f, 4.6f, 7.8f, 4.6f, width = 3.6f * s)
            line(19.4f, 19.4f, 19.4f, 16.2f, width = 3.6f * s)
        }
        FronxIcon.Settings -> {
            drawCircle(c, 5f * s, o(12f, 12f), style = st)
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                line(12f + 7.6f * cos(a), 12f + 7.6f * sin(a), 12f + 10f * cos(a), 12f + 10f * sin(a), width = 2.6f * s)
            }
        }
        FronxIcon.Bluetooth -> poly(6.5f, 7.5f, 17f, 16.5f, 12f, 21.5f, 12f, 2.5f, 17f, 7.5f, 6.5f, 16.5f)
        FronxIcon.Wifi -> {
            drawCircle(c, 1.6f * s, o(12f, 19f))
            arc(12f, 19f, 5f, -135f, 90f)
            arc(12f, 19f, 9.5f, -135f, 90f)
            arc(12f, 19f, 14f, -135f, 90f)
        }
        FronxIcon.Play -> poly(7f, 4.5f, 19.5f, 12f, 7f, 19.5f, close = true, fill = true)
        FronxIcon.Pause -> {
            drawRoundRect(c, o(6f, 4.5f), Size(4.2f * s, 15f * s), CornerRadius(1f * s))
            drawRoundRect(c, o(13.8f, 4.5f), Size(4.2f * s, 15f * s), CornerRadius(1f * s))
        }
        FronxIcon.Next -> {
            poly(5.5f, 5f, 15.5f, 12f, 5.5f, 19f, close = true, fill = true)
            drawRoundRect(c, o(16.5f, 5f), Size(2.6f * s, 14f * s), CornerRadius(1f * s))
        }
        FronxIcon.Previous -> {
            poly(18.5f, 5f, 8.5f, 12f, 18.5f, 19f, close = true, fill = true)
            drawRoundRect(c, o(4.9f, 5f), Size(2.6f * s, 14f * s), CornerRadius(1f * s))
        }
        FronxIcon.Search -> { drawCircle(c, 6f * s, o(10f, 10f), style = st); line(14.5f, 14.5f, 20f, 20f) }
        FronxIcon.Star, FronxIcon.StarOutline -> {
            val pts = FloatArray(20)
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) 10f else 4.4f
                val a = (-90f + k * 36f) * PI.toFloat() / 180f
                pts[k * 2] = 12f + r * cos(a); pts[k * 2 + 1] = 12.8f + r * sin(a)
            }
            poly(*pts, close = true, fill = icon == FronxIcon.Star)
        }
        FronxIcon.Close -> { line(5.5f, 5.5f, 18.5f, 18.5f); line(18.5f, 5.5f, 5.5f, 18.5f) }
        FronxIcon.Back -> { line(19f, 12f, 5f, 12f); poly(11f, 6f, 5f, 12f, 11f, 18f) }
        FronxIcon.Check -> poly(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f)
        FronxIcon.Plus -> { line(12f, 4.5f, 12f, 19.5f); line(4.5f, 12f, 19.5f, 12f) }
        FronxIcon.Brightness -> {
            drawCircle(c, 4f * s, o(12f, 12f), style = st)
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                line(12f + 7f * cos(a), 12f + 7f * sin(a), 12f + 9.6f * cos(a), 12f + 9.6f * sin(a))
            }
        }
        FronxIcon.Volume -> {
            poly(3.5f, 9f, 7.5f, 9f, 12.5f, 4.8f, 12.5f, 19.2f, 7.5f, 15f, 3.5f, 15f, close = true, fill = true)
            arc(12.5f, 12f, 4.6f, -50f, 100f)
            arc(12.5f, 12f, 8.4f, -50f, 100f)
        }
        FronxIcon.Display -> {
            drawRoundRect(c, o(3f, 4f), Size(18f * s, 12f * s), CornerRadius(2f * s), style = st)
            line(12f, 16f, 12f, 20f); line(8f, 20f, 16f, 20f)
        }
        FronxIcon.Tune -> {
            line(3.5f, 6f, 20.5f, 6f); line(3.5f, 12f, 20.5f, 12f); line(3.5f, 18f, 20.5f, 18f)
            drawCircle(c, 2.6f * s, o(8f, 6f)); drawCircle(c, 2.6f * s, o(16f, 12f)); drawCircle(c, 2.6f * s, o(10f, 18f))
        }
        FronxIcon.Person -> {
            drawCircle(c, 4f * s, o(12f, 7.5f), style = st)
            arc(12f, 21f, 8f, 180f, 180f)
        }
        FronxIcon.Pin -> {
            drawCircle(c, 6f * s, o(12f, 9.5f), style = st)
            drawCircle(c, 2f * s, o(12f, 9.5f))
            line(12f, 15.5f, 12f, 21.5f)
        }
        FronxIcon.Trash -> {
            line(4.5f, 6.5f, 19.5f, 6.5f)
            poly(9f, 6.5f, 9f, 3.8f, 15f, 3.8f, 15f, 6.5f)
            poly(6.5f, 6.5f, 7.6f, 20f, 16.4f, 20f, 17.5f, 6.5f)
        }
        FronxIcon.Image -> {
            drawRoundRect(c, o(3f, 4.5f), Size(18f * s, 15f * s), CornerRadius(2.5f * s), style = st)
            poly(4.5f, 17f, 9.5f, 11.5f, 13f, 15f, 15.5f, 12.5f, 19.5f, 17f)
        }
    }
}
