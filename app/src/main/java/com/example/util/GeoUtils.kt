package com.example.util

import com.example.data.model.Mission
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

  private const val EARTH_RADIUS_METERS = 6371000.0

  /**
   * Proximity threshold in meters required to physically unlock and confirm a mission.
   */
  const val PROXIMITY_RADIUS_METERS = 120f

  fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
      sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (EARTH_RADIUS_METERS * c).toFloat()
  }

  fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLambda = Math.toRadians(lon2 - lon1)
    val y = sin(dLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
    val theta = Math.toDegrees(atan2(y, x))
    return ((theta + 360.0) % 360.0).toFloat()
  }

  fun formatDistance(meters: Float): String {
    return if (meters < 1000f) {
      "${meters.toInt().coerceAtLeast(1)} m"
    } else {
      val km = meters / 1000f
      String.format("%.1f km", km)
    }
  }

  /**
   * Built-in real-life quests positioned around the player's current location:
   * some right within immediate proximity (~45-95m) and others further out (~180-250m)
   * so players experience walking closer to unlock proximity points on the radar.
   */
  fun builtInLocalMissions(baseLat: Double, baseLon: Double): List<Mission> {
    return listOf(
      Mission(
        id = "quest_eco_tree",
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = "Guardián Verde",
        description = "Sal afuera, encuentra un árbol o jardín llamativo y confírmalo con una foto en el punto.",
        category = "eco",
        xpReward = 120L,
        latitude = baseLat + 0.0004,
        longitude = baseLon + 0.0003,
        isActive = true,
      ),
      Mission(
        id = "quest_explore_sky",
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = "Horizonte Urbano",
        description = "Acércate a este punto y captura una foto de la fachada, plaza o vista más interesante.",
        category = "explore",
        xpReward = 110L,
        latitude = baseLat - 0.0011,
        longitude = baseLon + 0.0015,
        isActive = true,
      ),
      Mission(
        id = "quest_social_help",
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = "Buena Acción del Día",
        description = "Recoge un envase reciclable, ordena un espacio común o deja un gesto amable en la zona.",
        category = "social",
        xpReward = 160L,
        latitude = baseLat + 0.0007,
        longitude = baseLon - 0.0004,
        isActive = true,
      ),
      Mission(
        id = "quest_challenge_art",
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = "Cazador de Detalles",
        description = "Camina hasta el marcador para fotografiar un letrero curioso o patrón geométrico.",
        category = "challenge",
        xpReward = 150L,
        latitude = baseLat - 0.0017,
        longitude = baseLon - 0.0009,
        isActive = true,
      ),
      Mission(
        id = "quest_fitness_walk",
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = "Ruta Activa 10 Min",
        description = "Camina hasta este punto de control al aire libre y demuestra tu llegada con foto.",
        category = "fitness",
        xpReward = 140L,
        latitude = baseLat + 0.0019,
        longitude = baseLon - 0.0005,
        isActive = true,
      ),
      Mission(
        id = "quest_creative_mural",
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = "Galería Callejera",
        description = "Acércate al marcador y captura un mural, escultura o detalle arquitectónico cercano.",
        category = "creative",
        xpReward = 155L,
        latitude = baseLat - 0.0006,
        longitude = baseLon - 0.0020,
        isActive = true,
      ),
    )
  }
}
