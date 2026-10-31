package com.fronx.smartlauncher.applications

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap?
)

/** An entry in the dashboard's quick-apps grid. */
sealed interface QuickTile {
    val key: String
    data class App(val app: AppInfo) : QuickTile { override val key get() = app.packageName }
    data object Bluetooth : QuickTile { override val key get() = "action:bluetooth" }
    data object AllApps : QuickTile { override val key get() = "action:all_apps" }
}
