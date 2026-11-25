package com.example.roulette

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Gestor de estado autocontenido en memoria para pruebas rápidas de la Ruleta de doble vista.
 *
 * No toca Room, Firestore ni el perfil principal de Veyra mientras se prueba.
 *
 * TODO [Persistencia Local Futura]:
 * 1. Inyectar aquí un repositorio respaldado por DataStore / Room (ej. RouletteLocalStore).
 * 2. Al inicializar, cargar `totalSpins`, `accumulatedXp`, `floorChargePercent`,
 *    `unlockedExclusiveTitles`, `spinMissions` y `spinHistory` desde disco.
 * 3. Al completar cada tirada en `applySpinOutcome()`, persistir el estado actualizado
 *    y acreditar el XP / nivel / título real en `RadarQuestRepository` y `LocalAuthManager`.
 */
class RouletteStateManager(
  private val prizes: List<RoulettePrize> = RouletteCatalogDefaults.defaultPrizes,
  private val random: Random = Random.Default,
) : ViewModel() {

  private val engine = AntiFormulaProbabilityEngine(
    prizes = prizes,
    random = random,
  )

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<RouletteUiState> = _uiState.asStateFlow()

  private var nextNonce = 1L

  private fun createInitialState(): RouletteUiState = RouletteUiState(
    activeView = RouletteDisplayView.CLASSIC,
    prizeProbabilities = engine.currentSnapshots(),
    spinMissions = RouletteCatalogDefaults.defaultSpinMissions(),
  )

  fun switchView(targetView: RouletteDisplayView) {
    _uiState.update { it.copy(activeView = targetView) }
  }

  fun toggleDebugPanel() {
    _uiState.update { it.copy(debugPanelExpanded = !it.debugPanelExpanded) }
  }

  /**
   * Inicia una tirada animada en la vista activa (Clásica u Horizontal).
   * La animación de Compose dura ~2.2s y el estado definitivo se consolida al terminar.
   */
  fun spinOnceAnimated(animationDurationMs: Long = 2200L) {
    if (_uiState.value.isSpinning) return

    val pityReady = _uiState.value.floorChargePercent >= 100
    val (winnerIdx, newSnapshots) = engine.rollAndRecalibrate(pityBoostReady = pityReady)
    val winningProbPercent = (_uiState.value.prizeProbabilities.getOrNull(winnerIdx)?.probability ?: 0.0) * 100.0
    val nonce = nextNonce++

    _uiState.update {
      it.copy(
        isSpinning = true,
        pendingTargetPrizeIndex = winnerIdx,
        spinAnimationNonce = nonce,
      )
    }

    if (animationDurationMs <= 0L) {
      applySpinOutcome(
        winnerIndex = winnerIdx,
        updatedSnapshots = newSnapshots,
        winningProbPercent = winningProbPercent,
        wasSimulatedBatch = false,
      )
    } else {
      viewModelScope.launch {
        delay(animationDurationMs)
        applySpinOutcome(
          winnerIndex = winnerIdx,
          updatedSnapshots = newSnapshots,
          winningProbPercent = winningProbPercent,
          wasSimulatedBatch = false,
        )
      }
    }
  }

  /**
   * Ejecuta un giro instantáneo sin espera de animación (útil en panel Debug o pruebas).
   */
  fun spinOnceInstant() {
    if (_uiState.value.isSpinning) return
    val pityReady = _uiState.value.floorChargePercent >= 100
    val probBefore = _uiState.value.prizeProbabilities
    val (winnerIdx, newSnapshots) = engine.rollAndRecalibrate(pityBoostReady = pityReady)
    val winPct = (probBefore.getOrNull(winnerIdx)?.probability ?: 0.0) * 100.0
    applySpinOutcome(
      winnerIndex = winnerIdx,
      updatedSnapshots = newSnapshots,
      winningProbPercent = winPct,
      wasSimulatedBatch = false,
    )
  }

  /**
   * Simula N tiradas seguidas en memoria (por ejemplo 10 o 50) ejecutando el motor
   * anti-fórmula paso a paso, actualizando misiones, historial y porcentajes en vivo.
   */
  fun simulateBatchSpins(count: Int) {
    if (_uiState.value.isSpinning || count <= 0) return
    repeat(count) {
      val pityReady = _uiState.value.floorChargePercent >= 100
      val probBefore = _uiState.value.prizeProbabilities
      val (winnerIdx, newSnapshots) = engine.rollAndRecalibrate(pityBoostReady = pityReady)
      val winPct = (probBefore.getOrNull(winnerIdx)?.probability ?: 0.0) * 100.0
      applySpinOutcome(
        winnerIndex = winnerIdx,
        updatedSnapshots = newSnapshots,
        winningProbPercent = winPct,
        wasSimulatedBatch = true,
      )
    }
  }

  fun resetAll() {
    engine.reset()
    nextNonce = 1L
    _uiState.value = createInitialState().copy(
      activeView = _uiState.value.activeView,
      debugPanelExpanded = _uiState.value.debugPanelExpanded,
    )
  }

  private fun applySpinOutcome(
    winnerIndex: Int,
    updatedSnapshots: List<PrizeProbabilitySnapshot>,
    winningProbPercent: Double,
    wasSimulatedBatch: Boolean,
  ) {
    val prize = prizes[winnerIndex]

    _uiState.update { current ->
      val newTotalSpins = current.totalSpins + 1

      // Piso equilibrado: los premios de XP baja cargan el medidor de impulso; al llegar a 100% otorgan +50 XP extra
      var charge = current.floorChargePercent + prize.floorChargeBoost
      var floorBonusXp = 0L
      var floorTriggers = current.floorBonusTriggers
      if (charge >= 100) {
        charge -= 100
        floorBonusXp = FLOOR_OVERCHARGE_BONUS_XP
        floorTriggers += 1
      }

      // Actualizar las 5 misiones propias de la ruleta escalonadas
      var missionXpEarned = 0L
      var newlyCompletedMissionTitle: String? = null
      val updatedMissions = current.spinMissions.map { mission ->
        val nextCount = newTotalSpins.coerceAtMost(mission.targetSpins)
        val completedNow = !mission.isCompleted && nextCount >= mission.targetSpins
        if (completedNow) {
          missionXpEarned += mission.rewardXp
          newlyCompletedMissionTitle = mission.title
        }
        mission.copy(
          currentSpins = nextCount,
          isCompleted = mission.isCompleted || nextCount >= mission.targetSpins,
        )
      }

      // Títulos exclusivos desbloqueados (si ya se tiene, otorga compensación en XP)
      val updatedTitles = current.unlockedExclusiveTitles.toMutableList()
      var duplicateTitleCompensation = 0L
      if (!prize.unlockedTitle.isNullOrBlank()) {
        if (prize.unlockedTitle !in updatedTitles) {
          updatedTitles.add(prize.unlockedTitle)
        } else {
          duplicateTitleCompensation = prize.xpAmount
        }
      }

      // Misiones aleatorias y misiones de alto riesgo generadas en memoria
      val updatedRandomMissions = current.activeRandomMissions.toMutableList()
      if (prize.category == RoulettePrizeCategory.RANDOM_MISSION) {
        val pool = RouletteCatalogDefaults.randomMissionPool
        val picked = pool[(newTotalSpins + winnerIndex) % pool.size]
        updatedRandomMissions.add(0, picked)
      }
      val updatedHardMissions = current.activeHardMissions.toMutableList()
      if (prize.category == RoulettePrizeCategory.HARD_MISSION) {
        val pool = RouletteCatalogDefaults.hardMissionPool
        val picked = pool[(newTotalSpins + winnerIndex) % pool.size]
        updatedHardMissions.add(0, picked)
      }

      val basePrizeXp = if (prize.unlockedTitle != null && duplicateTitleCompensation == 0L) {
        0L
      } else {
        prize.xpAmount
      }
      val totalXpGain = basePrizeXp + floorBonusXp + missionXpEarned

      val record = RouletteSpinRecord(
        spinNumber = newTotalSpins,
        prize = prize,
        bonusFloorXp = floorBonusXp,
        viewUsed = current.activeView,
        wasSimulatedBatch = wasSimulatedBatch,
        timestampMs = System.currentTimeMillis(),
        winningProbabilityPercent = winningProbPercent,
      )

      // TODO [Persistencia Local Futura]: Guardar `record`, `updatedMissions`, `updatedTitles` y XP acumulado en DataStore / Room.
      current.copy(
        isSpinning = false,
        totalSpins = newTotalSpins,
        accumulatedXp = current.accumulatedXp + totalXpGain,
        bonusLevelsWon = current.bonusLevelsWon + prize.freeLevels,
        floorChargePercent = charge,
        floorBonusTriggers = floorTriggers,
        unlockedExclusiveTitles = updatedTitles,
        activeRandomMissions = updatedRandomMissions.take(8),
        activeHardMissions = updatedHardMissions.take(8),
        prizeProbabilities = updatedSnapshots,
        spinMissions = updatedMissions,
        spinHistory = (listOf(record) + current.spinHistory).take(60),
        lastWonRecord = record,
        lastCompletedSpinMissionTitle = newlyCompletedMissionTitle ?: current.lastCompletedSpinMissionTitle,
      )
    }
  }

  private companion object {
    const val FLOOR_OVERCHARGE_BONUS_XP = 50L
  }
}
