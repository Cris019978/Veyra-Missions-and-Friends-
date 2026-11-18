package com.example.data.local

import com.example.data.local.db.AuthorContributionDao
import com.example.data.local.db.AuthorContributionEntity
import com.example.data.local.db.LocalMissionPostDao
import com.example.data.local.db.LocalMissionPostEntity
import com.example.data.model.LocalMissionPost
import com.example.data.remote.SafePublicSpot
import com.example.util.GeoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID
import kotlin.random.Random

/**
 * Repository backing the real-time local mission feed and author contribution scores using Room SQLite storage.
 */
class LocalMissionFeedRepository(
  private val dao: LocalMissionPostDao,
  private val contributionDao: AuthorContributionDao? = null,
) {
  val postsFlow: Flow<List<LocalMissionPost>> = dao.observeAllPosts().map { entities ->
    entities.map { it.toDomain() }
  }

  val contributionScoresFlow: Flow<Map<String, Long>> =
    contributionDao?.observeAllContributions()?.map { entries ->
      entries.associate { it.authorKey.lowercase() to it.completedOrVerifiedCount }
    } ?: flowOf(emptyMap())

  suspend fun recordCompletedOrVerifiedMission(
    authorUserId: String,
    authorUsername: String = "",
    delta: Long = 1L,
  ): Long {
    val key = normalizeAuthorKey(authorUsername = authorUsername, authorUserId = authorUserId)
    return contributionDao?.incrementContribution(authorKey = key, delta = delta) ?: 0L
  }

  suspend fun publishMissionPost(
    title: String,
    description: String,
    category: String,
    xpReward: Long,
    authorUserId: String,
    authorDisplayName: String,
    authorUsername: String,
    authorRole: String,
    authorRoleTitle: String,
    latitude: Double,
    longitude: Double,
    placeLabel: String = "Ubicación GPS actual",
    createdAtMillis: Long = System.currentTimeMillis(),
  ): LocalMissionPost {
    val cleanRole = authorRole.ifBlank { LocalAuthManager.ROLE_EXPLORER }
    val cleanRoleTitle = authorRoleTitle.ifBlank { LocalAuthManager.TITLE_EXPLORER }
    val cleanUsername = authorUsername.removePrefix("@").ifBlank { authorUserId.ifBlank { "explorador" } }
    val cleanDisplay = authorDisplayName.trim().ifBlank { cleanUsername }
    val post = LocalMissionPost(
      id = "feed_" + UUID.randomUUID().toString().replace("-", "").take(14),
      title = title.trim().ifBlank { "Misión local cercana" }.take(80),
      description = description.trim().ifBlank { "Explora este punto cercano en el radar." }.take(280),
      category = category.ifBlank { "explore" },
      xpReward = xpReward.coerceIn(25L, 1000L),
      authorUserId = authorUserId.ifBlank { cleanUsername },
      authorDisplayName = cleanDisplay.take(40),
      authorUsername = cleanUsername.take(24),
      authorRole = cleanRole,
      authorRoleTitle = cleanRoleTitle,
      latitude = latitude.coerceIn(-90.0, 90.0),
      longitude = longitude.coerceIn(-180.0, 180.0),
      placeLabel = placeLabel.trim().ifBlank { "Zona pública cercana" }.take(80),
      createdAtMillis = createdAtMillis,
      isSystemGenerated = false,
    )
    dao.upsertPost(LocalMissionPostEntity.fromDomain(post))
    return post
  }

  suspend fun seedInitialPostsIfEmpty(
    baseLat: Double,
    baseLon: Double,
    safeAnchors: List<SafePublicSpot> = emptyList(),
  ) {
    val now = System.currentTimeMillis()
    contributionDao?.insertIfAbsent(
      SEED_TEMPLATES.map { tmpl ->
        AuthorContributionEntity(
          authorKey = tmpl.authorUsername.lowercase(),
          completedOrVerifiedCount = tmpl.initialContributionScore,
          updatedAtMillis = now,
        )
      }
    )
    if (dao.countPosts() > 0) return
    val batch = buildProximityBatch(
      baseLat = baseLat,
      baseLon = baseLon,
      seed = now,
      nowMillis = now,
      safeAnchors = safeAnchors,
      count = 5,
    )
    dao.upsertPosts(batch.map { LocalMissionPostEntity.fromDomain(it) })
  }

  suspend fun generateNewNearbyBatch(
    baseLat: Double,
    baseLon: Double,
    safeAnchors: List<SafePublicSpot> = emptyList(),
    seed: Long = System.currentTimeMillis(),
  ): List<LocalMissionPost> {
    val now = System.currentTimeMillis()
    val batch = buildProximityBatch(
      baseLat = baseLat,
      baseLon = baseLon,
      seed = seed,
      nowMillis = now,
      safeAnchors = safeAnchors,
      count = 3,
    )
    dao.upsertPosts(batch.map { LocalMissionPostEntity.fromDomain(it) })
    return batch
  }

  private fun buildProximityBatch(
    baseLat: Double,
    baseLon: Double,
    seed: Long,
    nowMillis: Long,
    safeAnchors: List<SafePublicSpot>,
    count: Int,
  ): List<LocalMissionPost> {
    val rng = Random(seed)
    val selectedTemplates = SEED_TEMPLATES.shuffled(rng).take(count)
    return selectedTemplates.mapIndexed { idx, tmpl ->
      val anchor = safeAnchors.getOrNull(idx)
      val (lat, lon, label) = if (anchor != null) {
        val pt = GeoUtils.destinationPoint(anchor.latitude, anchor.longitude, rng.nextDouble(10.0, 35.0), rng.nextDouble(0.0, 360.0))
        Triple(pt.first, pt.second, anchor.name)
      } else {
        // First items are within immediate GPS proximity (25m - 105m), others slightly wider (140m - 520m)
        val distMeters = if (idx < 2) rng.nextDouble(25.0, 105.0) else rng.nextDouble(140.0, 520.0)
        val pt = GeoUtils.destinationPoint(baseLat, baseLon, distMeters, rng.nextDouble(0.0, 360.0))
        Triple(pt.first, pt.second, tmpl.placeLabel)
      }
      val ageMinutes = when (idx) {
        0 -> 0L
        1 -> rng.nextLong(2L, 11L)
        2 -> rng.nextLong(14L, 38L)
        else -> rng.nextLong(45L, 140L)
      }
      LocalMissionPost(
        id = "feed_gen_" + UUID.randomUUID().toString().replace("-", "").take(12),
        title = tmpl.title,
        description = tmpl.description,
        category = tmpl.category,
        xpReward = tmpl.xpReward,
        authorUserId = tmpl.authorUsername,
        authorDisplayName = tmpl.authorDisplayName,
        authorUsername = tmpl.authorUsername,
        authorRole = tmpl.authorRole,
        authorRoleTitle = tmpl.authorRoleTitle,
        latitude = lat,
        longitude = lon,
        placeLabel = label,
        createdAtMillis = nowMillis - (ageMinutes * 60_000L),
        isSystemGenerated = true,
      )
    }
  }

  private data class SeedTemplate(
    val title: String,
    val description: String,
    val category: String,
    val xpReward: Long,
    val authorDisplayName: String,
    val authorUsername: String,
    val authorRole: String,
    val authorRoleTitle: String,
    val placeLabel: String,
    val initialContributionScore: Long,
  )

  companion object {
    fun normalizeAuthorKey(authorUsername: String, authorUserId: String): String {
      val cleanUsername = authorUsername.trim().removePrefix("@").lowercase()
      val cleanId = authorUserId.trim().removePrefix("@").lowercase()
      return cleanUsername.ifBlank { cleanId.ifBlank { "explorador" } }
    }

    private val SEED_TEMPLATES = listOf(
      SeedTemplate(
        title = "Baliza Inicial de Lanzamiento",
        description = "Encuentra un punto abierto cerca de tu radar, toma una foto rápida del horizonte urbano y suma XP extra.",
        category = "explore",
        xpReward = 180L,
        authorDisplayName = "Veyra HQ",
        authorUsername = "veyra",
        authorRole = LocalAuthManager.ROLE_CREATOR,
        authorRoleTitle = LocalAuthManager.TITLE_CREATOR,
        placeLabel = "Plaza pública cercana",
        initialContributionScore = 14L,
      ),
      SeedTemplate(
        title = "Ruta Beta: Reflejos y Copas",
        description = "Misión de prueba de campo: fotografía árboles o reflejos de luz dentro del radio de 120 metros.",
        category = "eco",
        xpReward = 150L,
        authorDisplayName = "Tester Alfa",
        authorUsername = "tester1",
        authorRole = LocalAuthManager.ROLE_BETA_TESTER,
        authorRoleTitle = LocalAuthManager.TITLE_BETA_TESTER,
        placeLabel = "Corredor verde público",
        initialContributionScore = 8L,
      ),
      SeedTemplate(
        title = "Cazador de Murales Locales",
        description = "Captura un detalle artístico, cartelera cultural o escultura accesible desde la banqueta.",
        category = "creative",
        xpReward = 165L,
        authorDisplayName = "Veyra Beta",
        authorUsername = "veyra_beta",
        authorRole = LocalAuthManager.ROLE_BETA_TESTER,
        authorRoleTitle = LocalAuthManager.TITLE_BETA_TESTER,
        placeLabel = "Paseo cultural",
        initialContributionScore = 6L,
      ),
      SeedTemplate(
        title = "Chequeo Relámpago de Caminata",
        description = "Camina hasta el marcador público más cercano y confirma tu paso para mantener la racha diaria.",
        category = "fitness",
        xpReward = 140L,
        authorDisplayName = "Camila Rutas",
        authorUsername = "camila_rutas",
        authorRole = LocalAuthManager.ROLE_EXPLORER,
        authorRoleTitle = LocalAuthManager.TITLE_EXPLORER,
        placeLabel = "Sendero peatonal",
        initialContributionScore = 4L,
      ),
      SeedTemplate(
        title = "Desafío Simetría Urbana",
        description = "Busca arcos, escalinatas o líneas geométricas desde una zona pública segura y compártelo en el feed.",
        category = "challenge",
        xpReward = 175L,
        authorDisplayName = "César E.",
        authorUsername = "cesquivel",
        authorRole = LocalAuthManager.ROLE_CREATOR,
        authorRoleTitle = LocalAuthManager.TITLE_CREATOR,
        placeLabel = "Explanada urbana",
        initialContributionScore = 11L,
      ),
    )
  }
}
