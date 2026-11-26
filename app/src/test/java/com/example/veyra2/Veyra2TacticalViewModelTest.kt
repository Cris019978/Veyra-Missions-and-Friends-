package com.example.veyra2

import androidx.lifecycle.ViewModelStore
import com.example.data.model.Mission
import com.example.data.model.MissionWithDistance
import com.example.util.GeoUtils
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

  @Test
  fun `syncMainMapMissions mirrors active missions, prunes 2km items, and synchronizes claim pin and walk`() {
    val claimedIds = mutableListOf<String>()
    val pinnedIds = mutableListOf<String>()
    val walkDeltas = mutableListOf<Pair<Float, Float>>()

    val vm = Veyra2TacticalViewModel(
      random = Random(123L),
      onClaimMainMission = { claimedIds.add(it) },
      onPinMainMission = { pinnedIds.add(it) },
      onSimulatedWalkGpsDelta = { dx, dy -> walkDeltas.add(dx to dy) },
    )
    vmStore.put("sync_main_test", vm)

    val playerLat = 19.4326
    val playerLon = -99.1332
    val (nearLat, nearLon) = GeoUtils.destinationPoint(playerLat, playerLon, 18.0, 90.0)
    val (midLat, midLon) = GeoUtils.destinationPoint(playerLat, playerLon, 480.0, 0.0)
    val (farLat, farLon) = GeoUtils.destinationPoint(playerLat, playerLon, 2250.0, 180.0)

    val syncedInput = listOf(
      MissionWithDistance(
        mission = Mission(
          id = "main_near_1",
          title = "Misión Cercana",
          description = "Plaza pública",
          xpReward = 150L,
          latitude = nearLat,
          longitude = nearLon,
        ),
        distanceMeters = 18f,
        bearingDegrees = 90f,
        isCompletedByMe = false,
      ),
      MissionWithDistance(
        mission = Mission(
          id = "main_mid_2",
          title = "Misión Media",
          description = "Corredor Norte",
          xpReward = 240L,
          latitude = midLat,
          longitude = midLon,
        ),
        distanceMeters = 480f,
        bearingDegrees = 0f,
        isCompletedByMe = true,
      ),
      MissionWithDistance(
        mission = Mission(
          id = "main_far_3",
          title = "Misión Lejana >2km",
          description = "Fuera de rango",
          xpReward = 300L,
          latitude = farLat,
          longitude = farLon,
        ),
        distanceMeters = 2250f,
        bearingDegrees = 180f,
        isCompletedByMe = true,
      ),
    )

    // Closed minimap: missions kept in memory (< 2km), zero markers drawn on Canvas
    vm.setMapMode(Veyra2MapMode.CLOSED_MINIMAP)
    vm.syncMainMapMissions(
      missions = syncedInput,
      playerLat = playerLat,
      playerLon = playerLon,
      activeAcceptedMissionId = "main_near_1",
    )

    val closedState = vm.uiState.value
    assertEquals(2, closedState.missionsInMemory.size)
    assertTrue(closedState.missionsInMemory.any { it.id == "main_near_1" && it.isPinnedTarget && it.isFromMainMap })
    assertTrue(closedState.missionsInMemory.any { it.id == "main_mid_2" && it.isClaimed })
    assertFalse("Main mission >= 2km must be pruned", closedState.missionsInMemory.any { it.id == "main_far_3" })
    assertTrue("Already completed main mission >= 2km stays in claimed ledger", "main_far_3" in closedState.rewardsLedger.claimedMissionIds)
    assertEquals(0, closedState.drawnMarkersCount)

    // Opening map draws the synced missions
    vm.enterLandscapeAimingMode()
    val openState = vm.uiState.value
    assertEquals(2, openState.drawnMarkersCount)

    // Pan reticle right onto main_near_1 (18m East) and mark target -> pins in main map
    vm.panCameraByMeters(18f, 0f)
    vm.markTargetAtReticle()
    assertEquals(listOf("main_near_1"), pinnedIds)

    // Claiming main_near_1 forwards claim to main ViewModel and blocks duplicate claims
    assertTrue(vm.claimMissionReward("main_near_1"))
    assertFalse(vm.claimMissionReward("main_near_1"))
    assertEquals(listOf("main_near_1"), claimedIds)
    assertEquals(150L, vm.uiState.value.rewardsLedger.totalXp)

    // Moving simulated player forwards delta to main map GPS walk handler
    vm.movePlayerByMeters(120f, -45f)
    assertEquals(listOf(120f to -45f), walkDeltas)
  }

  @Test
  fun `panCameraByMeters never teleports to 6000m when real GPS arrives far away or player walks past 6km`() {
    val vm = Veyra2TacticalViewModel(random = Random(321L))
    vmStore.put("reanchor_clamp_test", vm)

    // 1. Startup sync before GPS lock uses fallback CDMX coordinates
    vm.syncMainMapMissions(
      missions = emptyList(),
      playerLat = 19.4326,
      playerLon = -99.1332,
      isRealGps = false,
    )

    // 2. Real GPS arrives > 400 km away (Guadalajara) with a nearby mission at 120m East
    val realLat = 20.6597
    val realLon = -103.3496
    val (missionLat, missionLon) = GeoUtils.destinationPoint(realLat, realLon, 120.0, 90.0)
    vm.syncMainMapMissions(
      missions = listOf(
        MissionWithDistance(
          mission = Mission(
            id = "real_gps_near",
            title = "Plaza Real",
            xpReward = 200L,
            latitude = missionLat,
            longitude = missionLon,
          ),
          distanceMeters = 120f,
          bearingDegrees = 90f,
          isCompletedByMe = false,
        ),
      ),
      playerLat = realLat,
      playerLon = realLon,
      isRealGps = true,
    )

    val afterReanchor = vm.uiState.value
    assertEquals(0f, afterReanchor.playerPosition.xMeters, 1f)
    assertEquals(0f, afterReanchor.playerPosition.yMeters, 1f)
    assertEquals(0f, afterReanchor.cameraCenter.xMeters, 1f)
    assertEquals(0f, afterReanchor.cameraCenter.yMeters, 1f)
    assertEquals(1, afterReanchor.missionsInMemory.size)

    // Panning slightly must move smoothly from (0,0), NOT jump to (6000,6000)
    vm.panCameraByMeters(-26f, 15f)
    assertEquals(-26f, vm.uiState.value.cameraCenter.xMeters, 1f)
    assertEquals(15f, vm.uiState.value.cameraCenter.yMeters, 1f)

    // 3. Even if player walks 8 km away from origin, panning stays relative to playerPosition
    vm.recenterCameraOnPlayer()
    vm.movePlayerByMeters(8000f, 8000f)
    vm.panCameraByMeters(-26f, 0f)
    val farWalkState = vm.uiState.value
    assertEquals(farWalkState.playerPosition.xMeters - 26f, farWalkState.cameraCenter.xMeters, 1f)
    assertEquals(farWalkState.playerPosition.yMeters, farWalkState.cameraCenter.yMeters, 1f)
  }

  @Test
  fun `signalMission marks and pins mission for info and targeting without claiming rewards`() {
    val pinnedIds = mutableListOf<String>()
    val claimedIds = mutableListOf<String>()
    val vm = Veyra2TacticalViewModel(
      random = Random(77L),
      onClaimMainMission = { claimedIds.add(it) },
      onPinMainMission = { pinnedIds.add(it) },
    )
    vmStore.put("signal_only_test", vm)

    val playerLat = 19.4326
    val playerLon = -99.1332
    val (lat, lon) = GeoUtils.destinationPoint(playerLat, playerLon, 150.0, 45.0)
    vm.syncMainMapMissions(
      missions = listOf(
        MissionWithDistance(
          mission = Mission(
            id = "sig_m1",
            title = "Misión Señalizable",
            description = "Solo ver info y señalar",
            xpReward = 175L,
            latitude = lat,
            longitude = lon,
          ),
          distanceMeters = 150f,
          bearingDegrees = 45f,
          isCompletedByMe = false,
        ),
      ),
      playerLat = playerLat,
      playerLon = playerLon,
    )

    vm.inspectMission("sig_m1")
    vm.signalMission("sig_m1")
    vm.signalMission("sig_m1")

    val state = vm.uiState.value
    val signaled = state.missionsInMemory.first { it.id == "sig_m1" }
    assertTrue(signaled.isPinnedTarget)
    assertTrue(signaled.hasBeenInspected)
    assertFalse(signaled.isClaimed)
    assertEquals(1, state.markedTargets.count { it.linkedMissionId == "sig_m1" })
    assertEquals(0L, state.rewardsLedger.totalXp)
    assertTrue(claimedIds.isEmpty())
    assertEquals(listOf("sig_m1", "sig_m1"), pinnedIds)
  }
}
