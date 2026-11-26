package com.example.veyra2

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.maplab.MapViewScale

/**
 * Modos unificados del módulo táctico Veyra 2 sin crear pantallas desconectadas:
 * - [CLOSED_MINIMAP]: mapa cerrado/minimizado -> la lista textual y datos siguen en memoria,
 *   pero NO se dibujan marcadores de misiones sobre el Canvas del mapa.
 * - [OPEN_50], [OPEN_75], [OPEN_100]: mapa abierto con revelado progresivo y marcadores
 *   acordes a la escala activa.
 * - [LANDSCAPE_AIMING]: modo horizontal ("Preparing an Air Strike") con retícula central,
 *   joystick, pan táctil y pinch-to-zoom.
 */
enum class Veyra2MapMode(
  val label: String,
  val isMapOpen: Boolean,
  val scaleLevel: MapViewScale?,
) {
  CLOSED_MINIMAP(
    label = "Cerrado (0 pines)",
    isMapOpen = false,
    scaleLevel = null,
  ),
  OPEN_50(
    label = "Vista 50",
    isMapOpen = true,
    scaleLevel = MapViewScale.SCALE_50,
  ),
  OPEN_75(
    label = "Vista 75",
    isMapOpen = true,
    scaleLevel = MapViewScale.SCALE_75,
  ),
  OPEN_100(
    label = "Vista 100",
    isMapOpen = true,
    scaleLevel = MapViewScale.SCALE_100,
  ),
  LANDSCAPE_AIMING(
    label = "Horizontal • Mira",
    isMapOpen = true,
    scaleLevel = MapViewScale.SCALE_100,
  );

  val isLandscapeAiming: Boolean
    get() = this == LANDSCAPE_AIMING
}

/**
 * Estrategia de tasa de refresco adaptativa para balancear respuesta 1:1 y batería:
 * - [IDLE_LOW_30HZ]: reposo sin gestos ni desplazamiento.
 * - [ACTIVE_MOVEMENT_60HZ]: caminata / actualización moderada del mapa.
 * - [GESTURE_BOOST_120HZ]: arrastre, joystick, pinch-to-zoom o transición de orientación horizontal.
 */
enum class AdaptiveRefreshTier(
  val targetHz: Float,
  val shortLabel: String,
  val pulseDurationMs: Int,
) {
  IDLE_LOW_30HZ(
    targetHz = 30f,
    shortLabel = "30 Hz • Reposo",
    pulseDurationMs = 2600,
  ),
  ACTIVE_MOVEMENT_60HZ(
    targetHz = 60f,
    shortLabel = "60 Hz • Movimiento",
    pulseDurationMs = 1500,
  ),
  GESTURE_BOOST_120HZ(
    targetHz = 120f,
    shortLabel = "120 Hz • Gesto/Mira",
    pulseDurationMs = 0, // Durante gestos activos se congela el pulso decorativo para cero competencia
  ),
}

enum class Veyra2MissionDifficulty(
  val label: String,
  val markerColor: Color,
  val minDrawReveal: Float,
) {
  NORMAL("Normal", Color(0xFF34D399), 0f),
  TACTICAL("Táctica", Color(0xFF38BDF8), 0.25f),
  HIGH_DIFFICULTY("Alta Dificultad", Color(0xFFF97316), 0.65f),
}

/**
 * Coordenada relativa local en metros.
 * TODO [GPS Real]: Conectar proyección desde `LocationTracker` (lat/lon -> dx/dy metros).
 */
@Immutable
data class Veyra2Coordinate(
  val xMeters: Float,
  val yMeters: Float,
)

/**
 * Entidad FÍSICA y efímera de una misión sobre el mapa.
 * Al alejarse >= [recycleThresholdMeters] (2,000 m por defecto), esta entidad física se elimina
 * del mapa (haya sido tocada/reclamada o no), sin afectar jamás a [PlayerRewardsLedger].
 */
@Immutable
data class PhysicalMapMission(
  val id: String,
  val title: String,
  val summary: String,
  val difficulty: Veyra2MissionDifficulty,
  val position: Veyra2Coordinate,
  val xpReward: Long,
  val bonusLevelsReward: Int = 0,
  val titleReward: String? = null,
  val hasBeenTouched: Boolean = false,
  val hasBeenInspected: Boolean = false,
  val isPinnedTarget: Boolean = false,
  val isClaimed: Boolean = false,
  val spawnedAtSeq: Long = 0L,
)

/**
 * Libro mayor INMUTABLE de recompensas del jugador.
 * Permanece completamente desacoplado de [PhysicalMapMission]: borrar marcadores del mapa
 * por distancia o reciclaje NUNCA borra XP, niveles, títulos ni IDs reclamados.
 *
 * TODO [Persistencia Local]: Guardar este objeto en DataStore/Room (`LocalAccountStore`)
 * y sincronizar deltas con `RadarQuestViewModel.applyRouletteRewards(...)`.
 */
@Immutable
data class PlayerRewardsLedger(
  val totalXp: Long = 0L,
  val bonusLevels: Int = 0,
  val unlockedTitles: List<String> = emptyList(),
  val earnedSpins: Int = 0,
  val claimedMissionIds: Set<String> = emptySet(),
  val completedMissionsCount: Int = 0,
  val recycledUntouchedCount: Int = 0,
  val recycledTouchedOrClaimedCount: Int = 0,
) {
  val totalRecycledCount: Int
    get() = recycledUntouchedCount + recycledTouchedOrClaimedCount
}

@Immutable
data class AimedTargetMarker(
  val id: String,
  val label: String,
  val position: Veyra2Coordinate,
  val linkedMissionId: String? = null,
)

@Immutable
data class AimedRoutePoint(
  val id: String,
  val orderIndex: Int,
  val position: Veyra2Coordinate,
)

@Immutable
data class Veyra2TacticalUiState(
  val mapMode: Veyra2MapMode = Veyra2MapMode.LANDSCAPE_AIMING,
  val lastPortraitMapMode: Veyra2MapMode = Veyra2MapMode.OPEN_75,
  val playerPosition: Veyra2Coordinate = Veyra2Coordinate(0f, 0f),
  val playerHeadingDeg: Float = 18f,
  val cameraCenter: Veyra2Coordinate = Veyra2Coordinate(0f, 0f),
  val aimZoomFactor: Float = 1.15f,
  val isGestureDragging: Boolean = false,
  val isSliding50To75: Boolean = false,
  val blend50To75: Float = 1f,
  val pullDown100Progress: Float = 0f,
  val isToggleButtonVisible: Boolean = true,
  val recycleThresholdMeters: Float = DEFAULT_RECYCLE_DISTANCE_METERS,
  val totalWalkedMeters: Float = 0f,
  /** Todas las misiones vivas en memoria (visibles en lista textual aunque el mapa esté cerrado). */
  val missionsInMemory: List<PhysicalMapMission> = emptyList(),
  /** Subconjunto exacto dibujado en el Canvas (vacío cuando mapMode == CLOSED_MINIMAP). */
  val drawnMissions: List<PhysicalMapMission> = emptyList(),
  val rewardsLedger: PlayerRewardsLedger = PlayerRewardsLedger(),
  val markedTargets: List<AimedTargetMarker> = emptyList(),
  val routePoints: List<AimedRoutePoint> = emptyList(),
  val selectedMissionId: String? = null,
  val missionUnderReticleId: String? = null,
  val refreshTier: AdaptiveRefreshTier = AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ,
  val isRotatingTransition: Boolean = false,
  val debugPanelExpanded: Boolean = false,
  val lastEventLog: String = "Retícula lista • Mueve la mira y marca objetivos o rutas",
) {
  val mapRevealProgress: Float
    get() = when (mapMode) {
      Veyra2MapMode.CLOSED_MINIMAP -> 0f
      Veyra2MapMode.OPEN_50 -> if (isSliding50To75) (0.50f * blend50To75).coerceIn(0f, 0.50f) else 0f
      Veyra2MapMode.OPEN_75 -> when {
        isSliding50To75 -> (0.50f * blend50To75).coerceIn(0f, 0.50f)
        pullDown100Progress > 0.02f -> (0.50f + 0.50f * pullDown100Progress).coerceIn(0.50f, 1f)
        else -> 0.50f
      }
      Veyra2MapMode.OPEN_100, Veyra2MapMode.LANDSCAPE_AIMING -> 1f
    }

  val effectiveScaleRadiusMeters: Int
    get() = when (mapMode) {
      Veyra2MapMode.CLOSED_MINIMAP -> 120
      Veyra2MapMode.OPEN_50 -> 260
      Veyra2MapMode.OPEN_75 -> 650
      Veyra2MapMode.OPEN_100 -> 1950
      Veyra2MapMode.LANDSCAPE_AIMING -> (1450f / aimZoomFactor.coerceIn(0.45f, 3.5f)).toInt().coerceIn(220, 2400)
    }

  val drawnMarkersCount: Int
    get() = drawnMissions.size

  val selectedMission: PhysicalMapMission?
    get() = missionsInMemory.firstOrNull { it.id == selectedMissionId }

  val missionUnderReticle: PhysicalMapMission?
    get() = missionsInMemory.firstOrNull { it.id == missionUnderReticleId }

  companion object {
    const val DEFAULT_RECYCLE_DISTANCE_METERS = 2000f
    const val MAX_MEMORY_MISSIONS = 16
    const val MAX_DRAWN_CANVAS_MISSIONS = 14
    const val SPAWN_WALK_STEP_METERS = 260f
  }
}
