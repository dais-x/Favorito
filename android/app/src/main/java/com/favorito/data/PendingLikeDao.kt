package com.favorito.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface PendingLikeDao {
    @Insert
    suspend fun insert(entity: PendingLikeEntity): Long

    @Query("SELECT * FROM pending_likes WHERE status = :status ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(status: String, limit: Int): List<PendingLikeEntity>

    @Query("UPDATE pending_likes SET status = :status, updatedAt = :updatedAt, lastError = NULL WHERE id = :id")
    suspend fun markStatus(id: Long, status: String, updatedAt: Long)

    @Query("UPDATE pending_likes SET attempts = attempts + 1, lastError = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAttemptFailed(id: Long, error: String, updatedAt: Long)
}
