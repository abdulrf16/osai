package com.fronx.smartlauncher.launcher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fronx.smartlauncher.applications.AppInfo
import com.fronx.smartlauncher.applications.AppsScreen
import com.fronx.smartlauncher.applications.QuickTile
import com.fronx.smartlauncher.connectivity.PhoneScreen
import com.fronx.smartlauncher.dashboard.DashboardActions
import com.fronx.smartlauncher.dashboard.DashboardScreen
import com.fronx.smartlauncher.design.Fronx
import com.fronx.smartlauncher.design.FronxTheme
import com.fronx.smartlauncher.media.MediaActions
import com.fronx.smartlauncher.media.MusicScreen
import com.fronx.smartlauncher.navigation.NavigationScreen
import com.fronx.smartlauncher.preferences.LauncherPreferences
import com.fronx.smartlauncher.preferences.WallpaperStyle
import com.fronx.smartlauncher.settings.CustomizationScreen
import com.fronx.smartlauncher.settings.FavoritesScreen
import com.fronx.smartlauncher.settings.QuickSettingsActions
import com.fronx.smartlauncher.settings.QuickSettingsScreen
import com.fronx.smartlauncher.utils.Launch
import com.fronx.smartlauncher.utils.decodeSampled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class SettingsPage { MAIN, CUSTOMIZE, FAVORITES }

@Composable
fun LauncherRoot(
    vm: LauncherViewModel,
    homeTick: Int,
    requestDefaultHome: () -> Unit,
    pickWallpaper: () -> Unit
) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val p = prefs
    if (p == null) { // DataStore first read: just the base background for a few ms
        Box(Modifier.fillMaxSize().background(Color(0xFF090D13)))
        return
    }
    FronxTheme(p.darkTheme, p.accent) { LauncherContent(vm, p, homeTick, requestDefaultHome, pickWallpaper) }
}

@Composable
private fun LauncherContent(
    vm: LauncherViewModel, p: LauncherPreferences, homeTick: Int,
    requestDefaultHome: () -> Unit, pickWallpaper: () -> Unit
) {
    val ctx = LocalContext.current
    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    val apps by vm.apps.collectAsStateWithLifecycle()
    val tiles by vm.quickTiles.collectAsStateWithLifecycle()
    val conn by vm.connectivity.collectAsStateWithLifecycle()
    val media by vm.media.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

    // Home pressed while we're already open -> back to dashboard.
    LaunchedEffect(homeTick) { if (homeTick > 0) { route = Route.HOME; settingsPage = SettingsPage.MAIN } }

    // Weather: one fetch on show + at most every 30 min while visible; never blocks startup.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(p.weatherCity, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { vm.refreshWeather(); delay(30 * 60 * 1000L) }
        }
    }

    // Default-home status can change while we're in Settings; re-check whenever we become visible.
    var isDefault by remember { mutableStateOf(Launch.isDefaultHome(ctx)) }
    var hasAccess by remember { mutableStateOf(Launch.hasNotificationAccess(ctx)) }
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            isDefault = Launch.isDefaultHome(ctx); hasAccess = Launch.hasNotificationAccess(ctx)
        }
    }

    BackHandler(enabled = route != Route.HOME) {
        if (route == Route.SETTINGS && settingsPage != SettingsPage.MAIN) settingsPage = SettingsPage.MAIN
        else route = Route.HOME
    }

    val mediaActions = MediaActions(
        togglePlayPause = vm.mediaController::togglePlayPause,
        next = vm.mediaController::next,
        previous = vm.mediaController::previous,
        openApp = { vm.mediaController.openApp() },
        openAccessSettings = { Launch.start(ctx, Launch.notificationAccess()) },
        browseMusicApps = { route = Route.MUSIC }
    )

    fun launchApp(a: AppInfo) { Launch.start(ctx, Launch.component(a.packageName, a.activityName)) }
    fun openNavigationApp() {
        val maps = Launch.mapsHome()
        if (Launch.canResolve(ctx, maps)) Launch.start(ctx, maps) else route = Route.NAVIGATION
    }
    fun openDialer() { Launch.start(ctx, Launch.dialer()) }

    val dashboardActions = DashboardActions(
        openTile = { t ->
            when (t) {
                is QuickTile.App -> launchApp(t.app)
                QuickTile.Bluetooth -> Launch.start(ctx, Launch.bluetooth())
                QuickTile.AllApps -> route = Route.APPS
            }
        },
        openNavigation = ::openNavigationApp,
        openPhone = ::openDialer,
        openSettings = { route = Route.SETTINGS; settingsPage = SettingsPage.MAIN },
        openAllApps = { route = Route.APPS },
        media = mediaActions,
        setDefaultLauncher = requestDefaultHome
    )

    Box(Modifier.fillMaxSize()) {
        Wallpaper(p)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (route) {
                    Route.HOME -> DashboardScreen(p, tiles, conn, media, weather, isDefault, dashboardActions)
                    Route.APPS -> AppsScreen(
                        apps = apps, favorites = vm.currentFavorites(p).toSet(), columns = p.gridColumns,
                        onLaunch = ::launchApp, onToggleFavorite = { vm.toggleFavorite(it.packageName) },
                        onAppInfo = { Launch.start(ctx, Launch.appDetails(it.packageName)) }
                    )
                    Route.NAVIGATION -> {
                        val navApps = remember(apps) { vm.navigationApps() }
                        NavigationScreen(
                            navApps = navApps, preferredPackage = p.preferredNavPackage, destinations = p.destinations,
                            onOpenApp = ::launchApp, onOpenMaps = ::openNavigationApp,
                            mapsInstalled = navApps.any { it.packageName == Launch.MAPS_PACKAGE },
                            onSelectApp = { pkg -> vm.update { it.copy(preferredNavPackage = pkg) } },
                            onNavigate = { q -> navigateTo(ctx, q, p.preferredNavPackage ?: navApps.firstOrNull()?.packageName) },
                            onAddDestination = vm::addDestination, onRemoveDestination = vm::removeDestination
                        )
                    }
                    Route.MUSIC -> {
                        val music = remember(apps) { vm.musicApps() }
                        MusicScreen(media, mediaActions, music, ::launchApp)
                    }
                    Route.PHONE -> PhoneScreen(
                        conn, Launch.canResolve(ctx, Launch.dialer()), Launch.canResolve(ctx, Launch.contacts()),
                        onDialer = ::openDialer, onContacts = { Launch.start(ctx, Launch.contacts()) },
                        onBluetooth = { Launch.start(ctx, Launch.bluetooth()) }, onWifi = { Launch.start(ctx, Launch.wifi()) }
                    )
                    Route.SETTINGS -> when (settingsPage) {
                        SettingsPage.MAIN -> QuickSettingsScreen(
                            isDefaultHome = isDefault, hasNotificationAccess = hasAccess, lastCrash = vm.lastCrash(),
                            actions = QuickSettingsActions(
                                display = { Launch.start(ctx, Launch.display()) }, sound = { Launch.start(ctx, Launch.sound()) },
                                brightness = { Launch.start(ctx, Launch.display()) }, bluetooth = { Launch.start(ctx, Launch.bluetooth()) },
                                wifi = { Launch.start(ctx, Launch.wifi()) }, system = { Launch.start(ctx, Launch.system()) },
                                customize = { settingsPage = SettingsPage.CUSTOMIZE },
                                chooseDefaultHome = requestDefaultHome,
                                notificationAccess = { Launch.start(ctx, Launch.notificationAccess()) },
                                clearCrash = { vm.clearCrash(); settingsPage = SettingsPage.MAIN; route = Route.SETTINGS }
                            )
                        )
                        SettingsPage.CUSTOMIZE -> CustomizationScreen(
                            prefs = p, favoriteCount = vm.currentFavorites(p).size, onChange = vm::update,
                            onPickWallpaper = pickWallpaper, onEditFavorites = { settingsPage = SettingsPage.FAVORITES },
                            onBack = { settingsPage = SettingsPage.MAIN }
                        )
                        SettingsPage.FAVORITES -> FavoritesScreen(
                            apps = apps ?: emptyList(), favorites = vm.currentFavorites(p).toSet(),
                            onToggle = { vm.toggleFavorite(it.packageName) }, onBack = { settingsPage = SettingsPage.CUSTOMIZE }
                        )
                    }
                }
            }
            Dock(route, onSelect = { r -> route = r; if (r == Route.SETTINGS) settingsPage = SettingsPage.MAIN })
        }
    }
}

private fun navigateTo(ctx: Context, query: String, pkg: String?) {
    val withPkg = Launch.navigateTo(query, pkg)
    if (pkg != null && Launch.canResolve(ctx, withPkg)) Launch.start(ctx, withPkg)
    else Launch.start(ctx, Launch.navigateTo(query, null)) // let Android choose any handler
}

@Composable
private fun Wallpaper(p: LauncherPreferences) {
    val c = Fronx.colors
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(c.bgPrimary))
    when (p.wallpaper) {
        WallpaperStyle.PLAIN -> Unit
        WallpaperStyle.CARBON -> Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(c.bgPrimary, c.bgSecondary.copy(alpha = if (c.isDark) 0.9f else 0.6f)))))
        WallpaperStyle.MIDNIGHT -> Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(c.accent.copy(alpha = 0.18f), Color.Transparent), radius = 1100f)))
        WallpaperStyle.CUSTOM -> {
            val uri = p.customWallpaperUri
            val bmp by produceBitmap(ctx, uri)
            bmp?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize().alpha(0.55f), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(c.bgPrimary.copy(alpha = 0.55f)))
            }
        }
    }
}

@Composable
private fun produceBitmap(ctx: Context, uri: String?) =
    androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, uri) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { decodeSampled(ctx.contentResolver, android.net.Uri.parse(uri), 1600) }
    }
