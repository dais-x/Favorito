package com.favorito

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

/**
 * Short-lived foreground service used only for the microphone prompt.
 *
 * Android restricts microphone access from background components. This service
 * is started only after the five-second threshold, posts a small foreground
 * notification while speech recognition is active, then stops itself in the
 * PromptCoordinator cleanup callback.
 */
class PromptForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SettingsStore.masterEnabled || intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        val track = TrackSnapshot(
            trackKey = intent.getStringExtra(EXTRA_TRACK_KEY).orEmpty(),
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
            durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        )

        if (track.trackKey.isBlank() || track.title.isBlank() || track.durationMs <= 0L) {
            stopForegroundCompat()
            stopSelf(startId)
            return START_NOT_STICKY
        }

        PromptCoordinator.start(this, track) {
            stopForegroundCompat()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Favorito prompt",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown only while Favorito listens for yes or no."
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_stat_favorito)
            .setContentTitle("Favorito")
            .setContentText("Listening for yes or no")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val EXTRA_TRACK_KEY = "track_key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_DURATION_MS = "duration_ms"

        private const val CHANNEL_ID = "favorito_prompt"
        private const val NOTIFICATION_ID = 42
    }
}
