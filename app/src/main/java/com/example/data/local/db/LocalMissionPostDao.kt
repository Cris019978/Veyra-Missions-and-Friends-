package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMissionPostDao {
  @Query("SELECT * FROM local_mission_posts ORDER BY createdAtMillis DESC")
  fun observeAllPosts(): Flow<List<LocalMissionPostEntity>>

  @Query("SELECT COUNT(*) FROM local_mission_posts")
  suspend fun countPosts(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPost(post: LocalMissionPostEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPosts(posts: List<LocalMissionPostEntity>)

  @Query("DELETE FROM local_mission_posts WHERE isSystemGenerated = 1")
  suspend fun clearSystemGeneratedPosts()
}
