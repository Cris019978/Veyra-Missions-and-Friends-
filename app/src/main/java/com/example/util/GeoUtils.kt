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

  fun openInExternalMap(
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
        Uri.parse("https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=17/$latitude/$longitude"),
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
    val variantKey: String,
    val timeLimitSeconds: Int = 0,
    val defaultSafeZoneName: String = "Parque o plaza pública cercana",
  )

  private val MISSION_TEMPLATES = listOf(
    // --- Puzzle Category Missions (solved inline inside the mission menu; several have a 60s countdown!) ---
    MissionTemplate(
      id = "quest_puzzle_clock_wires",
      title = "Desactivación Relámpago",
      description = "Llega al punto demostrado y conecta los cables de colores dentro de este menú antes de 1 minuto.",
      category = "puzzle",
      missionType = "wires",
      iconKey = "bolt",
      xpReward = 190L,
      variantKey = "timed_wires_circuit",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Plaza cívica abierta",
    ),
    MissionTemplate(
      id = "quest_puzzle_clock_matrix",
      title = "Matriz Cronometrada 3×3",
      description = "En el punto geográfico exacto, arma el rompecabezas 3×3 dentro del menú en menos de 60 segundos.",
      category = "puzzle",
      missionType = "puzzle",
      iconKey = "grid_puzzle",
      xpReward = 200L,
      variantKey = "timed_sliding_matrix",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Andador peatonal público",
    ),
    MissionTemplate(
      id = "quest_puzzle_clock_sequence",
      title = "Código de Pulso (1 min)",
      description = "Memoriza y repite la secuencia luminosa de 4 nodos dentro del menú antes que el reloj llegue a cero.",
      category = "puzzle",
      missionType = "memory",
      iconKey = "memory",
      xpReward = 195L,
      variantKey = "timed_memory_cipher",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Jardín o glorieta pública",
    ),
    MissionTemplate(
      id = "quest_puzzle_clock_beacon",
      title = "Sintonía Express 60s",
      description = "Calibra la antena del sector emparejando la secuencia de pulso en un minuto exacto.",
      category = "puzzle",
      missionType = "memory",
      iconKey = "hub",
      xpReward = 185L,
      variantKey = "timed_signal_calibration",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Mirador o parque público",
    ),
    MissionTemplate(
      id = "quest_puzzle_botanico",
      title = "Mosaico del Jardín",
      description = "Acércate al punto demostrado y resuelve aquí mismo el rompecabezas deslizable botánico.",
      category = "puzzle",
      missionType = "puzzle",
      iconKey = "eco",
      xpReward = 155L,
      variantKey = "zen_botanical_mosaic",
      timeLimitSeconds = 0,
      defaultSafeZoneName = "Zona verde o jardín abierto",
    ),
    MissionTemplate(
      id = "quest_puzzle_compass",
      title = "Brújula Fragmentada",
      description = "Ordena las piezas de orientación dentro del menú al entrar al radio seguro del marcador.",
      category = "puzzle",
      missionType = "puzzle",
      iconKey = "compass",
      xpReward = 160L,
      variantKey = "orientation_puzzle",
      timeLimitSeconds = 0,
      defaultSafeZoneName = "Paseo peatonal seguro",
    ),
    MissionTemplate(
      id = "quest_puzzle_cipher",
      title = "Cortafuegos de Campo",
      description = "Empareja las terminales de colores dentro del menú de la misión en esta coordenada.",
      category = "puzzle",
      missionType = "wires",
      iconKey = "cable",
      xpReward = 175L,
      variantKey = "field_wire_patch",
      timeLimitSeconds = 0,
      defaultSafeZoneName = "Explanada cultural",
    ),
    MissionTemplate(
      id = "quest_puzzle_sequence_vault",
      title = "Bóveda de Patrones",
      description = "Descifra la combinación secuencial de colores y símbolos al llegar al punto público.",
      category = "puzzle",
      missionType = "memory",
      iconKey = "extension",
      xpReward = 170L,
      variantKey = "pattern_vault_memory",
      timeLimitSeconds = 0,
      defaultSafeZoneName = "Biblioteca o centro comunitario",
    ),

    // --- Eco, Explore, Social, Challenge, Fitness & Creative Variants ---
    MissionTemplate(
      id = "quest_eco_tree",
      title = "Guardián Verde",
      description = "Encuentra un árbol o área verde pública en este sector y confírmalo con una foto.",
      category = "eco",
      missionType = "photo",
      iconKey = "park",
      xpReward = 120L,
      variantKey = "flora_capture",
      defaultSafeZoneName = "Parque arbolado público",
    ),
    MissionTemplate(
      id = "quest_eco_water",
      title = "Reflejo del Agua",
      description = "Captura una fuente, charco o reflejo natural desde un espacio peatonal abierto.",
      category = "eco",
      missionType = "photo",
      iconKey = "water_drop",
      xpReward = 130L,
      variantKey = "water_reflection_hunt",
      defaultSafeZoneName = "Fuente o corredor verde",
    ),
    MissionTemplate(
      id = "quest_eco_sun",
      title = "Luz entre Copas",
      description = "Fotografía sombras o rayos de luz atravesando árboles o pérgolas públicas.",
      category = "eco",
      missionType = "photo",
      iconKey = "wb_sunny",
      xpReward = 125L,
      variantKey = "canopy_light_study",
      defaultSafeZoneName = "Alameda peatonal",
    ),
    MissionTemplate(
      id = "quest_explore_sky",
      title = "Horizonte Urbano",
      description = "Acércate a este punto público de OpenStreetMap y fotografía la fachada o plaza más vistosa.",
      category = "explore",
      missionType = "photo",
      iconKey = "landscape",
      xpReward = 125L,
      variantKey = "skyline_framing",
      defaultSafeZoneName = "Plaza pública central",
    ),
    MissionTemplate(
      id = "quest_explore_arch",
      title = "Geometría Viva",
      description = "Busca arcos, escalinatas o líneas simétricas en la vía pública y tómales foto.",
      category = "explore",
      missionType = "photo",
      iconKey = "architecture",
      xpReward = 145L,
      variantKey = "symmetry_architecture",
      defaultSafeZoneName = "Corredor arquitectónico público",
    ),
    MissionTemplate(
      id = "quest_explore_map",
      title = "Cartógrafo Express",
      description = "Alcanza el punto aleatorio verificado y resuelve el circuito cartográfico cronometrado.",
      category = "explore",
      missionType = "puzzle",
      iconKey = "map",
      xpReward = 180L,
      variantKey = "timed_cartography",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Intersección peatonal segura",
    ),
    MissionTemplate(
      id = "quest_social_help",
      title = "Buena Acción del Día",
      description = "Realiza un gesto amable u ordena un banco de parque público y comparte evidencia.",
      category = "social",
      missionType = "photo",
      iconKey = "volunteer",
      xpReward = 165L,
      variantKey = "kindness_quest",
      defaultSafeZoneName = "Zona de convivencia vecinal",
    ),
    MissionTemplate(
      id = "quest_social_pets",
      title = "Huellas Amigas",
      description = "Visita un parque amigable con mascotas y captura una escena alegre al aire libre.",
      category = "social",
      missionType = "photo",
      iconKey = "pets",
      xpReward = 135L,
      variantKey = "pet_park_spotlight",
      defaultSafeZoneName = "Parque canino o jardín",
    ),
    MissionTemplate(
      id = "quest_social_florist",
      title = "Ramo Comunitario",
      description = "Conecta la red de riego de este punto público dentro del menú sin errores.",
      category = "social",
      missionType = "wires",
      iconKey = "florist",
      xpReward = 150L,
      variantKey = "community_garden_wires",
      defaultSafeZoneName = "Huerto o camellón público",
    ),
    MissionTemplate(
      id = "quest_social_cafe",
      title = "Encuentro en la Esquina",
      description = "Llega a una cafetería o plaza segura y desbloquea el patrón de encuentro.",
      category = "social",
      missionType = "memory",
      iconKey = "coffee",
      xpReward = 145L,
      variantKey = "cafe_corner_sequence",
      defaultSafeZoneName = "Café o terraza pública",
    ),
    MissionTemplate(
      id = "quest_challenge_art",
      title = "Cazador de Detalles",
      description = "Encuentra una textura curiosa, mosaico o tipografía urbana y tómale una foto nítida.",
      category = "challenge",
      missionType = "photo",
      iconKey = "camera",
      xpReward = 150L,
      variantKey = "micro_texture_hunt",
      defaultSafeZoneName = "Pasaje cultural público",
    ),
    MissionTemplate(
      id = "quest_challenge_library",
      title = "Archivo Clasificado",
      description = "Conecta los 5 cables del archivo secreto cuando entres al perímetro seguro de 120 m.",
      category = "challenge",
      missionType = "wires",
      iconKey = "local_library",
      xpReward = 175L,
      variantKey = "archive_terminal_link",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Biblioteca pública cercana",
    ),
    MissionTemplate(
      id = "quest_fitness_walk",
      title = "Ruta Activa 10 Min",
      description = "Camina hasta este punto de control peatonal y confirma tu llegada con una selfie o vista.",
      category = "fitness",
      missionType = "photo",
      iconKey = "directions_run",
      xpReward = 140L,
      variantKey = "cardio_checkpoint",
      defaultSafeZoneName = "Sendero o pista pública",
    ),
    MissionTemplate(
      id = "quest_fitness_trail",
      title = "Sendero Relámpago",
      description = "Alcanza el hito de caminata y resuelve el rompecabezas de ruta antes de descansar.",
      category = "fitness",
      missionType = "puzzle",
      iconKey = "hiking",
      xpReward = 160L,
      variantKey = "trail_sprint_puzzle",
      defaultSafeZoneName = "Circuito de caminata",
    ),
    MissionTemplate(
      id = "quest_fitness_pulse",
      title = "Circuito de Ritmo",
      description = "Al llegar al punto de ejercicio, conecta las terminales de impulso energético.",
      category = "fitness",
      missionType = "wires",
      iconKey = "rocket",
      xpReward = 160L,
      variantKey = "rhythm_boost_wires",
      defaultSafeZoneName = "Parque deportivo abierto",
    ),
    MissionTemplate(
      id = "quest_creative_mural",
      title = "Galería Callejera",
      description = "Acércate al espacio cultural y captura un mural, monumento o escultura pública.",
      category = "creative",
      missionType = "photo",
      iconKey = "brush",
      xpReward = 155L,
      variantKey = "street_mural_capture",
      defaultSafeZoneName = "Corredor artístico",
    ),
    MissionTemplate(
      id = "quest_creative_neon",
      title = "Instalación Luminosa",
      description = "Reconecta los cables de neón dentro del menú de la misión en esta ubicación.",
      category = "creative",
      missionType = "wires",
      iconKey = "lightbulb",
      xpReward = 170L,
      variantKey = "neon_art_wires",
      defaultSafeZoneName = "Plaza iluminada",
    ),
    MissionTemplate(
      id = "quest_creative_studio",
      title = "Lienzo Cromático",
      description = "Reconstruye el mosaico artístico deslizando las piezas en este punto seguro.",
      category = "creative",
      missionType = "puzzle",
      iconKey = "palette",
      xpReward = 165L,
      variantKey = "studio_color_mosaic",
      defaultSafeZoneName = "Centro artístico abierto",
    ),
    MissionTemplate(
      id = "quest_creative_music",
      title = "Acorde Urbano",
      description = "Repite el ritmo melódico de 4 tonos en la memoria interactiva al llegar al punto.",
      category = "creative",
      missionType = "memory",
      iconKey = "music_note",
      xpReward = 165L,
      variantKey = "melody_memory_lock",
      timeLimitSeconds = 60,
      defaultSafeZoneName = "Kiosco o foro al aire libre",
    ),
    MissionTemplate(
      id = "quest_creative_museum",
      title = "Reliquia Oculta",
      description = "Descifra el rompecabezas histórico frente al museo o sitio cultural del mapa.",
      category = "creative",
      missionType = "puzzle",
      iconKey = "museum",
      xpReward = 175L,
      variantKey = "heritage_relic_board",
      defaultSafeZoneName = "Museo o plaza histórica",
    ),
  )

  fun distinctMissionVariantsCount(): Int = MISSION_TEMPLATES.map { it.variantKey }.toSet().size

  private val PRIVATE_ZONE_GUARD_RADIANS = setOf("priv", "home", "restricted")

  /**
   * Checks whether a generated offset keeps clear of overly dense overlaps or flagged place labels.
   */
  fun isSafeGeneratedSpot(
    label: String,
    existingCoords: List<Pair<Double, Double>>,
    lat: Double,
    lon: Double,
    minSeparationMeters: Float = 18f,
  ): Boolean {
    val lower = label.lowercase()
    if (com.example.data.remote.OsmSafePlaceFinder.BLOCKED_KEYWORDS.any { lower.contains(it) }) {
      return false
    }
    if (PRIVATE_ZONE_GUARD_RADIANS.any { lower == it }) return false
    return existingCoords.none { (eLat, eLon) ->
      distanceMeters(eLat, eLon, lat, lon) < minSeparationMeters
    }
  }

  /**
   * Generates 28 distinct missions spanning >= 20 gameplay variants across verified OpenStreetMap
   * safe public places (or pedestrian-safe randomized rings when offline), avoiding
   * private property, highways, hospitals, police stations, and dangerous areas.
   */
  fun generateRandomMissions(
    baseLat: Double,
    baseLon: Double,
    seed: Long = System.currentTimeMillis(),
    maxRadiusMeters: Float = 1800f,
    safeAnchors: List<com.example.data.remote.SafePublicSpot> = emptyList(),
  ): List<Mission> {
    val rng = Random(seed)
    val clampedMax = maxRadiusMeters.coerceIn(350f, 15000f)
    val verifiedAnchors = safeAnchors.filter { spot ->
      !com.example.data.remote.OsmSafePlaceFinder.BLOCKED_KEYWORDS.any {
        spot.name.lowercase().contains(it)
      }
    }
    val usedCoords = mutableListOf<Pair<Double, Double>>()

    return MISSION_TEMPLATES.mapIndexed { index, tmpl ->
      val anchor = if (verifiedAnchors.isNotEmpty() && index >= 2) {
        verifiedAnchors[(index + rng.nextInt(verifiedAnchors.size)) % verifiedAnchors.size]
      } else {
        null
      }

      var candidateLat: Double
      var candidateLon: Double
      var attempts = 0
      do {
        if (anchor != null) {
          // Small pedestrian jitter around a verified OpenStreetMap safe public place
          val jitterMeters = rng.nextDouble(5.0, 26.0)
          val jitterBearing = rng.nextDouble(0.0, 360.0)
          val pt = destinationPoint(anchor.latitude, anchor.longitude, jitterMeters, jitterBearing)
          candidateLat = pt.first
          candidateLon = pt.second
        } else {
          // Safe pedestrian ring around player (first ~5 close enough to test within 120m!)
          val distMeters = when {
            index < 5 -> rng.nextDouble(22.0, 105.0)
            index < 16 -> rng.nextDouble(135.0, minOf(620.0, clampedMax * 0.65))
            else -> rng.nextDouble(
              minOf(420.0, clampedMax * 0.45),
              (clampedMax * 0.92).coerceAtLeast(500.0),
            )
          }
          val bearingDeg = rng.nextDouble(0.0, 360.0)
          val pt = destinationPoint(baseLat, baseLon, distMeters, bearingDeg)
          candidateLat = pt.first
          candidateLon = pt.second
        }
        attempts++
      } while (
        attempts < 6 &&
        !isSafeGeneratedSpot(
          label = anchor?.name ?: tmpl.defaultSafeZoneName,
          existingCoords = usedCoords,
          lat = candidateLat,
          lon = candidateLon,
          minSeparationMeters = if (anchor != null) 10f else 18f,
        )
      )

      usedCoords.add(candidateLat to candidateLon)
      val safePlaceLabel = anchor?.name ?: tmpl.defaultSafeZoneName

      Mission(
        id = tmpl.id,
        creatorId = "system_veyra",
        creatorName = "Veyra",
        title = tmpl.title,
        description = tmpl.description,
        category = tmpl.category,
        missionType = tmpl.missionType,
        iconKey = tmpl.iconKey,
        timeLimitSeconds = tmpl.timeLimitSeconds,
        placeLabel = safePlaceLabel,
        isSafePublicVerified = true,
        xpReward = tmpl.xpReward,
        latitude = candidateLat,
        longitude = candidateLon,
        isActive = true,
      )
    }
  }

  fun builtInLocalMissions(baseLat: Double, baseLon: Double, seed: Long = 202504L): List<Mission> {
    return generateRandomMissions(baseLat, baseLon, seed = seed)
  }
}
