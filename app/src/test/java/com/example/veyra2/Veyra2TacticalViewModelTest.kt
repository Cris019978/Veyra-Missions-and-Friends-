package com.example.veyra2

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class Veyra2TacticalViewModelTest {

  private val dispatcher = StandardTestDispatcher()
  private val vmStore = ViewModelStore()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    vmStore.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun `deferred drawing keeps missions in memory with zero drawn markers while map is closed`() {
    val vm = Veyra2TacticalViewModel(random = Random(10L))
    vmStore.put("deferred_test", vm)

    vm.setMapMode(Veyra2MapMode.CLOSED_MINIMAP)
    val closedState = vm.uiState.value
    assertTrue("Textual missions must remain in memory when closed", closedState.missionsInMemory.isNotEmpty())
    assertEquals(0, closedState.drawnMarkersCount)
    assertTrue(closedState.drawnMissions.isEmpty())

    vm.setMapMode(Veyra2MapMode.OPEN_50)
    val open50 = vm.uiState.value
    assertTrue("Vista 50 must draw nearby missions once map opens", open50.drawnMarkersCount in 1..open50.missionsInMemory.size)
    assertTrue(open50.drawnMissions.all { m ->
      hypot(m.position.xMeters, m.position.yMeters) <= 300f || m.isPinnedTarget
    })

    vm.setMapMode(Veyra2MapMode.OPEN_100)
    val open100 = vm.uiState.value
    assertTrue("Vista 100 must draw more markers than Vista 50", open100.drawnMarkersCount >= open50.drawnMarkersCount)
  }

  @Test
  fun `distance recycling at 2km removes untouched and claimed physical markers without erasing rewards`() {
    var syncedXp = 0L
    var syncedLevels = 0
    val syncedTitles = mutableListOf<String>()

    val vm = Veyra2TacticalViewModel(
      random = Random(42L),
      onSyncRewardsToProfile = { xp, levels, title ->
        syncedXp += xp
        syncedLevels += levels
        if (title != null) syncedTitles.add(title)
      },
    )
    vmStore.put("recycle_test", vm)

    // Spawn an untouched mission nearby and a claimed high-difficulty mission
    val untouchedId = vm.spawnDebugMission(
      distanceMeters = 120f,
      touched = false,
      claimedImmediately = false,
      difficulty = Veyra2MissionDifficulty.NORMAL,
    )
    val claimedId = vm.spawnDebugMission(
      distanceMeters = 150f,
      touched = true,
      claimedImmediately = true,
      difficulty = Veyra2MissionDifficulty.HIGH_DIFFICULTY,
    )

    // Claiming twice must be rejected
    assertFalse("Duplicate claim must be prevented", vm.claimMissionReward(claimedId))

    val beforeMove = vm.uiState.value
    assertEquals(540L, beforeMove.rewardsLedger.totalXp)
    assertTrue("Explorador de Élite" in beforeMove.rewardsLedger.unlockedTitles)
    assertTrue(claimedId in beforeMove.rewardsLedger.claimedMissionIds)
    assertTrue(beforeMove.missionsInMemory.any { it.id == untouchedId })
    assertTrue(beforeMove.missionsInMemory.any { it.id == claimedId })

    // Move player > 2.3 km away so both missions exceed 2000m threshold
    vm.movePlayerByMeters(dxMeters = 2350f, dyMeters = 0f)

    val afterMove = vm.uiState.value
    assertFalse("Untouched mission >= 2km must be physically deleted", afterMove.missionsInMemory.any { it.id == untouchedId })
    assertFalse("Claimed mission >= 2km must also be physically deleted from map", afterMove.missionsInMemory.any { it.id == claimedId })
    assertFalse(afterMove.drawnMissions.any { it.id == claimedId })

    // Immutable Rewards Ledger MUST be preserved 100%
    assertEquals(540L, afterMove.rewardsLedger.totalXp)
    assertEquals(1, afterMove.rewardsLedger.completedMissionsCount)
    assertTrue("Explorador de Élite" in afterMove.rewardsLedger.unlockedTitles)
    assertTrue(claimedId in afterMove.rewardsLedger.claimedMissionIds)
    assertTrue(afterMove.rewardsLedger.recycledUntouchedCount >= 1)
    assertTrue(afterMove.rewardsLedger.recycledTouchedOrClaimedCount >= 1)
    assertEquals(540L, syncedXp)
    assertEquals(listOf("Explorador de Élite"), syncedTitles)
  }

  @Test
  fun `walking spawns new missions while capping memory and canvas items`() {
    val vm = Veyra2TacticalViewModel(random = Random(99L))
    vmStore.put("spawn_cap_test", vm)

    repeat(15) {
      vm.movePlayerByMeters(dxMeters = 320f, dyMeters = 90f)
    }

    val state = vm.uiState.value
    assertTrue(state.totalWalkedMeters > 4000f)
    assertTrue(state.missionsInMemory.size <= Veyra2TacticalUiState.MAX_MEMORY_MISSIONS)
    assertTrue(state.drawnMissions.size <= Veyra2TacticalUiState.MAX_DRAWN_CANVAS_MISSIONS)
  }

  @Test
  fun `landscape aiming preserves targets and routes across mode transitions and supports pan zoom`() {
    val vm = Veyra2TacticalViewModel(random = Random(7L))
    vmStore.put("aiming_test", vm)

    vm.enterLandscapeAimingMode()
    assertTrue(vm.uiState.value.mapMode.isLandscapeAiming)

    vm.panCameraByMeters(140f, -95f)
    vm.applyAimZoomMultiplier(1.5f)
    vm.markTargetAtReticle()
    vm.addRouteWaypointAtReticle()

    vm.panCameraByMeters(120f, 60f)
    vm.addRouteWaypointAtReticle()

    // Switch back to portrait scale and re-enter landscape aiming -> zero lost marks/routes
    vm.setMapMode(Veyra2MapMode.OPEN_75)
    vm.enterLandscapeAimingMode()

    val restored = vm.uiState.value
    assertEquals(1, restored.markedTargets.size)
    assertEquals(2, restored.routePoints.size)
    assertEquals(260f, restored.cameraCenter.xMeters, 0.01f)
    assertEquals(-35f, restored.cameraCenter.yMeters, 0.01f)
    assertTrue(restored.aimZoomFactor > 1.5f)
  }

  @Test
  fun `adaptive refresh rate boosts on gesture and cools down cleanly to idle`() = runTest {
    val vm = Veyra2TacticalViewModel(random = Random(5L))
    vmStore.put("refresh_test", vm)

    vm.setGestureBoostActive(true)
    assertEquals(AdaptiveRefreshTier.GESTURE_BOOST_120HZ, vm.uiState.value.refreshTier)
    assertEquals(120f, vm.uiState.value.refreshTier.targetHz, 0.01f)

    vm.setGestureBoostActive(false)
    advanceTimeBy(400L)
    assertEquals(AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ, vm.uiState.value.refreshTier)

    advanceTimeBy(900L)
    assertEquals(AdaptiveRefreshTier.IDLE_LOW_30HZ, vm.uiState.value.refreshTier)
    assertEquals(30f, vm.uiState.value.refreshTier.targetHz, 0.01f)
  }
}
