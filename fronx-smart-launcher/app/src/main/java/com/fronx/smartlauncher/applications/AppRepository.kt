package com.fronx.smartlauncher.applications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.fronx.smartlauncher.utils.Launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lists launchable apps with PackageManager.queryIntentActivities(MAIN/LAUNCHER) - covered by the
 * manifest <queries> entry, so QUERY_ALL_PACKAGES is not needed. Package-change broadcasts are only
 * registered while the launcher is visible.
 */
class AppRepository(context: Context, private val scope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    /** null while the first load is running. */
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    private var job: Job? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reload()
    }

    fun attach() {
        if (!receiverRegistered) {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            appContext.registerReceiver(receiver, f)
            receiverRegistered = true
        }
        if (_apps.value == null) reload()
    }

    fun detach() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    fun reload() {
        job?.cancel()
        job = scope.launch { _apps.value = withContext(Dispatchers.Default) { query() } }
    }

    private fun query(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results: List<ResolveInfo> = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        return results
            .filter { it.activityInfo.packageName != appContext.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val icon = runCatching { ri.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
                AppInfo(ri.activityInfo.packageName, ri.activityInfo.name, ri.loadLabel(pm).toString(), icon)
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Defaults for the quick grid when the user never customised it: only apps that are actually
     * installed. Package names are *candidates*, never assumed to exist.
     */
    fun defaultFavorites(apps: List<AppInfo>): List<String> {
        val byPkg = apps.associateBy { it.packageName }
        val out = LinkedHashSet<String>()
        fun addIfInstalled(pkg: String?) { if (pkg != null && byPkg.containsKey(pkg)) out += pkg }
        addIfInstalled(Launch.MAPS_PACKAGE)
        addIfInstalled("com.google.android.apps.youtube.music")
        addIfInstalled("com.spotify.music")
        addIfInstalled(resolvePackage(Launch.dialer()))
        apps.firstOrNull { it.label.contains("radio", ignoreCase = true) }?.let { out += it.packageName }
        addIfInstalled(resolvePackage(Launch.system()))
        return out.toList()
    }

    private fun resolvePackage(intent: Intent): String? = runCatching {
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    /** Installed navigation apps: anything that handles geo: links or declares the Maps category. */
    fun navigationApps(apps: List<AppInfo>): List<AppInfo> {
        val geo = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=test"))
        val pkgs = HashSet<String>()
        runCatching { pm.queryIntentActivities(geo, 0).forEach { pkgs += it.activityInfo.packageName } }
        runCatching { pm.queryIntentActivities(Launch.mapsHome(), 0).forEach { pkgs += it.activityInfo.packageName } }
        return apps.filter { it.packageName in pkgs }
    }

    /** Installed music apps: those declaring the Music category, plus any installed known players. */
    fun musicApps(apps: List<AppInfo>): List<AppInfo> {
        val pkgs = HashSet<String>()
        val music = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        runCatching { pm.queryIntentActivities(music, 0).forEach { pkgs += it.activityInfo.packageName } }
        pkgs += listOf("com.google.android.apps.youtube.music", "com.spotify.music")
        return apps.filter { it.packageName in pkgs }
    }

    private companion object { const val ICON_PX = 112 }
}

/** Pure mapping from stored favourites to the grid tiles (unit-testable). */
object QuickTiles {
    fun build(apps: List<AppInfo>, stored: List<String>?, defaults: List<String>): List<QuickTile> {
        val byPkg = apps.associateBy { it.packageName }
        val custom = stored != null
        val pkgs = (stored ?: defaults).filter { byPkg.containsKey(it) } // drop uninstalled
        val tiles = ArrayList<QuickTile>()
        pkgs.forEach { tiles += QuickTile.App(byPkg.getValue(it)) }
        if (!custom) tiles += QuickTile.Bluetooth
        tiles += QuickTile.AllApps
        return tiles
    }
}
