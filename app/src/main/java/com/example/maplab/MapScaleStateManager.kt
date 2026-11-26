package com.example.maplab

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Gestor de estado autocontenido en memoria para probar el Mapa de 3 vistas (50 / 75 / 100)
 * y sus gestos táctiles sin interferir con el sistema principal de Radar de Veyra.
 *
 * TODO [GPS / Sensores / Persistencia Local]:
 * 1. Conectar aquí `LocationTracker.observeLocationUpdates()` para actualizar `playerPosition`
 *    con coordenadas GPS reales en lugar del movimiento simulado del panel Debug.
 * 2. Conectar un sensor de orientación (`Sensor.TYPE_ROTATION_VECTOR`) para `playerHeadingDeg`.
 * 3. Persistir la última escala preferida (`activeScale`) y el Radio de Vista en DataStore
 *    (`LocalThemePreferences`) y sincronizar misiones desde `RadarQuestRepository` / Room.
 */
class MapScaleStateManager(
  private val random: Random = Random.Default,
) : ViewModel() {

  private val _uiState = MutableStateFlow(MapScaleUiState())
  val uiState: StateFlow<MapScaleUiState> = _uiState.asStateFlow()

  private var accumulatedDx = 0f
  private var accumulatedDy = 0f
  private var dragOriginBlend50To75 = 1f
  private var horizontalSwipeSign = 0f
  private var triggered100InCurrentDrag = false
  private var missionSequence = 1

  fun onDragStarted() {
    accumulatedDx = 0f
    accumulatedDy = 0f
    horizontalSwipeSign = 0f
    triggered100InCurrentDrag = false
    val current = _uiState.value
    dragOriginBlend50To75 = when (current.activeScale) {
      MapViewScale.SCALE_50 -> 0f
      MapViewScale.SCALE_75 -> 1f
      MapViewScale.SCALE_100 -> 1f
    }
    _uiState.update {
      it.copy(
        isGestureDragging = true,
        pullDown100Progress = 0f,
      )
    }
  }

  /**
   * Procesa el desplazamiento táctil continuo en tiempo real:
   * - Gesto horizontal (izquierda o derecha): desliza de forma fluida entre Vista 50 y Vista 75
   *   y oculta/minimiza el botón de alternar para priorizar el gesto directo.
   * - Gesto hacia abajo superando [MapGestureThresholds.PULL_DOWN_FOR_100_PX]: activa la Vista 100
   *   y hace reaparecer el botón de alternar vistas.
   * - Estando en Vista 100, deslizar hacia arriba regresa suavemente a Vista 75.
   */
  fun onDragDelta(dx: Float, dy: Float) {
    accumulatedDx += dx
    accumulatedDy += dy

    val current = _uiState.value

    if (current.activeScale == MapViewScale.SCALE_100) {
      if (accumulatedDy <= MapGestureThresholds.PULL_UP_EXIT_100_PX && !triggered100InCurrentDrag) {
        _uiState.update {
          it.copy(
            activeScale = MapViewScale.SCALE_75,
            blend50To75 = 1f,
            pullDown100Progress = 0f,
            isSlidingBetween50And75 = false,
            buttonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
            lastEventLog = "Gesto arriba: regreso de Vista 100 a Vista 75",
          )
        }
        accumulatedDx = 0f
        accumulatedDy = 0f
      }
      return
    }

    val isVerticalPullDown = accumulatedDy > 0f && abs(accumulatedDy) > abs(accumulatedDx) * 1.15f
    if (isVerticalPullDown) {
      val progress100 = (accumulatedDy / MapGestureThresholds.PULL_DOWN_FOR_100_PX).coerceIn(0f, 1f)
      if (accumulatedDy >= MapGestureThresholds.PULL_DOWN_FOR_100_PX) {
        triggered100InCurrentDrag = true
        _uiState.update {
          it.copy(
            activeScale = MapViewScale.SCALE_100,
            isSlidingBetween50And75 = false,
            pullDown100Progress = 1f,
            buttonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
            lastEventLog = "Gesto abajo superado -> Vista 100 activada (botón visible)",
          )
        }
      } else {
        _uiState.update {
          it.copy(
            pullDown100Progress = progress100,
            isSlidingBetween50And75 = false,
          )
        }
      }
      return
    }

    if (horizontalSwipeSign == 0f && abs(accumulatedDx) >= 6f) {
      horizontalSwipeSign = if (accumulatedDx >= 0f) 1f else -1f
    }

    val directionalProgress = if (horizontalSwipeSign == 0f) {
      0f
    } else {
      ((accumulatedDx * horizontalSwipeSign) / MapGestureThresholds.FULL_50_75_SLIDE_TRAVEL_PX).coerceIn(0f, 1f)
    }

    // Desde Vista 75 (1f) desliza hacia Vista 50 (0f); desde Vista 50 (0f) desliza hacia Vista 75 (1f)
    val nextBlend = if (dragOriginBlend50To75 >= 0.5f) {
      (1f - directionalProgress).coerceIn(0f, 1f)
    } else {
      directionalProgress.coerceIn(0f, 1f)
    }
    val hideButtonNow = abs(accumulatedDx) >= MapGestureThresholds.DRAG_MINIMIZE_BUTTON_PX

    _uiState.update {
      it.copy(
        isSlidingBetween50And75 = true,
        blend50To75 = nextBlend,
        pullDown100Progress = 0f,
        buttonVisibilityMode = if (hideButtonNow) {
          ToggleButtonVisibilityMode.MINIMIZED_DURING_GESTURE
        } else {
          it.buttonVisibilityMode
        },
        lastEventLog = if (hideButtonNow) {
          "Deslizando entre 50 y 75 • Botón minimizado para priorizar el gesto"
        } else {
          it.lastEventLog
        },
      )
    }
  }

  fun onDragEnded() {
    val current = _uiState.value
    if (current.activeScale == MapViewScale.SCALE_100 || triggered100InCurrentDrag) {
      _uiState.update {
        it.copy(
          isGestureDragging = false,
          isSlidingBetween50And75 = false,
          pullDown100Progress = 0f,
          buttonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
        )
      }
      resetAccumulators()
      return
    }

    val resolvedScale = if (current.isSlidingBetween50And75) {
      val directionalDx = accumulatedDx * (if (horizontalSwipeSign == 0f) 1f else horizontalSwipeSign)
      when {
        directionalDx >= MapGestureThresholds.HORIZONTAL_SWITCH_50_75_PX -> {
          if (dragOriginBlend50To75 >= 0.5f) MapViewScale.SCALE_50 else MapViewScale.SCALE_75
        }
        current.blend50To75 < 0.5f -> MapViewScale.SCALE_50
        else -> MapViewScale.SCALE_75
      }
    } else {
      current.activeScale
    }

    val resolvedBlend = if (resolvedScale == MapViewScale.SCALE_50) 0f else 1f
    val keepMinimized = current.isSlidingBetween50And75 &&
      abs(accumulatedDx) >= MapGestureThresholds.DRAG_MINIMIZE_BUTTON_PX

    _uiState.update {
      it.copy(
        activeScale = resolvedScale,
        blend50To75 = resolvedBlend,
        isGestureDragging = false,
        isSlidingBetween50And75 = false,
        pullDown100Progress = 0f,
        buttonVisibilityMode = if (keepMinimized) {
          ToggleButtonVisibilityMode.MINIMIZED_DURING_GESTURE
        } else {
          it.buttonVisibilityMode
        },
        lastEventLog = if (current.isSlidingBetween50And75) {
          "Gesto confirmado: ${resolvedScale.label} (${resolvedScale.viewRadiusMeters} m)"
        } else {
          it.lastEventLog
        },
      )
    }
    resetAccumulators()
  }

  fun onDragCancelled() {
    _uiState.update { current ->
      current.copy(
        isGestureDragging = false,
        isSlidingBetween50And75 = false,
        pullDown100Progress = 0f,
        blend50To75 = if (current.activeScale == MapViewScale.SCALE_50) 0f else 1f,
      )
    }
    resetAccumulators()
  }

  /**
   * Alterna cómodamente entre 50 -> 75 -> 100 -> 50 al pulsar el botón en pantalla.
   */
  fun cycleScaleFromButton() {
    val next = _uiState.value.activeScale.nextScale()
    forceScale(next, source = "Botón")
  }

  fun forceScale(scale: MapViewScale, source: String = "Debug") {
    _uiState.update {
      it.copy(
        activeScale = scale,
        blend50To75 = if (scale == MapViewScale.SCALE_50) 0f else 1f,
        isGestureDragging = false,
        isSlidingBetween50And75 = false,
        pullDown100Progress = 0f,
        buttonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
        lastEventLog = "$source -> ${scale.label} activada (${scale.viewRadiusMeters} m)",
      )
    }
  }

  fun restoreToggleButton() {
    _uiState.update {
      it.copy(
        buttonVisibilityMode = ToggleButtonVisibilityMode.VISIBLE_PRIMARY,
        lastEventLog = "Botón de alternar vistas restaurado",
      )
    }
  }

  fun simulatePlayerStep(dxMeters: Float, dyMeters: Float) {
    _uiState.update { current ->
      val nextPos = LocalMapCoordinate(
        xMeters = (current.playerPosition.xMeters + dxMeters).coerceIn(-1200f, 1200f),
        yMeters = (current.playerPosition.yMeters + dyMeters).coerceIn(-1200f, 1200f),
      )
      val heading = Math.toDegrees(atan2(dxMeters.toDouble(), (-dyMeters).toDouble())).toFloat()
      current.copy(
        playerPosition = nextPos,
        playerHeadingDeg = (heading + 360f) % 360f,
        lastEventLog = "Jugador movido a (${nextPos.xMeters.toInt()}m, ${nextPos.yMeters.toInt()}m)",
      )
    }
  }

  fun spawnNearMission() {
    val cur = _uiState.value
    val angle = random.nextDouble(0.0, Math.PI * 2.0)
    val dist = random.nextDouble(55.0, 145.0).toFloat()
    val newMission = MapLabMission(
      id = "m_gen_near_${missionSequence++}",
      title = "Pulso Cercano #$missionSequence",
      difficulty = if (missionSequence % 2 == 0) MapMissionDifficulty.TACTICAL else MapMissionDifficulty.NORMAL,
      position = LocalMapCoordinate(
        xMeters = cur.playerPosition.xMeters + (kotlin.math.cos(angle) * dist).toFloat(),
        yMeters = cur.playerPosition.yMeters + (kotlin.math.sin(angle) * dist).toFloat(),
      ),
      xpReward = 110 + (missionSequence * 15),
    )
    _uiState.update {
      it.copy(
        missions = listOf(newMission) + it.missions.take(11),
        selectedMissionId = newMission.id,
        lastEventLog = "Nueva misión cercana a ${dist.toInt()} m",
      )
    }
  }

  fun spawnHardDistantMission() {
    val cur = _uiState.value
    val angle = random.nextDouble(0.0, Math.PI * 2.0)
    val dist = random.nextDouble(480.0, 960.0).toFloat()
    val newMission = MapLabMission(
      id = "m_gen_hard_${missionSequence++}",
      title = "Alto Riesgo #$missionSequence",
      difficulty = MapMissionDifficulty.HIGH_DIFFICULTY,
      position = LocalMapCoordinate(
        xMeters = cur.playerPosition.xMeters + (kotlin.math.cos(angle) * dist).toFloat(),
        yMeters = cur.playerPosition.yMeters + (kotlin.math.sin(angle) * dist).toFloat(),
      ),
      xpReward = 550 + (missionSequence * 40),
      isActive = true,
    )
    _uiState.update {
      it.copy(
        missions = listOf(newMission) + it.missions.take(11),
        selectedMissionId = newMission.id,
        lastEventLog = "Misión difícil generada a ${dist.toInt()} m (ideal en Vista 100)",
      )
    }
  }

  fun shuffleMissions() {
    val cur = _uiState.value
    val moved = cur.missions.map { mission ->
      val maxRange = when (mission.difficulty) {
        MapMissionDifficulty.NORMAL -> 260.0
        MapMissionDifficulty.TACTICAL -> 480.0
        MapMissionDifficulty.HIGH_DIFFICULTY -> 980.0
      }
      val minRange = if (mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY) 380.0 else 50.0
      val angle = random.nextDouble(0.0, Math.PI * 2.0)
      val dist = random.nextDouble(minRange, maxRange).toFloat()
      mission.copy(
        position = LocalMapCoordinate(
          xMeters = cur.playerPosition.xMeters + (kotlin.math.cos(angle) * dist).toFloat(),
          yMeters = cur.playerPosition.yMeters + (kotlin.math.sin(angle) * dist).toFloat(),
        ),
      )
    }
    _uiState.update {
      it.copy(
        missions = moved,
        lastEventLog = "Misiones reubicadas alrededor del jugador",
      )
    }
  }

  fun selectMission(missionId: String?) {
    _uiState.update { it.copy(selectedMissionId = missionId) }
  }

  fun toggleDebugPanel() {
    _uiState.update { it.copy(debugPanelExpanded = !it.debugPanelExpanded) }
  }

  fun distanceFromPlayer(mission: MapLabMission): Float {
    val p = _uiState.value.playerPosition
    return hypot(mission.position.xMeters - p.xMeters, mission.position.yMeters - p.yMeters)
  }

  fun resetAll() {
    resetAccumulators()
    missionSequence = 1
    _uiState.value = MapScaleUiState(
      debugPanelExpanded = _uiState.value.debugPanelExpanded,
    )
  }

  private fun resetAccumulators() {
    accumulatedDx = 0f
    accumulatedDy = 0f
    horizontalSwipeSign = 0f
    triggered100InCurrentDrag = false
  }
}
