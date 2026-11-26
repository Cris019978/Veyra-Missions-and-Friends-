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
 * Gestor de estado de la Ruleta de doble vista con motor anti-fórmula, rotación de 10 variantes
 * de títulos especiales reclamables y sincronización real de XP / Niveles / Títulos con el perfil principal.
 */
class RouletteStateManager(
  private val prizes: List<RoulettePrize> = RouletteCatalogDefaults.defaultPrizes,
  private val random: Random = Random.Default,
  private val localStore: RouletteLocalStore? = null,
  private val initialUnlockedProfileTitles: List<String> = emptyList(),
  private val initialEquippedTitle: String = "",
  private val initialMainLevel: Long = 1L,
  private val initialMainXp: Long = 0L,
  private val onRewardEarned: (xpDelta: Long, bonusLevelsDelta: Int) -> Unit = { _, _ -> },
  private val onTitleClaimed: (title: String) -> Unit = {},
  private val onTitleEquipped: (title: String) -> Unit = {},
) : ViewModel() {

  private val engine = AntiFormulaProbabilityEngine(
    prizes = prizes,
    random = random,
  )

  private val variants = RouletteCatalogDefaults.specialTitleVariants
  private var slotAVariantIndex: Int = 0
  private var slotBVariantIndex: Int = 1.coerceAtMost((variants.size - 1).coerceAtLeast(0))
  private var lastWonVariantIndex: Int = -1
  private var rotationStep: Int = 0
  private var nextNonce = 1L

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<RouletteUiState> = _uiState.asStateFlow()

  private fun createInitialState(): RouletteUiState {
    val saved = localStore?.load()
    if (saved != null) {
      slotAVariantIndex = saved.slotAVariantIndex.coerceIn(0, variants.lastIndex)
      slotBVariantIndex = saved.slotBVariantIndex.coerceIn(0, variants.lastIndex)
      if (slotBVariantIndex == slotAVariantIndex && variants.size > 1) {
        slotBVariantIndex = (slotAVariantIndex + 1) % variants.size
      }
      lastWonVariantIndex = saved.lastWonVariantIndex
    }
    val mergedUnlocked = ((saved?.unlockedExclusiveTitles ?: emptyList()) + initialUnlockedProfileTitles)
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .distinct()
    val pending = (saved?.pendingClaimTitles ?: emptyList())
      .map { it.trim() }
      .filter { it.isNotBlank() && it !in mergedUnlocked }
      .distinct()
    val hydratedMissions = RouletteCatalogDefaults.defaultSpinMissions().map { m ->
      val savedPair = saved?.spinMissionsProgress?.get(m.id)
      if (savedPair != null) {
        m.copy(currentSpins = savedPair.first, isCompleted = savedPair.second)
      } else {
        m
      }
    }
    val activeSnapshots = applyTitleVariantsToSnapshots(engine.currentSnapshots())

    return RouletteUiState(
      activeView = RouletteDisplayView.CLASSIC,
      totalSpins = saved?.totalSpins ?: 0,
      accumulatedXp = saved?.accumulatedXp ?: 0L,
      bonusLevelsWon = saved?.bonusLevelsWon ?: 0,
      floorChargePercent = saved?.floorChargePercent ?: 0,
      floorBonusTriggers = saved?.floorBonusTriggers ?: 0,
      unlockedExclusiveTitles = mergedUnlocked,
      pendingClaimTitles = pending,
      equippedProfileTitle = initialEquippedTitle,
      activeTitleVariants = currentActiveVariantTitles(),
      mainProfileLevel = initialMainLevel,
      mainProfileXp = initialMainXp,
      prizeProbabilities = activeSnapshots,
      spinMissions = hydratedMissions,
    )
  }

  fun syncProfileSnapshot(
    mainLevel: Long,
    mainXp: Long,
    equippedTitle: String,
    profileUnlockedTitles: List<String>,
  ) {
    _uiState.update { current ->
      val mergedUnlocked = (current.unlockedExclusiveTitles + profileUnlockedTitles)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
      val remainingPending = current.pendingClaimTitles.filter { it !in mergedUnlocked }
      current.copy(
        mainProfileLevel = mainLevel,
        mainProfileXp = mainXp,
        equippedProfileTitle = equippedTitle,
        unlockedExclusiveTitles = mergedUnlocked,
        pendingClaimTitles = remainingPending,
      )
    }
  }

  fun switchView(targetView: RouletteDisplayView) {
    _uiState.update { it.copy(activeView = targetView) }
  }

  fun toggleDebugPanel() {
    _uiState.update { it.copy(debugPanelExpanded = !it.debugPanelExpanded) }
  }

  /**
   * Inicia una tirada animada en la vista activa (Clásica u Horizontal).
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
   * Simula N tiradas seguidas (por ejemplo 10 o 50) ejecutando el motor anti-fórmula
   * paso a paso, sumando XP y niveles al perfil principal y rotando las 10 variantes de título.
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

  fun claimPendingTitle(title: String, equipAfterClaim: Boolean = false) {
    val clean = title.trim()
    if (clean.isBlank()) return
    _uiState.update { current ->
      val nextPending = current.pendingClaimTitles.filterNot { it == clean }
      val nextUnlocked = if (clean in current.unlockedExclusiveTitles) {
        current.unlockedExclusiveTitles
      } else {
        current.unlockedExclusiveTitles + clean
      }
      val nextEquipped = if (equipAfterClaim) clean else current.equippedProfileTitle
      val nextState = current.copy(
        pendingClaimTitles = nextPending,
        unlockedExclusiveTitles = nextUnlocked,
        equippedProfileTitle = nextEquipped,
      )
      persistState(nextState)
      nextState
    }
    onTitleClaimed(clean)
    if (equipAfterClaim) {
      onTitleEquipped(clean)
    }
  }

  fun claimAllPendingTitles() {
    val toClaim = _uiState.value.pendingClaimTitles.toList()
    if (toClaim.isEmpty()) return
    _uiState.update { current ->
      val nextUnlocked = (current.unlockedExclusiveTitles + toClaim).distinct()
      val nextState = current.copy(
        pendingClaimTitles = emptyList(),
        unlockedExclusiveTitles = nextUnlocked,
      )
      persistState(nextState)
      nextState
    }
    toClaim.forEach { onTitleClaimed(it) }
  }

  fun equipProfileTitle(title: String) {
    val clean = title.trim()
    val currentUnlocked = _uiState.value.unlockedExclusiveTitles
    if (clean.isNotBlank() && clean !in currentUnlocked) return
    _uiState.update { it.copy(equippedProfileTitle = clean) }
    onTitleEquipped(clean)
  }

  fun resetAll() {
    engine.reset()
    nextNonce = 1L
    slotAVariantIndex = 0
    slotBVariantIndex = 1.coerceAtMost((variants.size - 1).coerceAtLeast(0))
    lastWonVariantIndex = -1
    rotationStep = 0
    localStore?.clear()
    _uiState.update { current ->
      RouletteUiState(
        activeView = current.activeView,
        debugPanelExpanded = current.debugPanelExpanded,
        prizeProbabilities = applyTitleVariantsToSnapshots(engine.currentSnapshots()),
        spinMissions = RouletteCatalogDefaults.defaultSpinMissions(),
        activeTitleVariants = currentActiveVariantTitles(),
        equippedProfileTitle = current.equippedProfileTitle,
        mainProfileLevel = current.mainProfileLevel,
        mainProfileXp = current.mainProfileXp,
      )
    }
  }

  private fun applySpinOutcome(
    winnerIndex: Int,
    updatedSnapshots: List<PrizeProbabilitySnapshot>,
    winningProbPercent: Double,
    wasSimulatedBatch: Boolean,
  ) {
    val currentPrizeBeforeSwap = resolveCurrentPrize(prizes[winnerIndex])
    var earnedXpToCredit = 0L
    var earnedLevelsToCredit = 0

    _uiState.update { current ->
      val newTotalSpins = current.totalSpins + 1

      // Piso equilibrado: los premios de XP baja cargan el medidor de impulso; al llegar a 100% otorgan +50 XP extra
      var charge = current.floorChargePercent + currentPrizeBeforeSwap.floorChargeBoost
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

      // Títulos exclusivos de ruleta: reclamables cuando se obtienen (sin duplicar si ya está pendiente o reclamado)
      val updatedPendingTitles = current.pendingClaimTitles.toMutableList()
      var duplicateTitleCompensation = 0L
      var wonClaimableTitle: String? = null
      var wonVariantIdx: Int? = null
      if (currentPrizeBeforeSwap.category == RoulettePrizeCategory.EXCLUSIVE_TITLE &&
        !currentPrizeBeforeSwap.unlockedTitle.isNullOrBlank()
      ) {
        val wonTitle = currentPrizeBeforeSwap.unlockedTitle
        wonVariantIdx = variants.indexOfFirst { it.title == wonTitle }.takeIf { it >= 0 }
        val alreadyClaimed = wonTitle in current.unlockedExclusiveTitles
        val alreadyPending = wonTitle in updatedPendingTitles
        if (!alreadyClaimed && !alreadyPending) {
          updatedPendingTitles.add(0, wonTitle)
          wonClaimableTitle = wonTitle
        } else {
          duplicateTitleCompensation = currentPrizeBeforeSwap.xpAmount
        }
      }

      // Misiones aleatorias y misiones de alto riesgo generadas
      val updatedRandomMissions = current.activeRandomMissions.toMutableList()
      if (currentPrizeBeforeSwap.category == RoulettePrizeCategory.RANDOM_MISSION) {
        val pool = RouletteCatalogDefaults.randomMissionPool
        val picked = pool[(newTotalSpins + winnerIndex) % pool.size]
        updatedRandomMissions.add(0, picked)
      }
      val updatedHardMissions = current.activeHardMissions.toMutableList()
      if (currentPrizeBeforeSwap.category == RoulettePrizeCategory.HARD_MISSION) {
        val pool = RouletteCatalogDefaults.hardMissionPool
        val picked = pool[(newTotalSpins + winnerIndex) % pool.size]
        updatedHardMissions.add(0, picked)
      }

      val basePrizeXp = if (currentPrizeBeforeSwap.category == RoulettePrizeCategory.EXCLUSIVE_TITLE &&
        duplicateTitleCompensation == 0L
      ) {
        0L
      } else {
        currentPrizeBeforeSwap.xpAmount
      }
      val totalXpGain = basePrizeXp + floorBonusXp + missionXpEarned
      earnedXpToCredit = totalXpGain
      earnedLevelsToCredit = currentPrizeBeforeSwap.freeLevels

      val record = RouletteSpinRecord(
        spinNumber = newTotalSpins,
        prize = currentPrizeBeforeSwap,
        bonusFloorXp = floorBonusXp,
        viewUsed = current.activeView,
        wasSimulatedBatch = wasSimulatedBatch,
        timestampMs = System.currentTimeMillis(),
        winningProbabilityPercent = winningProbPercent,
        claimableTitle = wonClaimableTitle,
        totalXpGranted = totalXpGain,
      )

      // Intercambiar variantes de título tras cada tirada para que siempre roten entre las 10 variantes
      val ownedOrPending = (current.unlockedExclusiveTitles + updatedPendingTitles).toSet()
      advanceTitleVariantsAfterSpin(
        wonVariantIndex = wonVariantIdx,
        ownedOrPendingTitles = ownedOrPending,
      )
      val rotatedSnapshots = applyTitleVariantsToSnapshots(updatedSnapshots)

      val nextState = current.copy(
        isSpinning = false,
        totalSpins = newTotalSpins,
        accumulatedXp = current.accumulatedXp + totalXpGain,
        bonusLevelsWon = current.bonusLevelsWon + currentPrizeBeforeSwap.freeLevels,
        floorChargePercent = charge,
        floorBonusTriggers = floorTriggers,
        pendingClaimTitles = updatedPendingTitles,
        activeTitleVariants = currentActiveVariantTitles(),
        activeRandomMissions = updatedRandomMissions.take(8),
        activeHardMissions = updatedHardMissions.take(8),
        prizeProbabilities = rotatedSnapshots,
        spinMissions = updatedMissions,
        spinHistory = (listOf(record) + current.spinHistory).take(60),
        lastWonRecord = record,
        lastCompletedSpinMissionTitle = newlyCompletedMissionTitle ?: current.lastCompletedSpinMissionTitle,
      )
      persistState(nextState)
      nextState
    }

    if (earnedXpToCredit > 0L || earnedLevelsToCredit > 0) {
      onRewardEarned(earnedXpToCredit, earnedLevelsToCredit)
    }
  }

  private fun advanceTitleVariantsAfterSpin(
    wonVariantIndex: Int?,
    ownedOrPendingTitles: Set<String>,
  ) {
    if (variants.size < 2) return
    if (wonVariantIndex != null) {
      lastWonVariantIndex = wonVariantIndex
    }
    val prevA = slotAVariantIndex
    val prevB = slotBVariantIndex
    rotationStep += 1

    val total = variants.size
    val orderedIndices = (0 until total).map { offset -> (rotationStep + offset) % total }

    val preferredUnowned = orderedIndices.filter { idx ->
      idx != prevA &&
        idx != prevB &&
        idx != lastWonVariantIndex &&
        variants[idx].title !in ownedOrPendingTitles
    }
    val fallbackAnyFresh = orderedIndices.filter { idx ->
      idx != prevA && idx != prevB && idx != lastWonVariantIndex
    }

    val nextA = preferredUnowned.firstOrNull()
      ?: fallbackAnyFresh.firstOrNull()
      ?: ((prevA + 2) % total)

    val nextB = preferredUnowned.firstOrNull { it != nextA }
      ?: fallbackAnyFresh.firstOrNull { it != nextA }
      ?: orderedIndices.firstOrNull { it != nextA && it != lastWonVariantIndex }
      ?: ((nextA + 1) % total)

    slotAVariantIndex = nextA
    slotBVariantIndex = nextB
  }

  private fun resolveCurrentPrize(basePrize: RoulettePrize): RoulettePrize {
    if (variants.isEmpty()) return basePrize
    return when (basePrize.id) {
      RouletteCatalogDefaults.TITLE_SLOT_A_ID -> RouletteCatalogDefaults.prizeForTitleSlot(
        slotId = basePrize.id,
        baseWeight = basePrize.baseWeight,
        variant = variants[slotAVariantIndex.coerceIn(0, variants.lastIndex)],
      )

      RouletteCatalogDefaults.TITLE_SLOT_B_ID -> RouletteCatalogDefaults.prizeForTitleSlot(
        slotId = basePrize.id,
        baseWeight = basePrize.baseWeight,
        variant = variants[slotBVariantIndex.coerceIn(0, variants.lastIndex)],
      )

      else -> basePrize
    }
  }

  private fun applyTitleVariantsToSnapshots(
    snapshots: List<PrizeProbabilitySnapshot>,
  ): List<PrizeProbabilitySnapshot> = snapshots.map { snap ->
    val resolved = resolveCurrentPrize(snap.prize)
    if (resolved == snap.prize) snap else snap.copy(prize = resolved)
  }

  private fun currentActiveVariantTitles(): List<String> = buildList {
    variants.getOrNull(slotAVariantIndex)?.let { add(it.title) }
    variants.getOrNull(slotBVariantIndex)?.let { add(it.title) }
  }

  private fun persistState(state: RouletteUiState) {
    localStore?.save(
      RoulettePersistedSnapshot(
        totalSpins = state.totalSpins,
        accumulatedXp = state.accumulatedXp,
        bonusLevelsWon = state.bonusLevelsWon,
        floorChargePercent = state.floorChargePercent,
        floorBonusTriggers = state.floorBonusTriggers,
        slotAVariantIndex = slotAVariantIndex,
        slotBVariantIndex = slotBVariantIndex,
        lastWonVariantIndex = lastWonVariantIndex,
        unlockedExclusiveTitles = state.unlockedExclusiveTitles,
        pendingClaimTitles = state.pendingClaimTitles,
        spinMissionsProgress = state.spinMissions.associate { it.id to (it.currentSpins to it.isCompleted) },
      )
    )
  }

  private companion object {
    const val FLOOR_OVERCHARGE_BONUS_XP = 50L
  }
}
