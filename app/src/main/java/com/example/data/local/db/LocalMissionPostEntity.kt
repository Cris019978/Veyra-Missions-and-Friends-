package com.example.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.LocalMissionPost

@Entity(tableName = "local_mission_posts")
data class LocalMissionPostEntity(
  @PrimaryKey val id: String,
  val title: String,
  val description: String,
  val category: String,
  val xpReward: Long,
  val authorUserId: String,
  val authorDisplayName: String,
  val authorUsername: String,
  val authorRole: String,
  val authorRoleTitle: String,
  val latitude: Double,
  val longitude: Double,
  val placeLabel: String,
  val createdAtMillis: Long,
  val isSystemGenerated: Boolean,
) {
  fun toDomain(): LocalMissionPost = LocalMissionPost(
    id = id,
    title = title,
    description = description,
    category = category,
    xpReward = xpReward,
    authorUserId = authorUserId,
    authorDisplayName = authorDisplayName,
    authorUsername = authorUsername,
    authorRole = authorRole,
    authorRoleTitle = authorRoleTitle,
    latitude = latitude,
    longitude = longitude,
    placeLabel = placeLabel,
    createdAtMillis = createdAtMillis,
    isSystemGenerated = isSystemGenerated,
  )

  companion object {
    fun fromDomain(post: LocalMissionPost): LocalMissionPostEntity = LocalMissionPostEntity(
      id = post.id,
      title = post.title,
      description = post.description,
      category = post.category,
      xpReward = post.xpReward,
      authorUserId = post.authorUserId,
      authorDisplayName = post.authorDisplayName,
      authorUsername = post.authorUsername,
      authorRole = post.authorRole,
      authorRoleTitle = post.authorRoleTitle,
      latitude = post.latitude,
      longitude = post.longitude,
      placeLabel = post.placeLabel,
      createdAtMillis = post.createdAtMillis,
      isSystemGenerated = post.isSystemGenerated,
    )
  }
}
