package com.example.roulette

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class RouletteEngineUnitTest {

  private val testDispatcher = UnconfinedTestDispatcher()
  private val vmStore = ViewModelStore()

  @Before
  fun setupMainDispatcher() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDownMainDispatcher() {
    vmStore.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun `anti formula engine keeps sum at 1 and swings high prob stronger than contained rares`() {
    val engine = AntiFormulaProbabilityEngine(random = Random(42L))
    val initialSnapshots = engine.currentSnapshots()
    val dominantIdx = initialSnapshots.indices.maxByOrNull { initialSnapshots[it].probability }!!
    val rarestIdx = initialSnapshots.indices.minByOrNull { initialSnapshots[it].probability }!!

    val dominantSeries = mutableListOf(initialSnapshots[dominantIdx].probability)
    val rarestSeries = mutableListOf(initialSnapshots[rarestIdx].probability)

    repeat(60) {
      val (winner, snapshots) = engine.rollAndRecalibrate()
      assertTrue(winner in snapshots.indices)
      val sum = snapshots.sumOf { it.probability }
      assertTrue("Probabilities must sum to 1.0 (was $sum)", abs(sum - 1.0) < 1e-6)
      assertTrue(snapshots.all { it.currentWeight > 0.0 && it.probability > 0.0 })

      dominantSeries.add(snapshots[dominantIdx].probability)
      rarestSeries.add(snapshots[rarestIdx].probability)
    }

    val dominantSpread = (dominantSeries.max() - dominantSeries.min()) * 100.0
    val rarestSpread = (rarestSeries.max() - rarestSeries.min()) * 100.0

    assertTrue(
      "Dominant item should experience strong swings (spread=$dominantSpread%)",
      dominantSpread > 5.0,
    )
    assertTrue(
      "Rarest item should remain softly contained relative to dominant (rarest=$rarestSpread%, dom=$dominantSpread%)",
      rarestSpread < dominantSpread * 0.25 && rarestSpread < 1.5,
    )

    engine.reset(newSeed = 42L)
    val resetSum = engine.currentSnapshots().sumOf { it.probability }
    assertTrue(abs(resetSum - 1.0) < 1e-6)
  }

  @Test
  fun `state manager batch simulation, view switching, missions, floor charge bonus, and reset`() {
    val manager = RouletteStateManager(random = Random(77L))
    vmStore.put("roulette_batch_test", manager)

    assertEquals(RouletteDisplayView.CLASSIC, manager.uiState.value.activeView)
    manager.switchView(RouletteDisplayView.HORIZONTAL)
    assertEquals(RouletteDisplayView.HORIZONTAL, manager.uiState.value.activeView)

    manager.simulateBatchSpins(50)
    val after50 = manager.uiState.value

    assertEquals(50, after50.totalSpins)
    assertEquals(50, after50.spinHistory.size)
    assertTrue(after50.accumulatedXp > 0L)
    assertEquals(RouletteDisplayView.HORIZONTAL, after50.activeView)

    // 4 of the 5 escalating spin missions (3, 10, 25, 50) must be completed; 100 remains pending
    val completedTargets = after50.spinMissions.filter { it.isCompleted }.map { it.targetSpins }
    assertEquals(listOf(3, 10, 25, 50), completedTargets)
    assertFalse(after50.spinMissions.last().isCompleted)

    // Switch back to Classic mid-session without losing state
    manager.switchView(RouletteDisplayView.CLASSIC)
    assertEquals(50, manager.uiState.value.totalSpins)

    manager.resetAll()
    val resetState = manager.uiState.value
    assertEquals(0, resetState.totalSpins)
    assertEquals(0L, resetState.accumulatedXp)
    assertTrue(resetState.spinHistory.isEmpty())
    assertTrue(resetState.spinMissions.none { it.isCompleted })
  }
}
