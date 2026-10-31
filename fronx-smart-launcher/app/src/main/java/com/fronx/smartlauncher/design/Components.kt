package com.fronx.smartlauncher.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Standard surface: rounded card, optional tap / long-press. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FronxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    color: Color = Fronx.colors.card,
    shape: Shape = Fronx.cardShape,
    content: @Composable BoxScope.() -> Unit
) {
    val base = modifier.clip(shape).background(color)
    val m = when {
        onClick != null || onLongClick != null ->
            base.combinedClickable(role = Role.Button, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
        else -> base
    }
    Box(m, content = content)
}

/** Large-target button with optional leading icon. [primary] uses the accent colour. */
@Composable
fun FronxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: FronxIcon? = null,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val c = Fronx.colors
    val bg = if (primary) c.accent else c.elevated
    val fg = if (primary) c.onAccent else c.textPrimary
    Row(
        modifier
            .clip(Fronx.buttonShape)
            .background(if (enabled) bg else c.elevated.copy(alpha = 0.5f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            FronxIconView(icon, if (enabled) fg else c.textMuted, size = 24.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = if (enabled) fg else c.textMuted, style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round icon-only button (touch target 56dp). */
@Composable
fun FronxIconButton(
    icon: FronxIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    iconSize: androidx.compose.ui.unit.Dp = 26.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    description: String? = null
) {
    val c = Fronx.colors
    val bg = if (filled) c.accent else Color.Transparent
    val tint = when {
        !enabled -> c.textMuted.copy(alpha = 0.5f)
        filled -> c.onAccent
        else -> c.textPrimary
    }
    Box(
        modifier.size(size).clip(CircleShape).background(bg)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { FronxIconView(icon, tint, size = iconSize) }
}

@Composable
fun FronxSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Fronx.colors
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
            uncheckedThumbColor = c.textSecondary, uncheckedTrackColor = c.elevated, uncheckedBorderColor = c.divider
        )
    )
}

/** Segmented single-choice control used for 12h/24h, units, layout and grid size. */
@Composable
fun <T> FronxSegmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Fronx.colors
    Row(modifier.clip(Fronx.buttonShape).background(c.elevated).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { opt ->
            val sel = opt == selected
            Box(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (sel) c.accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(opt) }
                    .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label(opt), color = if (sel) c.onAccent else c.textSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
