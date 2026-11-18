package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserSettings(
  @DocumentId val id: String = "",
  val userId: String = "",
  val themeMode: String = "system", // system, light, dark
  val paletteKey: String = "dynamic", // dynamic, emerald, coral, indigo, amber
  val radarStyle: String = "neon", // classic, neon, compass
  val cornerStyle: String = "soft", // soft, pill, crisp
  val searchRadiusMeters: Long = 2000L,
  val animationTier: String = "high", // low, medium, high
  val notificationsEnabled: Boolean = true,
  // Private first-launch onboarding & taste preferences (strictly readable/writable only by owner)
  val onboardingCompleted: Boolean = false,
  val playStyle: String = "balanced", // helper, competitor, explorer, balanced
  val favoriteCategories: List<String> = emptyList(),
  val privacyComfort: String = "radius", // radius, close_only, ghost
  val missionPace: String = "casual", // casual, daily, intense
  val gestureNavEnabled: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

enum class MissionCategory(
  val key: String,
  val label: String,
  val shortDesc: String,
) {
  EXPLORE("explore", "Exploración", "Lugares, vistas y rutas urbanas"),
  ECO("eco", "Ecología", "Naturaleza, parques y reciclaje"),
  SOCIAL("social", "Comunidad", "Ayudar a otros y buenos gestos"),
  CHALLENGE("challenge", "Reto Foto", "Capturas creativas y detalles"),
  FITNESS("fitness", "Movimiento", "Caminatas, pasos y aire libre"),
  CREATIVE("creative", "Arte Urbano", "Murales, diseño y arquitectura"),
  PUZZLE("puzzle", "Puzzle", "Acertijos, cables y retos cronometrados");

  companion object {
    fun fromKey(key: String): MissionCategory =
      entries.firstOrNull { it.key == key } ?: EXPLORE
  }
}

@IgnoreExtraProperties
data class PlayerProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val username: String = "",
  val callsign: String = "",
  val avatarIcon: String = "explore",
  val avatarColorHex: String = "#00BFA5",
  val avatarPhotoBase64: String = "",
  val bannerPhotoBase64: String = "",
  val bannerPreset: String = "topo",
  val statusMessage: String = "Explorando cerca",
  val seekingHelp: Boolean = false,
  val isOnline: Boolean = true,
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val xp: Long = 0L,
  val level: Long = 1L,
  val missionsCompleted: Long = 0L,
  val helpsGiven: Long = 0L,
  val duelsWon: Long = 0L,
  val role: String = "explorer", // explorer, creator, beta_tester
  val roleTitle: String = "Explorador",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Mission(
  @DocumentId val id: String = "",
  val creatorId: String = "",
  val creatorName: String = "",
  val title: String = "",
  val description: String = "",
  val category: String = "explore", // explore, eco, social, challenge, fitness, creative, puzzle
  val missionType: String = "photo", // photo, wires, puzzle, memory
  val iconKey: String = "explore",
  val timeLimitSeconds: Int = 0, // 0 = sin límite, 60 = temporizador de 1 minuto
  val placeLabel: String = "Zona pública segura",
  val isSafePublicVerified: Boolean = true,
  val xpReward: Long = 100L,
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val isActive: Boolean = true,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MissionCompletion(
  @DocumentId val id: String = "",
  val userId: String = "",
  val userName: String = "",
  val missionId: String = "",
  val missionTitle: String = "",
  val category: String = "explore",
  val xpEarned: Long = 100L,
  val caption: String = "",
  val photoThumbnail: String = "",
  val latitude: Double = 0.0,
  val longitude: Double = 0.0,
  val isPublic: Boolean = true,
  val authorRole: String = "explorer",
  val authorRoleTitle: String = "Explorador",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Challenge(
  @DocumentId val id: String = "",
  val creatorId: String = "",
  val creatorName: String = "",
  val opponentId: String = "",
  val opponentName: String = "",
  val mode: String = "duel", // duel, coop
  val title: String = "",
  val missionId: String = "",
  val xpStake: Long = 150L,
  val status: String = "open", // open, active, completed, cancelled
  val participantIds: List<String> = emptyList(),
  val creatorProgress: Long = 0L,
  val opponentProgress: Long = 0L,
  val winnerId: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PlayerInteraction(
  @DocumentId val id: String = "",
  val fromUserId: String = "",
  val fromUserName: String = "",
  val toUserId: String = "",
  val toUserName: String = "",
  val type: String = "help", // help, pulse, cheer
  val message: String = "",
  val xpBonus: Long = 25L,
  val participantIds: List<String> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
)

data class NearbyPlayerItem(
  val profile: PlayerProfile,
  val distanceMeters: Float,
  val bearingDegrees: Float,
)

data class MissionWithDistance(
  val mission: Mission,
  val distanceMeters: Float,
  val bearingDegrees: Float,
  val isCompletedByMe: Boolean,
  val isWithinProximity: Boolean = false,
)

data class AppNotificationItem(
  val id: String,
  val title: String,
  val message: String,
  val type: String, // proximity, help, pulse, challenge
  val timestampMs: Long,
  val isRead: Boolean = false,
)

data class NearbyMissionPopup(
  val missionId: String,
  val title: String,
  val category: String,
  val distanceMeters: Float,
  val xpReward: Long,
)

