package com.osai.voiceassistant.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.view.KeyEvent
import com.osai.voiceassistant.service.NotificationMessageReader
import com.osai.voiceassistant.utils.Logger

/**
 * Controls whichever media app currently holds an active [MediaController]
 * session (YouTube Music, Spotify, etc.) via the system [MediaSessionManager].
 *
 * Requires notification listener access to be granted, since
 * [MediaSessionManager.getActiveSessions] needs a bound
 * NotificationListenerService component to authorize the query. We reuse
 * [NotificationMessageReader], which the app already needs for message
 * reading, so no extra permission surface is introduced.
 */
class MediaControlManager(private val context: Context) {

    private val mediaSessionManager =
        context.applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    fun play(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY) || dispatch { it.transportControls.play() }

    fun pause(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE) || dispatch { it.transportControls.pause() }

    fun resume(): Boolean = play()

    fun next(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) || dispatch { it.transportControls.skipToNext() }

    fun previous(): Boolean =
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) || dispatch { it.transportControls.skipToPrevious() }

    fun stop(): Boolean = sendMediaKey(KeyEvent.KEYCODE_MEDIA_STOP) || dispatch { it.transportControls.stop() }

    /**
     * Dispatches a transport action to the highest priority active media
     * session. Returns false (without throwing) if no session is active or
     * notification listener access has not been granted, so callers can give
     * graceful spoken feedback instead of crashing.
     */
    private fun dispatch(action: (MediaController) -> Unit): Boolean {
        val controller = activeController() ?: run {
            Logger.w(TAG, "No active media session available to control")
            return false
        }
        return try {
            action(controller)
            Logger.d(TAG, "Dispatched media action to ${controller.packageName}")
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "Notification listener access not granted for media control", e)
            false
        }
    }

    private fun activeController(): MediaController? {
        return try {
            val listenerComponent = ComponentName(context, NotificationMessageReader::class.java)
            mediaSessionManager.getActiveSessions(listenerComponent).firstOrNull()
        } catch (e: SecurityException) {
            Logger.e(TAG, "Cannot read active media sessions - missing notification access", e)
            null
        }
    }

    /**
     * Fallback path: broadcasting a synthetic media button key event is
     * honoured by most media apps' MediaSession media button receivers even
     * without notification listener access, so we try this first as it is
     * less privileged and more broadly compatible.
     */
    private fun sendMediaKey(keyCode: Int): Boolean {
        val controller = activeController() ?: return false
        return try {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            controller.dispatchMediaButtonEvent(down)
            controller.dispatchMediaButtonEvent(up)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    companion object {
        private const val TAG = "MediaControlManager"
    }
}
