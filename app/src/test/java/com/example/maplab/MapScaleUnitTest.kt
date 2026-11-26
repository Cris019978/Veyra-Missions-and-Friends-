package com.example.maplab

import androidx.lifecycle.ViewModelStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MapScaleUnitTest {

  private val vmStore = ViewModelStore()

  @After
  fun cleanup() {
    vmStore.clear()
  }

  @Test
  fun `horizontal slide between 50 and 75 interpolates smoothly and minimizes toggle button`() {
    val manager = MapScaleStateManager(random = Random(123L))
    vmStore.put("map_gesture_test", manager)

    assertEquals(MapViewScale.SCALE_75, manager.uiState.value.activeScale)
    assertTrue(manager.uiState.value.isToggleButtonVisible)

    // Begin sliding horizontally from 75 toward 50
    manager.onDragStarted()
    manager.onDragDelta(dx = 40f, dy = 0f)

    val midSlide = manager.uiState.value
    assertTrue(midSlide.isSlidingBetween50And75)
    assertTrue(midSlide.blend50To75 in 0.5f..0.9f)
    assertFalse("Toggle button must hide/minimize during 50<->75 gesture", midSlide.isToggleButtonVisible)
    assertEquals(ToggleButtonVisibilityMode.MINIMIZED_DURING_GESTURE, midSlide.buttonVisibilityMode)

    // Continue sliding past threshold and release -> settles on Vista 50
    manager.onDragDelta(dx = 120f, dy = 0f)
    manager.onDragEnded()

    val afterSlideTo50 = manager.uiState.value
    assertEquals(MapViewScale.SCALE_50, afterSlideTo50.activeScale)
    assertEquals(180, afterSlideTo50.effectiveViewRadiusMeters)
    assertFalse(afterSlideTo50.isToggleButtonVisible)

    // Slide back in opposite direction -> returns smoothly to Vista 75
    manager.onDragStarted()
    manager.onDragDelta(dx = -170f, dy = 0f)
    manager.onDragEnded()

    val afterSlideTo75 = manager.uiState.value
    assertEquals(MapViewScale.SCALE_75, afterSlideTo75.activeScale)
    assertEquals(450, afterSlideTo75.effectiveViewRadiusMeters)
  }

  @Test
  fun `pull down beyond threshold activates Vista 100 and restores toggle button`() {
    val manager = MapScaleStateManager(random = Random(456L))
    vmStore.put("map_pull_down_test", manager)

    // First minimize the button via a 50<->75 slide
    manager.onDragStarted()
    manager.onDragDelta(dx = 120f, dy = 0f)
    manager.onDragEnded()
    assertFalse(manager.uiState.value.isToggleButtonVisible)

    // Now pull downward past threshold to trigger Vista 100
    manager.onDragStarted()
    manager.onDragDelta(dx = 0f, dy = 70f)
    assertTrue(manager.uiState.value.pullDown100Progress > 0.4f)
    assertEquals(MapViewScale.SCALE_50, manager.uiState.value.activeScale)

    manager.onDragDelta(dx = 0f, dy = 80f) // total dy = 150 > 135
    val at100 = manager.uiState.value
    assertEquals(MapViewScale.SCALE_100, at100.activeScale)
    assertEquals(1200, at100.effectiveViewRadiusMeters)
    assertTrue("Toggle button must reappear on Vista 100 activation", at100.isToggleButtonVisible)
    assertEquals(ToggleButtonVisibilityMode.VISIBLE_PRIMARY, at100.buttonVisibilityMode)

    manager.onDragEnded()
    assertTrue(manager.uiState.value.isToggleButtonVisible)

    // Tapping toggle button cycles comfortably from 100 -> 50 -> 75 -> 100
    manager.cycleScaleFromButton()
    assertEquals(MapViewScale.SCALE_50, manager.uiState.value.activeScale)
    manager.cycleScaleFromButton()
    assertEquals(MapViewScale.SCALE_75, manager.uiState.value.activeScale)
  }

  @Test
  fun `debug controls simulate player movement, spawn near or hard missions, and reset cleanly`() {
    val manager = MapScaleStateManager(random = Random(789L))
    vmStore.put("map_debug_test", manager)
    val initialCount = manager.uiState.value.missions.size

    manager.forceScale(MapViewScale.SCALE_50)
    assertEquals(MapViewScale.SCALE_50, manager.uiState.value.activeScale)

    manager.simulatePlayerStep(dxMeters = 45f, dyMeters = -45f)
    assertEquals(45f, manager.uiState.value.playerPosition.xMeters, 0.01f)
    assertEquals(-45f, manager.uiState.value.playerPosition.yMeters, 0.01f)

    manager.spawnNearMission()
    manager.spawnHardDistantMission()
    val updated = manager.uiState.value
    assertEquals(initialCount + 2, updated.missions.size)
    assertEquals(MapMissionDifficulty.HIGH_DIFFICULTY, updated.missions.first().difficulty)

    manager.resetAll()
    val resetState = manager.uiState.value
    assertEquals(MapViewScale.SCALE_75, resetState.activeScale)
    assertEquals(0f, resetState.playerPosition.xMeters, 0.01f)
    assertEquals(0f, resetState.playerPosition.yMeters, 0.01f)
    assertEquals(initialCount, resetState.missions.size)
    assertTrue(resetState.isToggleButtonVisible)
  }
}
