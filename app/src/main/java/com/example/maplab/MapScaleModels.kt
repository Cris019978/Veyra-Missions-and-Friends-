package com.example.maplab

import androidx.compose.ui.graphics.Color

/**
 * Escalas disponibles para el mapa táctil offline-first:
 * - [SCALE_50]: vista cercana/cerrada de alto detalle.
 * - [SCALE_75]: vista intermedia equilibrada.
 * - [SCALE_100]: vista amplia/general del entorno y misiones difíciles.
 */
enum class MapViewScale(
  val level: Int,
  val label: String,
  val shortLabel: String,
  val viewRadiusMeters: Int,
  val zoomFactor: Float,
  val subtitle: String,
) {
  SCALE_50(
    level = 50,
    label = "Vista 50",
    shortLabel = "50 • Cercana",
    viewRadiusMeters = 180,
    zoomFactor = 2.45f,
    subtitle = "Escala cercana • Alto detalle alrededor del jugador",
  ),
  SCALE_75(
    level = 75,
    label = "Vista 75",
    shortLabel = "75 • Media",
    viewRadiusMeters = 450,
    zoomFactor = 1.25f,
    subtitle = "Escala intermedia • Contexto urbano y Radio de Vista cómodo",
  ),
  SCALE_100(
    level = 100,
    label = "Vista 100",
    shortLabel = "100 • Amplia",
    viewRadiusMeters = 1200,
    zoomFactor = 0.54f,
    subtitle = "Panorama general • Entorno amplio y misiones de mayor dificultad",
  );

  fun nextScale(): MapViewScale = when (this) {
    SCALE_50 -> SCALE_75
    SCALE_75 -> SCALE_100
    SCALE_100 -> SCALE_50
  }
}

/**
 * Umbrales en píxeles (ajustados por densidad desde la UI si se desea, con defaults
 * calibrados para pantallas móviles estándar) para gobernar las transiciones gestuales.
 */
object MapGestureThresholds {
  /** Micro-desplazamiento mínimo para ocultar/minimizar el botón de alternar al usar gesto 50<->75. */
  const val DRAG_MINIMIZE_BUTTON_PX = 16f

  /** Desplazamiento horizontal mínimo al soltar para confirmar cambio entre Vista 50 y Vista 75. */
  const val HORIZONTAL_SWITCH_50_75_PX = 64f

  /** Recorrido completo usado para interpolar suavemente entre Vista 50 (0f) y Vista 75 (1f). */
  const val FULL_50_75_SLIDE_TRAVEL_PX = 160f

  /** Umbral claro al jalar/desplazar hacia abajo para activar la Vista 100. */
  const val PULL_DOWN_FOR_100_PX = 135f

  /** Umbral hacia arriba para salir cómodamente de la Vista 100 hacia la Vista 75. */
  const val PULL_UP_EXIT_100_PX = -90f
}

/**
 * Controla el protagonismo y visibilidad del botón de alternar vistas.
 * - [VISIBLE_PRIMARY]: visible y listo para pulsar (estado inicial o al activar Vista 100).
 * - [MINIMIZED_DURING_GESTURE]: oculto/minimizado mientras se usa o prioriza el gesto directo 50 <-> 75.
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

/**
 * Coordenada local relativa en metros respecto al origen de prueba.
 * TODO [GPS Real]: Proyectar latitud/longitud del dispositivo a metros relativos
 * usando `GeoUtils` alrededor de la ubicación inicial de la sesión.
 */
data class LocalMapCoordinate(
  val xMeters: Float,
  val yMeters: Float,
)

data class MapLabMission(
  val id: String,
  val title: String,
  val difficulty: MapMissionDifficulty,
  val position: LocalMapCoordinate,
  val xpReward: Int,
  val isActive: Boolean = false,
)

data class MapScaleUiState(
  val activeScale: MapViewScale = MapViewScale.SCALE_75,
  val playerPosition: LocalMapCoordinate = LocalMapCoordinate(0f, 0f),
  val playerHeadingDeg: Float = 28f,
  val missions: List<MapLabMission> = MapLabDefaults.defaultMissions(),
  val selectedMissionId: String? = "m_active_1",
  val isGestureDragging: Boolean = false,
  val isSlidingBetween50And75: Boolean = false,
  /** 0f = Vista 50, 1f = Vista 75 (interpolación continua durante el gesto táctil). */
  val blend50To75: Float = 1f,
  /** 0f..1f progreso del gesto hacia abajo para activar Vista 100. */
  val pullDown100Progress: Float = 0f,
  val buttonVisibilityMode: ToggleButtonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
  val lastEventLog: String = "Desliza horizontal entre 50 y 75, o jala abajo para Vista 100",
  val debugPanelExpanded: Boolean = true,
) {
  val isToggleButtonVisible: Boolean
    get() = buttonVisibilityMode == ToggleButtonVisibilityMode.VISIBLE_PRIMARY

  val effectiveZoomFactor: Float
    get() = when {
      activeScale == MapViewScale.SCALE_100 -> MapViewScale.SCALE_100.zoomFactor
      isSlidingBetween50And75 -> {
        val z50 = MapViewScale.SCALE_50.zoomFactor
        val z75 = MapViewScale.SCALE_75.zoomFactor
        z50 + (z75 - z50) * blend50To75.coerceIn(0f, 1f)
      }
      else -> activeScale.zoomFactor
    }

  val effectiveViewRadiusMeters: Int
    get() = when {
      activeScale == MapViewScale.SCALE_100 -> MapViewScale.SCALE_100.viewRadiusMeters
      isSlidingBetween50And75 -> {
        val r50 = MapViewScale.SCALE_50.viewRadiusMeters.toFloat()
        val r75 = MapViewScale.SCALE_75.viewRadiusMeters.toFloat()
        (r50 + (r75 - r50) * blend50To75.coerceIn(0f, 1f)).toInt()
      }
      else -> activeScale.viewRadiusMeters
    }
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
      title = "Nodo Enlazado (Activa)",
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
  )
}
