package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AuthorContributionDao {
  @Query("SELECT * FROM author_contributions")
  fun observeAllContributions(): Flow<List<AuthorContributionEntity>>

  @Query("SELECT * FROM author_contributions WHERE authorKey = :authorKey LIMIT 1")
  suspend fun getContribution(authorKey: String): AuthorContributionEntity?

  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun insertIfAbsent(entries: List<AuthorContributionEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertContribution(entry: AuthorContributionEntity)

  @Transaction
  suspend fun incrementContribution(
    authorKey: String,
    delta: Long = 1L,
    nowMillis: Long = System.currentTimeMillis(),
  ): Long {
    val cleanKey = authorKey.trim().lowercase()
    if (cleanKey.isBlank() || delta <= 0L) return 0L
    val current = getContribution(cleanKey)?.completedOrVerifiedCount ?: 0L
    val next = (current + delta).coerceAtLeast(0L)
    upsertContribution(
      AuthorContributionEntity(
        authorKey = cleanKey,
        completedOrVerifiedCount = next,
        updatedAtMillis = nowMillis,
      )
    )
    return next
  }
}
