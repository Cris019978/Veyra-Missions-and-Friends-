package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.Mission
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object GeoUtils {

  private const val EARTH_RADIUS_METERS = 6371000.0

  /**
   * Proximity threshold in meters required to physically unlock and confirm a mission.
   */
  const val PROXIMITY_RADIUS_METERS = 120f

  /**
   * Very close radius threshold in meters that triggers close-spot guiding notifications.
   */
  const val CLOSE_GUIDANCE_RADIUS_METERS = 35f

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

  fun destinationPoint(
    baseLat: Double,
    baseLon: Double,
    distanceMeters: Double,
    bearingDegrees: Double,
  ): Pair<Double, Double> {
    val angularDist = distanceMeters / EARTH_RADIUS_METERS
    val bearingRad = Math.toRadians(bearingDegrees)
    val lat1Rad = Math.toRadians(baseLat)
    val lon1Rad = Math.toRadians(baseLon)

    val lat2Rad = asin(
      sin(lat1Rad) * cos(angularDist) +
        cos(lat1Rad) * sin(angularDist) * cos(bearingRad)
    )
    val lon2Rad = lon1Rad + atan2(
      sin(bearingRad) * sin(angularDist) * cos(lat1Rad),
      cos(angularDist) - sin(lat1Rad) * sin(lat2Rad)
    )
    return Math.toDegrees(lat2Rad).coerceIn(-89.9, 89.9) to Math.toDegrees(lon2Rad).coerceIn(-179.9, 179.9)
  }

  fun cardinalDirectionEs(bearingDegrees: Float): String {
    val norm = ((bearingDegrees % 360f) + 360f) % 360f
    return when {
      norm < 22.5f || norm >= 337.5f -> "Norte"
      norm < 67.5f -> "Noreste"
      norm < 112.5f -> "Este"
      norm < 157.5f -> "Sureste"
      norm < 202.5f -> "Sur"
      norm < 247.5f -> "Suroeste"
      norm < 292.5f -> "Oeste"
      else -> "Noroeste"
    }
  }

  fun compassArrowForBearing(bearingDegrees: Float): String {
    val norm = ((bearingDegrees % 360f) + 360f) % 360f
    return when {
      norm < 22.5f || norm >= 337.5f -> "↑"
      norm < 67.5f -> "↗"
      norm < 112.5f -> "→"
      norm < 157.5f -> "↘"
      norm < 202.5f -> "↓"
      norm < 247.5f -> "↙"
      norm < 292.5f -> "←"
      else -> "↖"
    }
  }

  fun formatDistance(meters: Float): String {
    return if (meters < 1000f) {
      "${meters.toInt().coerceAtLeast(1)} m"
    } else {
      val km = meters / 1000f
      String.format("%.1f km", km)
    }
  }

  fun openInGoogleMaps(
    context: Context,
    latitude: Double,
    longitude: Double,
    title: String,
  ): Boolean {
    val label = Uri.encode(title)
    val geoIntent = Intent(
      Intent.ACTION_VIEW,
      Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)"),
    ).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return runCatching {
      context.startActivity(geoIntent)
      true
    }.getOrElse {
      val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"),
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      runCatching {
        context.startActivity(webIntent)
        true
      }.getOrDefault(false)
    }
  }

  private data class MissionTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val missionType: String,
    val iconKey: String,
    val xpReward: Long,
  )

  private val MISSION_TEMPLATES = listOf(
    MissionTemplate(
      id = "quest_eco_tree",
      title = "Guardián Verde",
      description = "Encuentra un árbol o jardín llamativo en este sector y confírmalo con una foto.",
      category = "eco",
      missionType = "photo",
      iconKey = "park",
      xpReward = 120L,
    ),
    MissionTemplate(
      id = "quest_wires_solar",
      title = "Circuito Solar Urbano",
      description = "Llega al punto y reconecta los 5 cables de colores para reactivar el nodo de energía limpia.",
      category = "eco",
      missionType = "wires",
      iconKey = "bolt",
      xpReward = 155L,
    ),
    MissionTemplate(
      id = "quest_puzzle_botanico",
      title = "Mosaico del Jardín",
      description = "Acércate al marcador y arma el rompecabezas deslizable del mapa botánico.",
      category = "eco",
      missionType = "puzzle",
      iconKey = "eco",
      xpReward = 145L,
    ),
    MissionTemplate(
      id = "quest_explore_sky",
      title = "Horizonte Urbano",
      description = "Acércate a este punto aleatorio y captura la fachada, plaza o vista más interesante.",
      category = "explore",
      missionType = "photo",
      iconKey = "landscape",
      xpReward = 115L,
    ),
    MissionTemplate(
      id = "quest_wires_beacon",
      title = "Enlace de Baliza",
      description = "Sincroniza los cables de señal en este sector para calibrar el radar de Veyra.",
      category = "explore",
      missionType = "wires",
      iconKey = "hub",
      xpReward = 150L,
    ),
    MissionTemplate(
      id = "quest_puzzle_compass",
      title = "Brújula Fragmentada",
      description = "Ordena las piezas del rompecabezas de orientación al llegar al punto asignado.",
      category = "explore",
      missionType = "puzzle",
      iconKey = "compass",
      xpReward = 160L,
    ),
    MissionTemplate(
      id = "quest_social_help",
      title = "Buena Acción del Día",
      description = "Ordena un espacio común o realiza un gesto amable en la zona y regístralo con foto.",
      category = "social",
      missionType = "photo",
      iconKey = "volunteer",
      xpReward = 165L,
    ),
    MissionTemplate(
      id = "quest_wires_community",
      title = "Puente Vecinal",
      description = "Conecta todas las líneas de colores del panel comunitario para enviar energía solidaria.",
      category = "social",
      missionType = "wires",
      iconKey = "cable",
      xpReward = 140L,
    ),
    MissionTemplate(
      id = "quest_puzzle_cafe",
      title = "Encuentro en la Esquina",
      description = "Completa el rompecabezas de encuentro social en el punto aleatorio marcado.",
      category = "social",
      missionType = "puzzle",
      iconKey = "coffee",
      xpReward = 135L,
    ),
    MissionTemplate(
      id = "quest_challenge_art",
      title = "Cazador de Detalles",
      description = "Camina hasta el marcador para fotografiar un letrero curioso o patrón geométrico.",
      category = "challenge",
      missionType = "photo",
      iconKey = "camera",
      xpReward = 150L,
    ),
    MissionTemplate(
      id = "quest_wires_cipher",
      title = "Cortafuegos de Campo",
      description = "Empareja correctamente los cables codificados sin equivocarte en el punto exacto.",
      category = "challenge",
      missionType = "wires",
      iconKey = "memory",
      xpReward = 180L,
    ),
    MissionTemplate(
      id = "quest_puzzle_vault",
      title = "Cerradura Táctica",
      description = "Resuelve el rompecabezas 3×3 en menos movimientos posibles cuando entres al radio.",
      category = "challenge",
      missionType = "puzzle",
      iconKey = "grid_puzzle",
      xpReward = 185L,
    ),
    MissionTemplate(
      id = "quest_fitness_walk",
      title = "Ruta Activa 10 Min",
      description = "Camina hasta este punto de control al aire libre y demuestra tu llegada con foto.",
      category = "fitness",
      missionType = "photo",
      iconKey = "directions_run",
      xpReward = 140L,
    ),
    MissionTemplate(
      id = "quest_wires_pulse",
      title = "Circuito de Ritmo",
      description = "Al llegar al punto de caminata, conecta los cables de impulso para registrar tu marca.",
      category = "fitness",
      missionType = "wires",
      iconKey = "rocket",
      xpReward = 160L,
    ),
    MissionTemplate(
      id = "quest_puzzle_trail",
      title = "Sendero Relámpago",
      description = "Completa el rompecabezas de ruta al alcanzar esta coordenada aleatoria.",
      category = "fitness",
      missionType = "puzzle",
      iconKey = "hiking",
      xpReward = 155L,
    ),
    MissionTemplate(
      id = "quest_creative_mural",
      title = "Galería Callejera",
      description = "Acércate al marcador y captura un mural, escultura o detalle arquitectónico cercano.",
      category = "creative",
      missionType = "photo",
      iconKey = "brush",
      xpReward = 155L,
    ),
    MissionTemplate(
      id = "quest_wires_neon",
      title = "Panel de Neón",
      description = "Conecta los cables luminosos de colores para encender la instalación artística del punto.",
      category = "creative",
      missionType = "wires",
      iconKey = "lightbulb",
      xpReward = 170L,
    ),
    MissionTemplate(
      id = "quest_puzzle_studio",
      title = "Lienzo Deslizable",
      description = "Reconstruye la composición deslizando las piezas del rompecabezas en el lugar.",
      category = "creative",
      missionType = "puzzle",
      iconKey = "palette",
      xpReward = 165L,
    ),
    MissionTemplate(
      id = "quest_eco_water",
      title = "Guardia del Agua",
      description = "Encuentra una fuente, canal o lluvia reflejada y toma una fotografía creativa.",
      category = "eco",
      missionType = "photo",
      iconKey = "water_drop",
      xpReward = 130L,
    ),
    MissionTemplate(
      id = "quest_eco_sun",
      title = "Luz del Mediodía",
      description = "Captura la luz solar entre hojas o edificios desde este punto aleatorio.",
      category = "eco",
      missionType = "photo",
      iconKey = "wb_sunny",
      xpReward = 125L,
    ),
    MissionTemplate(
      id = "quest_social_pets",
      title = "Huellas Amigas",
      description = "Explora la zona amigable con mascotas o parques cercanos y comparte una captura.",
      category = "social",
      missionType = "photo",
      iconKey = "pets",
      xpReward = 135L,
    ),
    MissionTemplate(
      id = "quest_creative_music",
      title = "Acorde Urbano",
      description = "Arma el rompecabezas rítmico tras llegar al punto sonoro de tu zona.",
      category = "creative",
      missionType = "puzzle",
      iconKey = "music_note",
      xpReward = 150L,
    ),
    MissionTemplate(
      id = "quest_explore_arch",
      title = "Geometría Viva",
      description = "Fotografía arcos, escaleras o líneas arquitectónicas interesantes cerca del pin.",
      category = "explore",
      missionType = "photo",
      iconKey = "architecture",
      xpReward = 145L,
    ),
    MissionTemplate(
      id = "quest_explore_map",
      title = "Cartógrafo Express",
      description = "Llega al marcador aleatorio de Google Maps y ordena el rompecabezas cartográfico.",
      category = "explore",
      missionType = "puzzle",
      iconKey = "map",
      xpReward = 170L,
    ),
    MissionTemplate(
      id = "quest_challenge_library",
      title = "Código de Archivo",
      description = "Conecta los 5 cables del archivo secreto cuando entres al perímetro de 120 m.",
      category = "challenge",
      missionType = "wires",
      iconKey = "local_library",
      xpReward = 175L,
    ),
    MissionTemplate(
      id = "quest_creative_museum",
      title = "Reliquia Oculta",
      description = "Completa el rompecabezas histórico al acercarte al punto cultural aleatorio.",
      category = "creative",
      missionType = "puzzle",
      iconKey = "museum",
      xpReward = 160L,
    ),
    MissionTemplate(
      id = "quest_fitness_sprint",
      title = "Circuito de Energía",
      description = "Conecta las terminales de color al completar tu tramo de caminata hacia el punto.",
      category = "fitness",
      missionType = "wires",
      iconKey = "extension",
      xpReward = 165L,
    ),
    MissionTemplate(
      id = "quest_social_florist",
      title = "Ramo Compartido",
      description = "Reconecta los cables del invernadero comunitario en esta coordenada aleatoria.",
      category = "social",
      missionType = "wires",
      iconKey = "florist",
      xpReward = 150L,
    ),
  )

  /**
   * Generates 28 distinct missions placed at random polar coordinates around the user's location
   * using the given seed so coordinates are genuinely randomized instead of fixed points.
   */
  fun generateRandomMissions(
    baseLat: Double,
    baseLon: Double,
    seed: Long = System.currentTimeMillis(),
    maxRadiusMeters: Float = 1800f,
  ): List<Mission> {
    val rng = Random(seed)
    val clampedMax = maxRadiusMeters.coerceIn(350f, 15000f)

    return MISSION_TEMPLATES.mapIndexed { index, tmpl ->
      // Keep the first ~5 missions closely reachable (24m..110m), next ~11 medium (130m..580m), rest wider
      val distMeters = when {
        index < 5 -> rng.nextDouble(24.0, 108.0)
        index < 16 -> rng.nextDouble(130.0, minOf(620.0, clampedMax * 0.65))
        else -> rng.nextDouble(
          minOf(420.0, clampedMax * 0.45),
          (clampedMax * 0.94).coerceAtLeast(500.0),
        )
      }
      val bearingDeg = rng.nextDouble(0.0, 360.0)
      val (lat, lon) = destinationPoint(baseLat, baseLon, distMeters, bearingDeg)
      Mission(
        id = tmpl.id,
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = tmpl.title,
        description = tmpl.description,
        category = tmpl.category,
        missionType = tmpl.missionType,
        iconKey = tmpl.iconKey,
        xpReward = tmpl.xpReward,
        latitude = lat,
        longitude = lon,
        isActive = true,
      )
    }
  }

  fun builtInLocalMissions(baseLat: Double, baseLon: Double, seed: Long = 202504L): List<Mission> {
    return generateRandomMissions(baseLat, baseLon, seed = seed)
  }
}
