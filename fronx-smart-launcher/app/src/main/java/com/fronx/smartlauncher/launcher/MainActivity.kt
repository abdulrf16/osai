package com.fronx.smartlauncher.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.fronx.smartlauncher.preferences.WallpaperStyle
import com.fronx.smartlauncher.utils.Launch

/**
 * The HOME activity. singleTask so pressing Home returns here instead of stacking copies.
 * Does not hide or intercept the system bars / navigation.
 */
class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    private var homeTick by mutableIntStateOf(0)

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { /* status re-read on resume */ }

    private val wallpaperPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.update { it.copy(customWallpaperUri = uri.toString(), wallpaper = WallpaperStyle.CUSTOM) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherRoot(vm, homeTick, requestDefaultHome = ::requestDefaultHome, pickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) })
        }
    }

    override fun onStart() { super.onStart(); vm.onVisible() }
    override fun onStop() { super.onStop(); vm.onHidden() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeTick++
    }

    /** Role request when supported, else documented Settings screens. Never changes the default silently. */
    private fun requestDefaultHome() {
        val role = Launch.homeRoleRequest(this)
        if (role != null && runCatching { roleRequest.launch(role) }.isSuccess) return
        for (i in Launch.homeSettingsChain()) {
            if (Launch.canResolve(this, i) && Launch.start(this, i)) return
        }
    }
}
