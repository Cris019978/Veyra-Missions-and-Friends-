package com.example.maplab

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Escalas disponibles para el radar/mapa único offline-first:
 * - [SCALE_50]: apertura cerrada / detalle inmediato alrededor del jugador.
 * - [SCALE_75]: apertura intermedia equilibrada con contexto urbano.
 * - [SCALE_100]: apertura amplia / exploración general del entorno y misiones difíciles.
 */
enum class MapViewScale(
  val level: Int,
  val label: String,
  val shortLabel: String,
  val viewRadiusMeters: Int,
  val zoomFactor: Float,
  val revealProgress: Float,
  val subtitle: String,
) {
  SCALE_50(
    level = 50,
    label = "Vista 50",
    shortLabel = "50 • Cercana",
    viewRadiusMeters = 180,
    zoomFactor = 2.45f,
    revealProgress = 0.0f,
    subtitle = "Escala cercana • Alto detalle alrededor del jugador",
  ),
  SCALE_75(
    level = 75,
    label = "Vista 75",
    shortLabel = "75 • Media",
    viewRadiusMeters = 450,
    zoomFactor = 1.25f,
    revealProgress = 0.50f,
    subtitle = "Escala intermedia • Contexto urbano y Radio de Vista cómodo",
  ),
  SCALE_100(
    level = 100,
    label = "Vista 100",
    shortLabel = "100 • Amplia",
    viewRadiusMeters = 1200,
    zoomFactor = 0.54f,
    revealProgress = 1.0f,
    subtitle = "Panorama general • Entorno amplio y misiones de mayor dificultad",
  );

  fun nextScale(): MapViewScale = when (this) {
    SCALE_50 -> SCALE_75
    SCALE_75 -> SCALE_100
    SCALE_100 -> SCALE_50
  }
}

/**
 * Umbrales calibrados para gestos táctiles sobre el componente único de radar/mapa.
 */
object MapGestureThresholds {
  /** Micro-desplazamiento mínimo para ocultar/minimizar el botón de alternar al deslizar entre 50 y 75. */
  const val DRAG_MINIMIZE_BUTTON_PX = 16f

  /** Desplazamiento horizontal mínimo al soltar para confirmar el cambio entre 50 y 75. */
  const val HORIZONTAL_SWITCH_50_75_PX = 64f

  /** Recorrido completo usado para interpolar suavemente entre Vista 50 (0f) y Vista 75 (1f). */
  const val FULL_50_75_SLIDE_TRAVEL_PX = 160f

  /** Umbral claro al jalar/desplazar hacia abajo para activar la Vista 100. */
  const val PULL_DOWN_FOR_100_PX = 135f

  /** Umbral hacia arriba para volver cómodamente de Vista 100 a Vista 75. */
  const val PULL_UP_EXIT_100_PX = -90f
}

/**
 * Controla el protagonismo y visibilidad del botón de alternar vistas.
 * - [VISIBLE_PRIMARY]: visible y protagonista (reposo inicial o al activar Vista 100).
 * - [MINIMIZED_DURING_GESTURE]: oculto/minimizado mientras prioriza el gesto directo 50 <-> 75.
 */
enum class ToggleButtonVisibilityMode(val label: String) {
  VISIBLE_PRIMARY("Visible (Protagonista)"),
  MINIMIZED_DURING_GESTURE("Minimizado por gesto 50↔75"),
}

enum class MapMissionDifficulty(
  val label: String,
  val markerColor: Color,
) {
  NORMAL("Normal", Color(0xFF34D399)),
  TACTICAL("Táctica", Color(0xFF38BDF8)),
  HIGH_DIFFICULTY("Alta Dificultad", Color(0xFFF43F5E)),
}

enum class MapLandmarkType(val label: String, val accentColor: Color) {
  PARK("Parque", Color(0xFF22C55E)),
  PLAZA("Plaza", Color(0xFF38BDF8)),
  DISTRICT("Sector", Color(0xFFA78BFA)),
  RIDGE("Mirador", Color(0xFFFBBF24)),
}

/**
 * Coordenada local relativa en metros respecto al origen de prueba.
 * TODO [GPS Real]: Proyectar latitud/longitud del dispositivo a metros relativos
 * usando `GeoUtils` alrededor de la ubicación inicial de la sesión.
 */
// OPT: @Immutable evita invalidaciones de Compose al comparar coordenadas de jugador/misiones.
@Immutable
data class LocalMapCoordinate(
  val xMeters: Float,
  val yMeters: Float,
)

// OPT: @Immutable estabiliza los marcadores del mapa durante gestos de arrastre a 60fps.
@Immutable
data class MapLabMission(
  val id: String,
  val title: String,
  val difficulty: MapMissionDifficulty,
  val position: LocalMapCoordinate,
  val xpReward: Int,
  val isActive: Boolean = false,
)

// OPT: @Immutable estabiliza las zonas de referencia (parques, plazas, distritos).
@Immutable
data class MapLandmark(
  val id: String,
  val name: String,
  val type: MapLandmarkType,
  val position: LocalMapCoordinate,
  val radiusMeters: Float,
)

// OPT: @Immutable hace estable el estado completo de la pantalla de mapa para saltar recomposiciones en capas sin cambio.
@Immutable
data class MapScaleUiState(
  val activeScale: MapViewScale = MapViewScale.SCALE_75,
  val playerPosition: LocalMapCoordinate = LocalMapCoordinate(0f, 0f),
  val playerHeadingDeg: Float = 28f,
  val missions: List<MapLabMission> = MapLabDefaults.defaultMissions(),
  val landmarks: List<MapLandmark> = MapLabDefaults.defaultLandmarks(),
  val selectedMissionId: String? = "m_active_1",
  val isGestureDragging: Boolean = false,
  val isSlidingBetween50And75: Boolean = false,
  /** 0f = Vista 50, 1f = Vista 75 (interpolación continua durante el gesto lateral). */
  val blend50To75: Float = 1f,
  /** 0f..1f progreso del gesto hacia abajo para abrir a Vista 100. */
  val pullDown100Progress: Float = 0f,
  val buttonVisibilityMode: ToggleButtonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
  val lastEventLog: String = "Desliza entre 50 y 75, o jala hacia abajo para abrir Vista 100",
  /** Discreto por defecto para priorizar la interfaz principal y el radar/mapa. */
  val debugPanelExpanded: Boolean = false,
) {
  val isToggleButtonVisible: Boolean
    get() = buttonVisibilityMode == ToggleButtonVisibilityMode.VISIBLE_PRIMARY

  /**
   * Progreso unificado de revelado del mapa (0f = Vista 50 cerrada, 0.5f = Vista 75, 1f = Vista 100 amplia).
   * Responde en tiempo real tanto al deslizar entre 50 y 75 como al jalar hacia abajo rumbo a 100.
   */
  val mapRevealProgress: Float
    get() = when {
      activeScale == MapViewScale.SCALE_100 -> 1.0f
      isSlidingBetween50And75 -> (0.50f * blend50To75).coerceIn(0f, 0.50f)
      pullDown100Progress > 0.02f -> {
        val baseReveal = activeScale.revealProgress
        (baseReveal + (1.0f - baseReveal) * pullDown100Progress).coerceIn(0f, 1f)
      }
      else -> activeScale.revealProgress
    }

  val effectiveZoomFactor: Float
    get() {
      val r = mapRevealProgress
      return if (r <= 0.50f) {
        val t = (r / 0.50f).coerceIn(0f, 1f)
        lerpFloat(MapViewScale.SCALE_50.zoomFactor, MapViewScale.SCALE_75.zoomFactor, t)
      } else {
        val t = ((r - 0.50f) / 0.50f).coerceIn(0f, 1f)
        lerpFloat(MapViewScale.SCALE_75.zoomFactor, MapViewScale.SCALE_100.zoomFactor, t)
      }
    }

  val effectiveViewRadiusMeters: Int
    get() {
      val r = mapRevealProgress
      return if (r <= 0.50f) {
        val t = (r / 0.50f).coerceIn(0f, 1f)
        lerpFloat(MapViewScale.SCALE_50.viewRadiusMeters.toFloat(), MapViewScale.SCALE_75.viewRadiusMeters.toFloat(), t).toInt()
      } else {
        val t = ((r - 0.50f) / 0.50f).coerceIn(0f, 1f)
        lerpFloat(MapViewScale.SCALE_75.viewRadiusMeters.toFloat(), MapViewScale.SCALE_100.viewRadiusMeters.toFloat(), t).toInt()
      }
    }

  private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction
}

object MapLabDefaults {
  fun defaultMissions(): List<MapLabMission> = listOf(
    MapLabMission(
      id = "m_near_1",
      title = "Baliza de Esquina",
      difficulty = MapMissionDifficulty.NORMAL,
      position = LocalMapCoordinate(xMeters = 68f, yMeters = -52f),
      xpReward = 90,
    ),
    MapLabMission(
      id = "m_near_2",
      title = "Mural Oculto",
      difficulty = MapMissionDifficulty.TACTICAL,
      position = LocalMapCoordinate(xMeters = -95f, yMeters = 74f),
      xpReward = 140,
    ),
    MapLabMission(
      id = "m_active_1",
      title = "Nodo Enlazado",
      difficulty = MapMissionDifficulty.TACTICAL,
      position = LocalMapCoordinate(xMeters = 190f, yMeters = 155f),
      xpReward = 240,
      isActive = true,
    ),
    MapLabMission(
      id = "m_mid_1",
      title = "Circuito del Parque",
      difficulty = MapMissionDifficulty.NORMAL,
      position = LocalMapCoordinate(xMeters = -260f, yMeters = -180f),
      xpReward = 175,
    ),
    MapLabMission(
      id = "m_mid_2",
      title = "Estación Norte",
      difficulty = MapMissionDifficulty.TACTICAL,
      position = LocalMapCoordinate(xMeters = 330f, yMeters = -210f),
      xpReward = 260,
    ),
    MapLabMission(
      id = "m_hard_1",
      title = "Zona Roja: Contrarreloj",
      difficulty = MapMissionDifficulty.HIGH_DIFFICULTY,
      position = LocalMapCoordinate(xMeters = 580f, yMeters = -430f),
      xpReward = 520,
    ),
    MapLabMission(
      id = "m_hard_2",
      title = "Torre Extrema Nocturna",
      difficulty = MapMissionDifficulty.HIGH_DIFFICULTY,
      position = LocalMapCoordinate(xMeters = -690f, yMeters = 520f),
      xpReward = 680,
      isActive = true,
    ),
    MapLabMission(
      id = "m_hard_3",
      title = "Brecha Perimetral",
      difficulty = MapMissionDifficulty.HIGH_DIFFICULTY,
      position = LocalMapCoordinate(xMeters = -180f, yMeters = -760f),
      xpReward = 740,
    ),
  )

  fun defaultLandmarks(): List<MapLandmark> = listOf(
    MapLandmark(
      id = "lm_plaza_local",
      name = "Plaza Central",
      type = MapLandmarkType.PLAZA,
      position = LocalMapCoordinate(xMeters = 52f, yMeters = 48f),
      radiusMeters = 42f,
    ),
    MapLandmark(
      id = "lm_park_west",
      name = "Parque Verde",
      type = MapLandmarkType.PARK,
      position = LocalMapCoordinate(xMeters = -245f, yMeters = -165f),
      radiusMeters = 115f,
    ),
    MapLandmark(
      id = "lm_district_east",
      name = "Distrito Tech",
      type = MapLandmarkType.DISTRICT,
      position = LocalMapCoordinate(xMeters = 360f, yMeters = 120f),
      radiusMeters = 150f,
    ),
    MapLandmark(
      id = "lm_park_south",
      name = "Bosque Sur",
      type = MapLandmarkType.PARK,
      position = LocalMapCoordinate(xMeters = 110f, yMeters = 580f),
      radiusMeters = 190f,
    ),
    MapLandmark(
      id = "lm_ridge_north",
      name = "Mirador Alto",
      type = MapLandmarkType.RIDGE,
      position = LocalMapCoordinate(xMeters = -480f, yMeters = -520f),
      radiusMeters = 165f,
    ),
  )
}
