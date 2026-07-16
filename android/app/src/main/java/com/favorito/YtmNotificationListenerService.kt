package com.favorito

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Event-driven monitor for YouTube Music.
 *
 * There is deliberately no loop that polls current playback position. Android
 * pushes MediaSession callbacks when the player changes state or metadata. On
 * each push, this service schedules exactly one threshold runnable for the
 * current track and cancels it as soon as the state changes.
 */
class YtmNotificationListenerService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var activeTrackKey: String? = null
    private var thresholdTrackKey: String? = null
    private val promptInFlight = AtomicBoolean(false)

    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            main.post {
                if (!SettingsStore.masterEnabled) {
                    goIdle()
                } else {
                    refreshActiveSession()
                }
            }
        }

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener {
            main.post { refreshActiveSession() }
        }

    private val thresholdRunnable = Runnable {
        val controller = activeController ?: return@Runnable
        val metadata = controller.metadata ?: return@Runnable
        val state = controller.playbackState ?: return@Runnable
        val snapshot = metadata.toTrackSnapshot() ?: return@Runnable

        if (snapshot.trackKey != activeTrackKey || thresholdTrackKey == snapshot.trackKey) return@Runnable
        if (!state.isActuallyPlaying()) return@Runnable

        val remaining = metadata.durationMs() - state.currentPositionMs()
        if (remaining in 0L..5_500L) {
            thresholdTrackKey = snapshot.trackKey
            startDecisionFlow(snapshot)
        } else {
            scheduleThresholdIfNeeded(controller, metadata, state)
        }
    }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            handleControllerUpdate(activeController?.metadata, state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            handleControllerUpdate(metadata, activeController?.playbackState)
        }

        override fun onSessionDestroyed() {
            goIdle()
        }
    }

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        getSharedPreferences("favorito_settings", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(settingsListener)
        mediaSessionManager = getSystemService(MediaSessionManager::class.java)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (SettingsStore.masterEnabled) {
        mediaSessionManager?.addOnActiveSessionsChangedListener(
                sessionsChangedListener,
                ComponentName(this, YtmNotificationListenerService::class.java)
            )
            refreshActiveSession()
        } else {
            goIdle()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        goIdle()
    }

    override fun onDestroy() {
        goIdle()
        getSharedPreferences("favorito_settings", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(settingsListener)
        super.onDestroy()
    }

    private fun refreshActiveSession() {
        if (!SettingsStore.masterEnabled) {
            goIdle()
            return
        }

        val component = ComponentName(this, YtmNotificationListenerService::class.java)
        val ytmController = mediaSessionManager
            ?.getActiveSessions(component)
            ?.firstOrNull { it.packageName == YOUTUBE_MUSIC_PACKAGE }

        if (ytmController == null) {
            goIdle()
            return
        }

        if (activeController?.sessionToken != ytmController.sessionToken) {
            activeController?.unregisterCallback(controllerCallback)
            activeController = ytmController
            ytmController.registerCallback(controllerCallback, main)
        }

        handleControllerUpdate(ytmController.metadata, ytmController.playbackState)
    }

    private fun handleControllerUpdate(metadata: MediaMetadata?, state: PlaybackState?) {
        main.removeCallbacks(thresholdRunnable)

        if (!SettingsStore.masterEnabled || metadata == null || state == null || !state.isActuallyPlaying()) {
            PlaybackStateHolder.clear()
            return
        }

        val snapshot = metadata.toTrackSnapshot()
        if (snapshot == null) {
            PlaybackStateHolder.clear()
            return
        }

        if (activeTrackKey != snapshot.trackKey) {
            activeTrackKey = snapshot.trackKey
            thresholdTrackKey = null
        }

        PlaybackStateHolder.isMusicActivelyPlaying = true
        PlaybackStateHolder.activePackageName = YOUTUBE_MUSIC_PACKAGE
        PlaybackStateHolder.lastTrackKey = snapshot.trackKey

        scheduleThresholdIfNeeded(activeController, metadata, state)
    }

    private fun scheduleThresholdIfNeeded(
        controller: MediaController?,
        metadata: MediaMetadata,
        state: PlaybackState
    ) {
        if (controller == null || !state.isActuallyPlaying()) return
        val durationMs = metadata.durationMs()
        if (durationMs <= 0L) return

        val currentPositionMs = state.currentPositionMs().coerceAtLeast(0L)
        val remainingMs = durationMs - currentPositionMs
        if (remainingMs <= 0L) return

        val delayMs = (remainingMs - THRESHOLD_REMAINING_MS).coerceAtLeast(0L)
        main.removeCallbacks(thresholdRunnable)
        main.postDelayed(thresholdRunnable, delayMs)
    }

    private fun startDecisionFlow(snapshot: TrackSnapshot) {
        if (!SettingsStore.masterEnabled) return
        if (!promptInFlight.compareAndSet(false, true)) return

        val intent = Intent(this, PromptForegroundService::class.java)
            .putExtra(PromptForegroundService.EXTRA_TRACK_KEY, snapshot.trackKey)
            .putExtra(PromptForegroundService.EXTRA_TITLE, snapshot.title)
            .putExtra(PromptForegroundService.EXTRA_ARTIST, snapshot.artist)
            .putExtra(PromptForegroundService.EXTRA_DURATION_MS, snapshot.durationMs)
        try {
            startForegroundService(intent)
            main.postDelayed({ promptInFlight.set(false) }, PROMPT_GUARD_MS)
        } catch (_: RuntimeException) {
            promptInFlight.set(false)
        }
    }

    private fun goIdle() {
        main.removeCallbacks(thresholdRunnable)
        activeController?.unregisterCallback(controllerCallback)
        activeController = null
        activeTrackKey = null
        thresholdTrackKey = null
        promptInFlight.set(false)
        PlaybackStateHolder.clear()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
    }

    private fun PlaybackState.isActuallyPlaying(): Boolean =
        state == PlaybackState.STATE_PLAYING && playbackSpeed > 0f

    private fun PlaybackState.currentPositionMs(): Long {
        val base = position.takeIf { it >= 0L } ?: 0L
        if (!isActuallyPlaying()) return base
        val delta = SystemClock.elapsedRealtime() - lastPositionUpdateTime
        return base + (delta * playbackSpeed).toLong()
    }

    private fun MediaMetadata.durationMs(): Long =
        getLong(MediaMetadata.METADATA_KEY_DURATION)

    private fun MediaMetadata.toTrackSnapshot(): TrackSnapshot? {
        val title = getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
            ?: getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)?.trim().orEmpty()
        val duration = durationMs()
        if (title.isBlank() || duration <= 0L) return null

        val mediaId = getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
        val key = listOf(mediaId, title, artist, duration.toString())
            .joinToString("|")
            .lowercase()
        return TrackSnapshot(key, title, artist, duration)
    }

    companion object {
        private const val YOUTUBE_MUSIC_PACKAGE = "com.google.android.apps.youtube.music"
        private const val THRESHOLD_REMAINING_MS = 5_000L
        private const val PROMPT_GUARD_MS = 12_000L
    }
}
