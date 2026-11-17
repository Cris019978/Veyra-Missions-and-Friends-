package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
  fun cardinalDirectionEs_mapsDirectionsAccurately() {
    assertEquals("Norte", GeoUtils.cardinalDirectionEs(0f))
    assertEquals("Noreste", GeoUtils.cardinalDirectionEs(45f))
    assertEquals("Este", GeoUtils.cardinalDirectionEs(90f))
    assertEquals("Sur", GeoUtils.cardinalDirectionEs(180f))
    assertEquals("Oeste", GeoUtils.cardinalDirectionEs(270f))
  }

  @Test
  fun generateRandomMissions_returnsAtLeast25DistinctRandomizedMissionsWithVariedTypesAndIcons() {
    val seedA = GeoUtils.generateRandomMissions(19.4326, -99.1332, seed = 1001L)
    val seedB = GeoUtils.generateRandomMissions(19.4326, -99.1332, seed = 9999L)

    assertTrue("Expected >= 25 missions, got ${seedA.size}", seedA.size >= 25)
    assertEquals(
      setOf("eco", "explore", "social", "challenge", "fitness", "creative"),
      seedA.map { it.category }.toSet(),
    )
    assertEquals(
      setOf("photo", "wires", "puzzle"),
      seedA.map { it.missionType }.toSet(),
    )
    val uniqueIcons = seedA.map { it.iconKey }.toSet()
    assertTrue("Expected >= 25 unique icons, got ${uniqueIcons.size}", uniqueIcons.size >= 25)
    assertTrue(seedA.all { it.isActive && it.xpReward >= 100L && it.creatorName == "Veyra" })

    // Verify coordinates actually randomize between seeds
    assertNotEquals(seedA.first().latitude, seedB.first().latitude, 0.000001)
    assertNotEquals(seedA.first().longitude, seedB.first().longitude, 0.000001)
  }
}
