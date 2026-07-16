package com.favorito

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.favorito.data.FavoritoDatabase
import com.favorito.data.PendingLikeEntity
import com.favorito.work.SyncPendingLikesWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object LikeExecutor {
    private const val YTM_PACKAGE = "com.google.android.apps.youtube.music"
    private const val HIDDEN_LIKE_ACTION =
        "com.google.android.youtube.music.pendingintent.controller_widget_like"

    fun like(context: Context, track: TrackSnapshot) {
        when (SettingsStore.likeActionMode) {
            SettingsStore.LIKE_QUEUE -> queueForYoutubeApi(context, track)
            else -> sendHiddenLikeIntent(context)
        }
    }

    /**
     * Approach A: hidden controller-widget broadcast.
     *
     * This is intentionally isolated because it is undocumented and may stop
     * working on any YouTube Music release. It costs no idle battery: it sends
     * exactly one explicit package broadcast only after the user says yes.
     */
    fun sendHiddenLikeIntent(context: Context) {
        val intent = Intent(HIDDEN_LIKE_ACTION).apply {
            setPackage(YTM_PACKAGE)
        }
        context.applicationContext.sendBroadcast(intent)
    }

    /**
     * Approach B: offline queue.
     *
     * The database write happens once after an affirmative answer. WorkManager
     * owns network wakeups using NetworkType.CONNECTED, so the app does not poll
     * connectivity or hold a resident service.
     */
    fun queueForYoutubeApi(context: Context, track: TrackSnapshot) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            FavoritoDatabase.get(app).pendingLikeDao().insert(
                PendingLikeEntity(
                    title = track.title,
                    artist = track.artist,
                    durationMs = track.durationMs
                )
            )
            enqueueSync(app)
        }
    }

    fun enqueueSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncPendingLikesWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "sync_pending_youtube_music_likes",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
