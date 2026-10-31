package com.fronx.smartlauncher.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxIcon
import com.fronx.smartlauncher.design.FronxIconView

enum class Route(val label: String, val icon: FronxIcon) {
    HOME("Home", FronxIcon.Home),
    APPS("Apps", FronxIcon.Apps),
    NAVIGATION("Navigation", FronxIcon.Navigation),
    MUSIC("Music", FronxIcon.Music),
    PHONE("Phone", FronxIcon.Phone),
    SETTINGS("Settings", FronxIcon.Settings)
}

/** Fixed bottom dock, identical on every launcher screen. Does not touch Android's system navigation. */
@Composable
fun Dock(current: Route, onSelect: (Route) -> Unit, modifier: Modifier = Modifier) {
    val c = Fronx.colors
    Column(modifier.fillMaxWidth().background(c.bgSecondary)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Route.entries.forEach { r ->
                val active = r == current
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) c.accent.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = Role.Tab) { onSelect(r) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    FronxIconView(r.icon, if (active) c.accentSecondary else c.textSecondary, size = 28.dp)
                    Text(r.label, style = MaterialTheme.typography.labelSmall, color = if (active) c.textPrimary else c.textSecondary, maxLines = 1)
                    // subtle blue indicator for the active destination
                    Box(Modifier.padding(top = 3.dp).width(if (active) 22.dp else 0.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(c.accent))
                }
            }
        }
    }
}
