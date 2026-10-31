package com.fronx.smartlauncher.utils

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat

/** Safe wrappers around startActivity using only documented Android intents. */
object Launch {

    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { // ActivityNotFound / Security: never crash the home screen
        Toast.makeText(context, "Not available on this device", Toast.LENGTH_SHORT).show()
        false
    }

    fun component(packageName: String, activity: String): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(packageName, activity))
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun packageLaunch(context: Context, packageName: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (i != null) start(context, i) else {
            Toast.makeText(context, "App not installed", Toast.LENGTH_SHORT).show(); false
        }
    }

    fun canResolve(context: Context, intent: Intent): Boolean =
        runCatching { intent.resolveActivity(context.packageManager) != null }.getOrDefault(false)

    // ---- documented Settings screens ----
    fun display() = Intent(Settings.ACTION_DISPLAY_SETTINGS)
    fun sound() = Intent(Settings.ACTION_SOUND_SETTINGS)
    fun bluetooth() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun wifi() = Intent(Settings.ACTION_WIFI_SETTINGS)
    fun system() = Intent(Settings.ACTION_SETTINGS)
    fun notificationAccess() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun appDetails(pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))

    // ---- phone / contacts ----
    fun dialer() = Intent(Intent.ACTION_DIAL)
    fun contacts(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)

    // ---- navigation ----
    const val MAPS_PACKAGE = "com.google.android.apps.maps"

    fun mapsHome(): Intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)

    /** Turn-by-turn request for an address / place. Google Maps uses its documented scheme; others get geo:. */
    fun navigateTo(query: String, packageName: String?): Intent {
        val enc = Uri.encode(query)
        val i = if (packageName == MAPS_PACKAGE) Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$enc"))
        else Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$enc"))
        if (packageName != null) i.setPackage(packageName)
        return i
    }

    // ---- HOME role ----
    fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun isDefaultHome(context: Context): Boolean = runCatching {
        val info = context.packageManager.resolveActivity(homeIntent(), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        info?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    /** Role-request intent when the OS supports ROLE_HOME and this app doesn't hold it yet. */
    fun homeRoleRequest(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = androidx.core.content.ContextCompat.getSystemService(context, RoleManager::class.java) ?: return null
        return if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME))
            rm.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }

    /** Documented fall-backs for choosing the default home app, most specific first. */
    fun homeSettingsChain(): List<Intent> = listOf(
        Intent(Settings.ACTION_HOME_SETTINGS),
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
