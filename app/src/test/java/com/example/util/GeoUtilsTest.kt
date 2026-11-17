package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

  @Test
  fun distanceMeters_knownCoordinates_returnsExpectedRange() {
    val d = GeoUtils.distanceMeters(19.4326, -99.1332, 19.4336, -99.1332)
    assertTrue("Expected ~111m, got $d", d in 105f..118f)
  }

  @Test
  fun bearingDegrees_dueEast_returnsNinety() {
    val b = GeoUtils.bearingDegrees(0.0, 0.0, 0.0, 0.01)
    assertTrue("Expected ~90 deg, got $b", b in 89f..91f)
  }

  @Test
  fun formatDistance_metersAndKilometers_formatsCorrectly() {
    assertEquals("250 m", GeoUtils.formatDistance(250f))
    assertEquals("1.5 km", GeoUtils.formatDistance(1500f))
  }

  @Test
  fun builtInLocalMissions_returnsSixCategoryQuests() {
    val list = GeoUtils.builtInLocalMissions(19.4326, -99.1332)
    assertEquals(6, list.size)
    assertEquals(
      setOf("eco", "explore", "social", "challenge", "fitness", "creative"),
      list.map { it.category }.toSet(),
    )
    assertTrue(list.all { it.isActive && it.xpReward >= 100L && it.creatorName == "Veyra" })
  }
}
