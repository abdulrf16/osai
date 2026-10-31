package com.fronx.smartlauncher.media

import android.service.notification.NotificationListenerService

/**
 * Intentionally empty. Android only lets an app enumerate other apps' media sessions
 * (MediaSessionManager.getActiveSessions) if the caller names a NotificationListenerService that the
 * user has enabled under Settings > Notification access. This class exists only to be that component:
 * it never reads, stores or forwards any notification.
 */
class MediaNotificationListener : NotificationListenerService()
