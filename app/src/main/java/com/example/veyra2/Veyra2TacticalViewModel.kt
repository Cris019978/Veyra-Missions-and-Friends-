package com.example.veyra2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maplab.MapGestureThresholds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * ViewModel autocontenido para "Mejora Veyra 2":
 * - Conserva puntos marcados, rutas, misiones y recompensas al rotar a horizontal.
 * - Separa completamente el estado físico efímero del mapa ([PhysicalMapMission]) del
 *   libro mayor inmutable de recompensas ([PlayerRewardsLedger]).
 * - Difiere el dibujado de marcadores hasta abrir el mapa (0 pines en [Veyra2MapMode.CLOSED_MINIMAP]).
 * - Recicla misiones >= 2 km (tocadas o no) sin borrar jamás recompensas ganadas, y genera nuevas al avanzar.
 *
 * TODO [GPS / Sensores / Persistencia Local]:
 * 1. Conectar `LocationTracker.observeLocationUpdates()` a [onRealGpsUpdated].
 * 2. Conectar `Sensor.TYPE_ROTATION_VECTOR` a [onSensorHeadingUpdated].
 * 3. Persistir [PlayerRewardsLedger], objetivos y rutas en Room/DataStore.
 */
class Veyra2TacticalViewModel(
  private val random: Random = Random.Default,
  private val onSyncRewardsToProfile: (xpDelta: Long, bonusLevels: Int, titleReward: String?) -> Unit = { _, _, _ -> },
) : ViewModel() {

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<Veyra2TacticalUiState> = _uiState.asStateFlow()

  private var seqCounter = 100L
  private var targetCounter = 1
  private var routeCounter = 1
  private var walkSpawnAccumulatorMeters = 0f

  private var accumulatedDx = 0f
  private var accumulatedDy = 0f
  private var dragOriginBlend = 1f
  private var horizontalSign = 0f
  private var triggered100InDrag = false

  private var refreshCooldownJob: Job? = null
  private var rotationBoostJob: Job? = null

  private fun createInitialState(): Veyra2TacticalUiState {
    val initialMissions = seedInitialMissions(Veyra2Coordinate(0f, 0f))
    val initialMode = Veyra2MapMode.LANDSCAPE_AIMING
    return Veyra2TacticalUiState(
      mapMode = initialMode,
      lastPortraitMapMode = Veyra2MapMode.OPEN_75,
      missionsInMemory = initialMissions,
      drawnMissions = computeDrawnMissions(
        missions = initialMissions,
        playerPos = Veyra2Coordinate(0f, 0f),
        mapMode = initialMode,
        recycleThresholdMeters = Veyra2TacticalUiState.DEFAULT_RECYCLE_DISTANCE_METERS,
      ),
      selectedMissionId = initialMissions.firstOrNull()?.id,
      refreshTier = AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ,
    )
  }

  private fun seedInitialMissions(origin: Veyra2Coordinate): List<PhysicalMapMission> = listOf(
    PhysicalMapMission(
      id = "v2_near_alpha",
      title = "Baliza Alfa",
      summary = "Escaneo táctico a corta distancia",
      difficulty = Veyra2MissionDifficulty.NORMAL,
      position = Veyra2Coordinate(origin.xMeters + 95f, origin.yMeters - 70f),
      xpReward = 120L,
      titleReward = "Ojo de Halcón",
      spawnedAtSeq = 1L,
    ),
    PhysicalMapMission(
      id = "v2_near_beta",
      title = "Nexo Urbano",
      summary = "Punto de enlace en plaza cercana",
      difficulty = Veyra2MissionDifficulty.TACTICAL,
      position = Veyra2Coordinate(origin.xMeters - 165f, origin.yMeters + 130f),
      xpReward = 210L,
      spawnedAtSeq = 2L,
    ),
    PhysicalMapMission(
      id = "v2_mid_gamma",
      title = "Repetidor Norte",
      summary = "Triangulación de señal intermedia",
      difficulty = Veyra2MissionDifficulty.TACTICAL,
      position = Veyra2Coordinate(origin.xMeters + 390f, origin.yMeters - 310f),
      xpReward = 320L,
      spawnedAtSeq = 3L,
    ),
    PhysicalMapMission(
      id = "v2_high_omega",
      title = "Zona Roja Omega",
      summary = "Objetivo prioritario de largo alcance",
      difficulty = Veyra2MissionDifficulty.HIGH_DIFFICULTY,
      position = Veyra2Coordinate(origin.xMeters + 880f, origin.yMeters + 540f),
      xpReward = 650L,
      bonusLevelsReward = 1,
      titleReward = "Comandante Táctico",
      spawnedAtSeq = 4L,
    ),
    PhysicalMapMission(
      id = "v2_edge_delta",
      title = "Límite Perimetral",
      summary = "Misión al borde del umbral de 2 km",
      difficulty = Veyra2MissionDifficulty.HIGH_DIFFICULTY,
      position = Veyra2Coordinate(origin.xMeters - 1480f, origin.yMeters - 920f), // ~1742m (< 2km)
      xpReward = 780L,
      spawnedAtSeq = 5L,
    ),
  )

  // ===========================================================================
  // ENTRADA / SALIDA DEL MODO HORIZONTAL ("Preparing an Air Strike") Y VISTAS
  // ===========================================================================

  fun enterLandscapeAimingMode() {
    boostRefreshForTransition()
    _uiState.update { cur ->
      val savedPortrait = if (cur.mapMode != Veyra2MapMode.LANDSCAPE_AIMING) {
        cur.mapMode
      } else {
        cur.lastPortraitMapMode
      }
      val drawn = computeDrawnMissions(
        missions = cur.missionsInMemory,
        playerPos = cur.playerPosition,
        mapMode = Veyra2MapMode.LANDSCAPE_AIMING,
        recycleThresholdMeters = cur.recycleThresholdMeters,
      )
      cur.copy(
        mapMode = Veyra2MapMode.LANDSCAPE_AIMING,
        lastPortraitMapMode = savedPortrait,
        drawnMissions = drawn,
        isToggleButtonVisible = true,
        missionUnderReticleId = findClosestMissionToPoint(drawn, cur.cameraCenter, snapRadiusMeters(cur.aimZoomFactor)),
        lastEventLog = "Modo Horizontal activo • Retícula lista (${drawn.size} marcadores)",
      )
    }
  }

  fun setMapMode(mode: Veyra2MapMode) {
    if (mode == Veyra2MapMode.LANDSCAPE_AIMING) {
      enterLandscapeAimingMode()
      return
    }
    boostRefreshForTransition()
    _uiState.update { cur ->
      val blend = if (mode == Veyra2MapMode.OPEN_50) 0f else 1f
      val drawn = computeDrawnMissions(
        missions = cur.missionsInMemory,
        playerPos = cur.playerPosition,
        mapMode = mode,
        recycleThresholdMeters = cur.recycleThresholdMeters,
      )
      cur.copy(
        mapMode = mode,
        lastPortraitMapMode = mode,
        blend50To75 = blend,
        pullDown100Progress = 0f,
        isSliding50To75 = false,
        isGestureDragging = false,
        isToggleButtonVisible = true,
        drawnMissions = drawn,
        missionUnderReticleId = if (mode.isMapOpen) {
          findClosestMissionToPoint(drawn, cur.cameraCenter, snapRadiusMeters(cur.aimZoomFactor))
        } else {
          null
        },
        lastEventLog = if (mode == Veyra2MapMode.CLOSED_MINIMAP) {
          "Mapa cerrado • ${cur.missionsInMemory.size} misiones en memoria, 0 dibujadas"
        } else {
          "${mode.label} • ${drawn.size} misiones dibujadas sin desincronizar"
        },
      )
    }
  }

  fun cyclePortraitScale() {
    val cur = _uiState.value.mapMode
    val next = when (cur) {
      Veyra2MapMode.CLOSED_MINIMAP -> Veyra2MapMode.OPEN_50
      Veyra2MapMode.OPEN_50 -> Veyra2MapMode.OPEN_75
      Veyra2MapMode.OPEN_75 -> Veyra2MapMode.OPEN_100
      Veyra2MapMode.OPEN_100 -> Veyra2MapMode.OPEN_50
      Veyra2MapMode.LANDSCAPE_AIMING -> Veyra2MapMode.OPEN_75
    }
    setMapMode(next)
  }

  // ===========================================================================
  // GESTOS DE ESCALA (50 <-> 75 <-> 100) SIN ROMPER ARQUITECTURA EXISTENTE
  // ===========================================================================

  fun onScaleDragStarted() {
    accumulatedDx = 0f
    accumulatedDy = 0f
    horizontalSign = 0f
    triggered100InDrag = false
    val cur = _uiState.value
    dragOriginBlend = if (cur.mapMode == Veyra2MapMode.OPEN_50) 0f else 1f
    setGestureBoostActive(true)
    _uiState.update { it.copy(isGestureDragging = true, pullDown100Progress = 0f) }
  }

  fun onScaleDragDelta(dx: Float, dy: Float) {
    val cur = _uiState.value
    if (cur.mapMode == Veyra2MapMode.CLOSED_MINIMAP || cur.mapMode == Veyra2MapMode.LANDSCAPE_AIMING) {
      return
    }
    accumulatedDx += dx
    accumulatedDy += dy

    if (cur.mapMode == Veyra2MapMode.OPEN_100) {
      if (accumulatedDy <= MapGestureThresholds.PULL_UP_EXIT_100_PX && !triggered100InDrag) {
        val drawn = computeDrawnMissions(
          cur.missionsInMemory,
          cur.playerPosition,
          Veyra2MapMode.OPEN_75,
          cur.recycleThresholdMeters,
        )
        _uiState.update {
          it.copy(
            mapMode = Veyra2MapMode.OPEN_75,
            lastPortraitMapMode = Veyra2MapMode.OPEN_75,
            blend50To75 = 1f,
            pullDown100Progress = 0f,
            isSliding50To75 = false,
            isToggleButtonVisible = true,
            drawnMissions = drawn,
            lastEventLog = "Gesto arriba: regreso a Vista 75",
          )
        }
        accumulatedDx = 0f
        accumulatedDy = 0f
      }
      return
    }

    val isVerticalPullDown = accumulatedDy > 0f && abs(accumulatedDy) > abs(accumulatedDx) * 1.15f
    if (isVerticalPullDown) {
      val p100 = (accumulatedDy / MapGestureThresholds.PULL_DOWN_FOR_100_PX).coerceIn(0f, 1f)
      if (accumulatedDy >= MapGestureThresholds.PULL_DOWN_FOR_100_PX) {
        triggered100InDrag = true
        val drawn = computeDrawnMissions(
          cur.missionsInMemory,
          cur.playerPosition,
          Veyra2MapMode.OPEN_100,
          cur.recycleThresholdMeters,
        )
        _uiState.update {
          it.copy(
            mapMode = Veyra2MapMode.OPEN_100,
            lastPortraitMapMode = Veyra2MapMode.OPEN_100,
            isSliding50To75 = false,
            pullDown100Progress = 1f,
            isToggleButtonVisible = true,
            drawnMissions = drawn,
            lastEventLog = "Gesto abajo: Vista 100 activada (botón visible)",
          )
        }
      } else {
        _uiState.update { it.copy(pullDown100Progress = p100, isSliding50To75 = false) }
      }
      return
    }

    if (horizontalSign == 0f && abs(accumulatedDx) >= 6f) {
      horizontalSign = if (accumulatedDx >= 0f) 1f else -1f
    }
    val progress = if (horizontalSign == 0f) {
      0f
    } else {
      ((accumulatedDx * horizontalSign) / MapGestureThresholds.FULL_50_75_SLIDE_TRAVEL_PX).coerceIn(0f, 1f)
    }
    val nextBlend = if (dragOriginBlend >= 0.5f) (1f - progress) else progress
    val hideButton = abs(accumulatedDx) >= MapGestureThresholds.DRAG_MINIMIZE_BUTTON_PX

    _uiState.update {
      it.copy(
        isSliding50To75 = true,
        blend50To75 = nextBlend.coerceIn(0f, 1f),
        pullDown100Progress = 0f,
        isToggleButtonVisible = if (hideButton) false else it.isToggleButtonVisible,
      )
    }
  }

  fun onScaleDragEnded() {
    val cur = _uiState.value
    setGestureBoostActive(false)
    if (cur.mapMode == Veyra2MapMode.OPEN_100 || triggered100InDrag) {
      _uiState.update {
        it.copy(
          isGestureDragging = false,
          isSliding50To75 = false,
          pullDown100Progress = 0f,
          isToggleButtonVisible = true,
        )
      }
      return
    }
    val resolvedMode = if (cur.isSliding50To75) {
      val dirDx = accumulatedDx * (if (horizontalSign == 0f) 1f else horizontalSign)
      when {
        dirDx >= MapGestureThresholds.HORIZONTAL_SWITCH_50_75_PX ->
          if (dragOriginBlend >= 0.5f) Veyra2MapMode.OPEN_50 else Veyra2MapMode.OPEN_75
        cur.blend50To75 < 0.5f -> Veyra2MapMode.OPEN_50
        else -> Veyra2MapMode.OPEN_75
      }
    } else {
      cur.mapMode
    }
    val drawn = computeDrawnMissions(
      cur.missionsInMemory,
      cur.playerPosition,
      resolvedMode,
      cur.recycleThresholdMeters,
    )
    _uiState.update {
      it.copy(
        mapMode = resolvedMode,
        lastPortraitMapMode = resolvedMode,
        blend50To75 = if (resolvedMode == Veyra2MapMode.OPEN_50) 0f else 1f,
        isGestureDragging = false,
        isSliding50To75 = false,
        pullDown100Progress = 0f,
        drawnMissions = drawn,
      )
    }
  }

  // ===========================================================================
  // PUNTERÍA EN HORIZONTAL: JOYSTICK, PAN TÁCTIL, PINCH-TO-ZOOM Y RETÍCULA
  // ===========================================================================

  fun setGestureBoostActive(active: Boolean) {
    if (active) {
      refreshCooldownJob?.cancel()
      _uiState.update {
        if (it.refreshTier == AdaptiveRefreshTier.GESTURE_BOOST_120HZ) {
          it
        } else {
          it.copy(refreshTier = AdaptiveRefreshTier.GESTURE_BOOST_120HZ)
        }
      }
    } else {
      scheduleRefreshCooldown()
    }
  }

  fun panCameraByMeters(dxMeters: Float, dyMeters: Float) {
    if (dxMeters == 0f && dyMeters == 0f) return
    _uiState.update { cur ->
      val nextCenter = Veyra2Coordinate(
        xMeters = (cur.cameraCenter.xMeters + dxMeters).coerceIn(-6000f, 6000f),
        yMeters = (cur.cameraCenter.yMeters + dyMeters).coerceIn(-6000f, 6000f),
      )
      val snapId = if (cur.mapMode.isMapOpen) {
        findClosestMissionToPoint(cur.drawnMissions, nextCenter, snapRadiusMeters(cur.aimZoomFactor))
      } else {
        null
      }
      cur.copy(
        cameraCenter = nextCenter,
        missionUnderReticleId = snapId,
        refreshTier = AdaptiveRefreshTier.GESTURE_BOOST_120HZ,
      )
    }
    scheduleRefreshCooldown()
  }

  fun applyAimZoomMultiplier(zoomMultiplier: Float) {
    if (zoomMultiplier == 1f) return
    _uiState.update { cur ->
      val nextZoom = (cur.aimZoomFactor * zoomMultiplier).coerceIn(0.45f, 3.5f)
      val snapId = if (cur.mapMode.isMapOpen) {
        findClosestMissionToPoint(cur.drawnMissions, cur.cameraCenter, snapRadiusMeters(nextZoom))
      } else {
        null
      }
      cur.copy(
        aimZoomFactor = nextZoom,
        missionUnderReticleId = snapId,
        refreshTier = AdaptiveRefreshTier.GESTURE_BOOST_120HZ,
      )
    }
    scheduleRefreshCooldown()
  }

  fun recenterCameraOnPlayer() {
    _uiState.update { cur ->
      val snapId = if (cur.mapMode.isMapOpen) {
        findClosestMissionToPoint(cur.drawnMissions, cur.playerPosition, snapRadiusMeters(cur.aimZoomFactor))
      } else {
        null
      }
      cur.copy(
        cameraCenter = cur.playerPosition,
        missionUnderReticleId = snapId,
        lastEventLog = "Mira centrada en tu posición",
      )
    }
  }

  /**
   * Marca el punto exacto bajo la retícula central; si hay una misión bajo la mira,
   * también marca la misión como tocada/fijada sin perder sincronía.
   */
  fun markTargetAtReticle() {
    _uiState.update { cur ->
      val targetPos = cur.cameraCenter
      val snappedMission = cur.drawnMissions.firstOrNull { it.id == cur.missionUnderReticleId }
        ?: findClosestMissionMission(cur.drawnMissions, targetPos, snapRadiusMeters(cur.aimZoomFactor))

      val marker = AimedTargetMarker(
        id = "tgt_${targetCounter++}",
        label = snappedMission?.title ?: "Objetivo #${targetCounter - 1}",
        position = snappedMission?.position ?: targetPos,
        linkedMissionId = snappedMission?.id,
      )

      val updatedMemory = if (snappedMission != null) {
        cur.missionsInMemory.map { m ->
          if (m.id == snappedMission.id) {
            m.copy(hasBeenTouched = true, isPinnedTarget = true)
          } else {
            m
          }
        }
      } else {
        cur.missionsInMemory
      }
      val updatedDrawn = computeDrawnMissions(
        updatedMemory,
        cur.playerPosition,
        cur.mapMode,
        cur.recycleThresholdMeters,
      )
      cur.copy(
        markedTargets = (cur.markedTargets + marker).takeLast(18),
        missionsInMemory = updatedMemory,
        drawnMissions = updatedDrawn,
        selectedMissionId = snappedMission?.id ?: cur.selectedMissionId,
        lastEventLog = "Objetivo marcado: ${marker.label} (${targetPos.xMeters.toInt()}m, ${targetPos.yMeters.toInt()}m)",
      )
    }
  }

  /**
   * Traza un nuevo punto de ruta en la coordenada exacta bajo la retícula central.
   */
  fun addRouteWaypointAtReticle() {
    _uiState.update { cur ->
      val nextIdx = cur.routePoints.size + 1
      val point = AimedRoutePoint(
        id = "wp_${routeCounter++}",
        orderIndex = nextIdx,
        position = cur.cameraCenter,
      )
      val updatedRoute = (cur.routePoints + point).takeLast(24)
      cur.copy(
        routePoints = updatedRoute,
        lastEventLog = "Ruta: punto #$nextIdx trazado (${point.position.xMeters.toInt()}m, ${point.position.yMeters.toInt()}m)",
      )
    }
  }

  fun clearAimedMarksAndRoute() {
    _uiState.update {
      it.copy(
        markedTargets = emptyList(),
        routePoints = emptyList(),
        lastEventLog = "Objetivos marcados y ruta limpiados",
      )
    }
  }

  // ===========================================================================
  // INSPECCIÓN Y RECOMPENSAS INMUTABLES (SEPARADAS DEL ESTADO FÍSICO DEL MAPA)
  // ===========================================================================

  fun inspectMission(missionId: String) {
    _uiState.update { cur ->
      val updated = cur.missionsInMemory.map { m ->
        if (m.id == missionId) m.copy(hasBeenTouched = true, hasBeenInspected = true) else m
      }
      val drawn = computeDrawnMissions(
        updated,
        cur.playerPosition,
        cur.mapMode,
        cur.recycleThresholdMeters,
      )
      val target = updated.firstOrNull { it.id == missionId }
      cur.copy(
        missionsInMemory = updated,
        drawnMissions = drawn,
        selectedMissionId = missionId,
        lastEventLog = if (target != null) {
          "Inspeccionada: ${target.title} (+${target.xpReward} XP)"
        } else {
          cur.lastEventLog
        },
      )
    }
  }

  /**
   * Reclama la recompensa de una misión en el [PlayerRewardsLedger] inmutable.
   * Aunque luego el marcador físico se elimine por alejarse >= 2 km, la recompensa
   * permanece intacta y [claimedMissionIds] impide duplicados.
   */
  fun claimMissionReward(missionId: String): Boolean {
    val current = _uiState.value
    val target = current.missionsInMemory.firstOrNull { it.id == missionId } ?: return false
    if (missionId in current.rewardsLedger.claimedMissionIds || target.isClaimed) {
      _uiState.update {
        it.copy(lastEventLog = "${target.title} ya fue reclamada anteriormente")
      }
      return false
    }

    _uiState.update { cur ->
      val updatedMissions = cur.missionsInMemory.map { m ->
        if (m.id == missionId) {
          m.copy(hasBeenTouched = true, hasBeenInspected = true, isClaimed = true)
        } else {
          m
        }
      }
      val ledger = cur.rewardsLedger
      val nextTitles = if (target.titleReward != null && target.titleReward !in ledger.unlockedTitles) {
        ledger.unlockedTitles + target.titleReward
      } else {
        ledger.unlockedTitles
      }
      val nextLedger = ledger.copy(
        totalXp = ledger.totalXp + target.xpReward,
        bonusLevels = ledger.bonusLevels + target.bonusLevelsReward,
        unlockedTitles = nextTitles,
        earnedSpins = ledger.earnedSpins + 1,
        claimedMissionIds = ledger.claimedMissionIds + missionId,
        completedMissionsCount = ledger.completedMissionsCount + 1,
      )
      val drawn = computeDrawnMissions(
        updatedMissions,
        cur.playerPosition,
        cur.mapMode,
        cur.recycleThresholdMeters,
      )
      cur.copy(
        missionsInMemory = updatedMissions,
        drawnMissions = drawn,
        rewardsLedger = nextLedger,
        selectedMissionId = missionId,
        lastEventLog = "Reclamada: ${target.title} (+${target.xpReward} XP guardados en libro mayor)",
      )
    }

    onSyncRewardsToProfile(target.xpReward, target.bonusLevelsReward, target.titleReward)
    return true
  }

  // ===========================================================================
  // MOVIMIENTO, RECICLAJE DINÁMICO (>= 2 KM) Y SPAWN SIN SATURAR CANVAS
  // ===========================================================================

  /**
   * Simula o recibe desplazamiento real del jugador en metros:
   * 1. Actualiza posición del jugador (y mueve la cámara junto con él para no perderlo).
   * 2. Recicla/borra del mapa TODA misión >= [recycleThresholdMeters] (tocada, reclamada o sin tocar),
   *    conservando al 100% las recompensas en [PlayerRewardsLedger].
   * 3. Si el jugador caminó >= [Veyra2TacticalUiState.SPAWN_WALK_STEP_METERS], genera nuevas misiones locales.
   */
  fun movePlayerByMeters(dxMeters: Float, dyMeters: Float) {
    val stepDist = hypot(dxMeters, dyMeters)
    if (stepDist <= 0.01f) return
    walkSpawnAccumulatorMeters += stepDist

    _uiState.update { cur ->
      val nextPlayer = Veyra2Coordinate(
        xMeters = cur.playerPosition.xMeters + dxMeters,
        yMeters = cur.playerPosition.yMeters + dyMeters,
      )
      val nextCamera = Veyra2Coordinate(
        xMeters = cur.cameraCenter.xMeters + dxMeters,
        yMeters = cur.cameraCenter.yMeters + dyMeters,
      )
      val heading = ((Math.toDegrees(atan2(dxMeters.toDouble(), (-dyMeters).toDouble())).toFloat()) + 360f) % 360f

      // 1. Reciclaje físico por distancia >= threshold (default 2000m)
      val threshold = cur.recycleThresholdMeters
      val surviving = ArrayList<PhysicalMapMission>(cur.missionsInMemory.size)
      var prunedUntouched = 0
      var prunedTouchedOrClaimed = 0

      cur.missionsInMemory.forEach { mission ->
        val dist = hypot(
          mission.position.xMeters - nextPlayer.xMeters,
          mission.position.yMeters - nextPlayer.yMeters,
        )
        if (dist >= threshold) {
          if (mission.hasBeenTouched || mission.hasBeenInspected || mission.isPinnedTarget || mission.isClaimed) {
            prunedTouchedOrClaimed++
          } else {
            prunedUntouched++
          }
        } else {
          surviving.add(mission)
        }
      }

      // 2. Spawn de nuevas misiones al avanzar, manteniendo tope para no saturar Canvas
      var spawnedCount = 0
      val mutableMissions = surviving.toMutableList()
      if (walkSpawnAccumulatorMeters >= Veyra2TacticalUiState.SPAWN_WALK_STEP_METERS) {
        val batches = (walkSpawnAccumulatorMeters / Veyra2TacticalUiState.SPAWN_WALK_STEP_METERS).toInt().coerceIn(1, 3)
        walkSpawnAccumulatorMeters %= Veyra2TacticalUiState.SPAWN_WALK_STEP_METERS
        repeat(batches) {
          if (mutableMissions.size < Veyra2TacticalUiState.MAX_MEMORY_MISSIONS) {
            mutableMissions.add(createDynamicMissionAhead(nextPlayer, dxMeters, dyMeters))
            spawnedCount++
          }
        }
      }
      // Si la memoria alcanza el tope, recicla las más lejanas primero (conservando rewardsLedger)
      val finalMissions = if (mutableMissions.size > Veyra2TacticalUiState.MAX_MEMORY_MISSIONS) {
        mutableMissions
          .sortedBy {
            hypot(it.position.xMeters - nextPlayer.xMeters, it.position.yMeters - nextPlayer.yMeters)
          }
          .take(Veyra2TacticalUiState.MAX_MEMORY_MISSIONS)
      } else {
        mutableMissions
      }

      val nextLedger = cur.rewardsLedger.copy(
        recycledUntouchedCount = cur.rewardsLedger.recycledUntouchedCount + prunedUntouched,
        recycledTouchedOrClaimedCount = cur.rewardsLedger.recycledTouchedOrClaimedCount + prunedTouchedOrClaimed,
      )
      val drawn = computeDrawnMissions(
        missions = finalMissions,
        playerPos = nextPlayer,
        mapMode = cur.mapMode,
        recycleThresholdMeters = threshold,
      )
      val totalPrunedThisStep = prunedUntouched + prunedTouchedOrClaimed

      cur.copy(
        playerPosition = nextPlayer,
        playerHeadingDeg = heading,
        cameraCenter = nextCamera,
        totalWalkedMeters = cur.totalWalkedMeters + stepDist,
        missionsInMemory = finalMissions,
        drawnMissions = drawn,
        rewardsLedger = nextLedger,
        missionUnderReticleId = if (cur.mapMode.isMapOpen) {
          findClosestMissionToPoint(drawn, nextCamera, snapRadiusMeters(cur.aimZoomFactor))
        } else {
          null
        },
        refreshTier = AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ,
        lastEventLog = buildString {
          append("Avance +${stepDist.toInt()}m")
          if (totalPrunedThisStep > 0) {
            append(" • Recicladas $totalPrunedThisStep (>=${threshold.toInt()}m, XP intacta)")
          }
          if (spawnedCount > 0) {
            append(" • +$spawnedCount nuevas")
          }
        },
      )
    }
    scheduleRefreshCooldown(targetIdleAfterMs = 1100L)
  }

  /**
   * TODO [GPS Real]: Conectar `LocationTracker` convirtiendo lat/lon en metros relativos.
   */
  fun onRealGpsUpdated(xMeters: Float, yMeters: Float) {
    val cur = _uiState.value.playerPosition
    movePlayerByMeters(xMeters - cur.xMeters, yMeters - cur.yMeters)
  }

  /**
   * TODO [Sensores Reales]: Conectar brújula / giroscopio del dispositivo.
   */
  fun onSensorHeadingUpdated(headingDeg: Float) {
    _uiState.update { it.copy(playerHeadingDeg = ((headingDeg % 360f) + 360f) % 360f) }
  }

  // ===========================================================================
  // CONTROLES DEL PANEL DE PRUEBA RÁPIDA (DEBUG EN MEMORIA)
  // ===========================================================================

  fun toggleDebugPanel() {
    _uiState.update { it.copy(debugPanelExpanded = !it.debugPanelExpanded) }
  }

  fun spawnDebugMission(
    distanceMeters: Float,
    touched: Boolean = false,
    claimedImmediately: Boolean = false,
    difficulty: Veyra2MissionDifficulty = Veyra2MissionDifficulty.TACTICAL,
  ): String {
    val cur = _uiState.value
    val angle = random.nextDouble(0.0, Math.PI * 2.0)
    val id = "v2_dbg_${++seqCounter}"
    val xp = when (difficulty) {
      Veyra2MissionDifficulty.NORMAL -> 100L
      Veyra2MissionDifficulty.TACTICAL -> 220L
      Veyra2MissionDifficulty.HIGH_DIFFICULTY -> 540L
    }
    val mission = PhysicalMapMission(
      id = id,
      title = when {
        claimedImmediately -> "Reclamada #${seqCounter}"
        touched -> "Inspeccionada #${seqCounter}"
        distanceMeters >= cur.recycleThresholdMeters -> "Lejana (>2km) #${seqCounter}"
        else -> "Prueba #${seqCounter}"
      },
      summary = "Misión de prueba a ${distanceMeters.toInt()} m",
      difficulty = difficulty,
      position = Veyra2Coordinate(
        xMeters = cur.playerPosition.xMeters + (cos(angle) * distanceMeters).toFloat(),
        yMeters = cur.playerPosition.yMeters + (sin(angle) * distanceMeters).toFloat(),
      ),
      xpReward = xp,
      titleReward = if (difficulty == Veyra2MissionDifficulty.HIGH_DIFFICULTY) "Explorador de Élite" else null,
      hasBeenTouched = touched || claimedImmediately,
      hasBeenInspected = touched || claimedImmediately,
      isClaimed = false,
      spawnedAtSeq = seqCounter,
    )

    _uiState.update { state ->
      val updatedList = (listOf(mission) + state.missionsInMemory)
        .take(Veyra2TacticalUiState.MAX_MEMORY_MISSIONS)
      val drawn = computeDrawnMissions(
        updatedList,
        state.playerPosition,
        state.mapMode,
        state.recycleThresholdMeters,
      )
      state.copy(
        missionsInMemory = updatedList,
        drawnMissions = drawn,
        selectedMissionId = mission.id,
        lastEventLog = "Debug: creada ${mission.title} a ${distanceMeters.toInt()} m",
      )
    }

    if (claimedImmediately) {
      claimMissionReward(id)
    }
    return id
  }

  /**
   * Ejecuta una pasada manual de reciclaje (equivalente al siguiente tick de movimiento)
   * útil en el panel Debug tras crear una misión > 2 km.
   */
  fun runManualDistanceRecyclePass() {
    movePlayerByMeters(dxMeters = 1f, dyMeters = 0f)
  }

  fun resetPrototypeSession(keepRewardsLedger: Boolean = false) {
    refreshCooldownJob?.cancel()
    rotationBoostJob?.cancel()
    walkSpawnAccumulatorMeters = 0f
    targetCounter = 1
    routeCounter = 1
    val freshMissions = seedInitialMissions(Veyra2Coordinate(0f, 0f))
    _uiState.update { cur ->
      val ledger = if (keepRewardsLedger) cur.rewardsLedger else PlayerRewardsLedger()
      val drawn = computeDrawnMissions(
        missions = freshMissions,
        playerPos = Veyra2Coordinate(0f, 0f),
        mapMode = cur.mapMode,
        recycleThresholdMeters = cur.recycleThresholdMeters,
      )
      cur.copy(
        playerPosition = Veyra2Coordinate(0f, 0f),
        cameraCenter = Veyra2Coordinate(0f, 0f),
        aimZoomFactor = 1.15f,
        totalWalkedMeters = 0f,
        missionsInMemory = freshMissions,
        drawnMissions = drawn,
        rewardsLedger = ledger,
        markedTargets = emptyList(),
        routePoints = emptyList(),
        selectedMissionId = freshMissions.firstOrNull()?.id,
        missionUnderReticleId = null,
        refreshTier = AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ,
        lastEventLog = if (keepRewardsLedger) {
          "Mapa reiniciado • Recompensas preservadas (${ledger.totalXp} XP)"
        } else {
          "Estado completo reiniciado"
        },
      )
    }
  }

  // ===========================================================================
  // UTILIDADES INTERNAS
  // ===========================================================================

  private fun boostRefreshForTransition() {
    rotationBoostJob?.cancel()
    _uiState.update {
      it.copy(
        refreshTier = AdaptiveRefreshTier.GESTURE_BOOST_120HZ,
        isRotatingTransition = true,
      )
    }
    rotationBoostJob = viewModelScope.launch {
      delay(480L)
      _uiState.update {
        it.copy(
          isRotatingTransition = false,
          refreshTier = AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ,
        )
      }
      scheduleRefreshCooldown(targetIdleAfterMs = 900L)
    }
  }

  private fun scheduleRefreshCooldown(targetIdleAfterMs: Long = 750L) {
    refreshCooldownJob?.cancel()
    refreshCooldownJob = viewModelScope.launch {
      delay(320L)
      _uiState.update { cur ->
        if (!cur.isGestureDragging && cur.refreshTier == AdaptiveRefreshTier.GESTURE_BOOST_120HZ) {
          cur.copy(refreshTier = AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ)
        } else {
          cur
        }
      }
      delay(targetIdleAfterMs)
      _uiState.update { cur ->
        if (!cur.isGestureDragging && !cur.isRotatingTransition) {
          cur.copy(refreshTier = AdaptiveRefreshTier.IDLE_LOW_30HZ)
        } else {
          cur
        }
      }
    }
  }

  private fun createDynamicMissionAhead(
    playerPos: Veyra2Coordinate,
    dxMeters: Float,
    dyMeters: Float,
  ): PhysicalMapMission {
    val baseAngle = atan2(dyMeters.toDouble(), dxMeters.toDouble())
    val jitter = random.nextDouble(-1.1, 1.1)
    val dist = random.nextDouble(140.0, 920.0).toFloat()
    val difficulty = when {
      dist >= 650f -> Veyra2MissionDifficulty.HIGH_DIFFICULTY
      dist >= 320f -> Veyra2MissionDifficulty.TACTICAL
      else -> Veyra2MissionDifficulty.NORMAL
    }
    val id = "v2_walk_${++seqCounter}"
    return PhysicalMapMission(
      id = id,
      title = when (difficulty) {
        Veyra2MissionDifficulty.NORMAL -> "Punto Ruta #$seqCounter"
        Veyra2MissionDifficulty.TACTICAL -> "Baliza Móvil #$seqCounter"
        Veyra2MissionDifficulty.HIGH_DIFFICULTY -> "Asalto Sector #$seqCounter"
      },
      summary = "Generada dinámicamente al explorar",
      difficulty = difficulty,
      position = Veyra2Coordinate(
        xMeters = playerPos.xMeters + (cos(baseAngle + jitter) * dist).toFloat(),
        yMeters = playerPos.yMeters + (sin(baseAngle + jitter) * dist).toFloat(),
      ),
      xpReward = when (difficulty) {
        Veyra2MissionDifficulty.NORMAL -> 115L
        Veyra2MissionDifficulty.TACTICAL -> 240L
        Veyra2MissionDifficulty.HIGH_DIFFICULTY -> 580L
      },
      titleReward = if (difficulty == Veyra2MissionDifficulty.HIGH_DIFFICULTY) "Nómada Táctico" else null,
      spawnedAtSeq = seqCounter,
    )
  }

  private fun computeDrawnMissions(
    missions: List<PhysicalMapMission>,
    playerPos: Veyra2Coordinate,
    mapMode: Veyra2MapMode,
    recycleThresholdMeters: Float,
  ): List<PhysicalMapMission> {
    // Regla 2: diferir el dibujado de marcadores hasta que el usuario abra/entre al mapa.
    if (!mapMode.isMapOpen) return emptyList()

    val maxRangeMeters = when (mapMode) {
      Veyra2MapMode.CLOSED_MINIMAP -> return emptyList()
      Veyra2MapMode.OPEN_50 -> 300f
      Veyra2MapMode.OPEN_75 -> 720f
      Veyra2MapMode.OPEN_100, Veyra2MapMode.LANDSCAPE_AIMING -> recycleThresholdMeters
    }

    return missions
      .asSequence()
      .map { mission ->
        val d = hypot(mission.position.xMeters - playerPos.xMeters, mission.position.yMeters - playerPos.yMeters)
        mission to d
      }
      .filter { (mission, d) ->
        mission.isPinnedTarget || d <= maxRangeMeters
      }
      .sortedBy { it.second }
      .take(Veyra2TacticalUiState.MAX_DRAWN_CANVAS_MISSIONS)
      .map { it.first }
      .toList()
  }

  private fun snapRadiusMeters(aimZoomFactor: Float): Float =
    (70f / aimZoomFactor.coerceIn(0.45f, 3.5f)).coerceIn(22f, 160f)

  private fun findClosestMissionToPoint(
    missions: List<PhysicalMapMission>,
    point: Veyra2Coordinate,
    maxSnapMeters: Float,
  ): String? = findClosestMissionMission(missions, point, maxSnapMeters)?.id

  private fun findClosestMissionMission(
    missions: List<PhysicalMapMission>,
    point: Veyra2Coordinate,
    maxSnapMeters: Float,
  ): PhysicalMapMission? {
    var best: PhysicalMapMission? = null
    var bestDist = maxSnapMeters
    for (mission in missions) {
      val d = hypot(mission.position.xMeters - point.xMeters, mission.position.yMeters - point.yMeters)
      if (d <= bestDist) {
        bestDist = d
        best = mission
      }
    }
    return best
  }
}
