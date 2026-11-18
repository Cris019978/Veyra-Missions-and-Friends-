package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "author_contributions")
data class AuthorContributionEntity(
  @PrimaryKey val authorKey: String,
  val completedOrVerifiedCount: Long = 0L,
  val updatedAtMillis: Long = System.currentTimeMillis(),
)
