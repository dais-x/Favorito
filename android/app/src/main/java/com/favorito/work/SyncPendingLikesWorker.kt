package com.favorito.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.favorito.SettingsStore
import com.favorito.data.FavoritoDatabase
import com.favorito.data.PendingLikeEntity

class SyncPendingLikesWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        SettingsStore.init(applicationContext)
        val token = SettingsStore.youtubeOauthToken
        if (token.isBlank()) return Result.retry()

        val dao = FavoritoDatabase.get(applicationContext).pendingLikeDao()
        val pending = dao.pending(PendingLikeEntity.STATUS_PENDING, 25)
        if (pending.isEmpty()) return Result.success()

        val client = YoutubeDataApiClient(token)
        var retryNeeded = false

        pending.forEach { item ->
            val result = client.like(item)
            if (result.isSuccess) {
                dao.markStatus(item.id, PendingLikeEntity.STATUS_SYNCED, System.currentTimeMillis())
            } else {
                retryNeeded = true
                dao.markAttemptFailed(
                    item.id,
                    result.exceptionOrNull()?.message ?: "unknown sync error",
                    System.currentTimeMillis()
                )
            }
        }

        if (retryNeeded) {
            return Result.retry()
        }

        return Result.success()
    }
}
