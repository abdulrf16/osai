package com.fronx.smartlauncher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.fronx.smartlauncher.utils.Launch
import com.fronx.smartlauncher.utils.scaledDown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MediaUiState {
    /** Notification access not granted: sessions of other apps cannot be listed. */
    data object AccessRequired : MediaUiState
    data object NoSession : MediaUiState
    data class Active(
        val packageName: String,
        val appName: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val art: Bitmap?,
        val isPlaying: Boolean,
        val statusLabel: String,
        /** -1 when the app does not report it. */
        val positionMs: Long,
        /** <= 0 when unknown (e.g. live streams). */
        val durationMs: Long,
        val updatedAtElapsed: Long,
        val speed: Float,
        val canPlayPause: Boolean,
        val canPrevious: Boolean,
        val canNext: Boolean
    ) : MediaUiState {
        val hasProgress get() = durationMs > 0 && positionMs >= 0
    }
}

/**
 * Wraps MediaSessionManager / MediaController. Listeners are only registered between attach() and
 * detach() (launcher visible), so there is no background work while another app is in front.
 */
class MediaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val manager: MediaSessionManager? = appContext.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(appContext, MediaNotificationListener::class.java)

    private val _state = MutableStateFlow<MediaUiState>(MediaUiState.NoSession)
    val state: StateFlow<MediaUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var sessionListenerRegistered = false
    private var artSource: Bitmap? = null
    private var artScaled: Bitmap? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        select(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    fun hasAccess(): Boolean = Launch.hasNotificationAccess(appContext)

    /** Call whenever the launcher becomes visible (also picks up access granted in Settings). */
    fun attach() = refresh()

    fun refresh() {
        val m = manager
        if (m == null || !hasAccess()) {
            unregisterAll()
            _state.value = MediaUiState.AccessRequired
            return
        }
        try {
            if (!sessionListenerRegistered) {
                m.addOnActiveSessionsChangedListener(sessionsListener, component)
                sessionListenerRegistered = true
            }
            select(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            unregisterAll()
            _state.value = MediaUiState.AccessRequired
        }
    }

    fun detach() = unregisterAll()

    private fun unregisterAll() {
        controller?.let { runCatching { it.unregisterCallback(callback) } }
        controller = null
        if (sessionListenerRegistered) {
            runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
            sessionListenerRegistered = false
        }
    }

    private fun select(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null }
            ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.let { runCatching { it.unregisterCallback(callback) } }
            controller = best
            best?.let { runCatching { it.registerCallback(callback) } }
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) { _state.value = MediaUiState.NoSession; return }
        val md = runCatching { c.metadata }.getOrNull()
        val ps = runCatching { c.playbackState }.getOrNull()
        val actions = ps?.actions ?: 0L
        val playing = ps?.state == PlaybackState.STATE_PLAYING
        val rawArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (rawArt !== artSource) { artSource = rawArt; artScaled = rawArt?.scaledDown(ART_MAX_EDGE) }
        val title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
            ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.takeIf { it.isNotBlank() }
            ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)?.takeIf { it.isNotBlank() }
        _state.value = MediaUiState.Active(
            packageName = c.packageName,
            appName = appLabel(c.packageName),
            title = title,
            artist = artist,
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() },
            art = artScaled,
            isPlaying = playing,
            statusLabel = statusLabel(ps?.state),
            positionMs = ps?.position?.takeIf { it != PlaybackState.PLAYBACK_POSITION_UNKNOWN } ?: -1L,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            updatedAtElapsed = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            canPlayPause = actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
            canPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
        )
    }

    private fun statusLabel(state: Int?) = when (state) {
        PlaybackState.STATE_PLAYING -> "Playing"
        PlaybackState.STATE_PAUSED -> "Paused"
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> "Buffering"
        PlaybackState.STATE_STOPPED -> "Stopped"
        PlaybackState.STATE_ERROR -> "Error"
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> "Skipping"
        else -> "Idle"
    }

    /** Falls back to the package name if the app label is not visible to us. */
    private fun appLabel(pkg: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // ---- transport controls: every call guarded, apps may die at any time ----
    fun togglePlayPause() {
        val c = controller ?: return
        runCatching {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
            else c.transportControls.play()
        }
    }

    fun next() { runCatching { controller?.transportControls?.skipToNext() } }
    fun previous() { runCatching { controller?.transportControls?.skipToPrevious() } }

    /** Opens the session's own activity if it provides one, else the app's launcher activity. */
    fun openApp(): Boolean {
        val c = controller ?: return false
        val sent = runCatching { c.sessionActivity?.send() }.isSuccess && c.sessionActivity != null
        return sent || Launch.packageLaunch(appContext, c.packageName)
    }

    private companion object { const val ART_MAX_EDGE = 640 }
}
