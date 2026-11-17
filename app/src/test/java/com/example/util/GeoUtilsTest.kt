package com.example.util

import com.example.data.remote.OsmSafePlaceFinder
import com.example.data.remote.SafePublicSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
  fun generateRandomMissions_returnsAtLeast25DistinctRandomizedMissionsAnd20VariantsWithPuzzleTimers() {
    val seedA = GeoUtils.generateRandomMissions(19.4326, -99.1332, seed = 1001L)
    val seedB = GeoUtils.generateRandomMissions(19.4326, -99.1332, seed = 9999L)

    assertTrue("Expected >= 25 missions, got ${seedA.size}", seedA.size >= 25)
    assertTrue("Expected >= 20 variants, got ${GeoUtils.distinctMissionVariantsCount()}", GeoUtils.distinctMissionVariantsCount() >= 20)
    assertEquals(
      setOf("eco", "explore", "social", "challenge", "fitness", "creative", "puzzle"),
      seedA.map { it.category }.toSet(),
    )
    assertEquals(
      setOf("photo", "wires", "puzzle", "memory"),
      seedA.map { it.missionType }.toSet(),
    )
    val timedPuzzles = seedA.filter { it.category == "puzzle" && it.timeLimitSeconds == 60 }
    assertTrue("Expected timed 1-min puzzle missions, got ${timedPuzzles.size}", timedPuzzles.size >= 3)
    val uniqueIcons = seedA.map { it.iconKey }.toSet()
    assertTrue("Expected >= 25 unique icons, got ${uniqueIcons.size}", uniqueIcons.size >= 25)
    assertTrue(seedA.all { it.isActive && it.xpReward >= 100L && it.creatorName == "Veyra" && it.isSafePublicVerified })

    // Verify coordinates actually randomize between seeds
    assertNotEquals(seedA.first().latitude, seedB.first().latitude, 0.000001)
    assertNotEquals(seedA.first().longitude, seedB.first().longitude, 0.000001)
  }

  @Test
  fun osmSafePlaceFinder_excludesPrivateAndDangerousLocationsAndAcceptsPublicParks() {
    assertTrue(OsmSafePlaceFinder.isSafePublicSpot("Parque México", listOf("park"), "OPERATIONAL"))
    assertTrue(OsmSafePlaceFinder.isSafePublicSpot("Biblioteca Central", listOf("library"), "OPERATIONAL"))
    assertTrue(OsmSafePlaceFinder.isSafePublicSpot("Museo de Arte", listOf("museum"), "OPERATIONAL"))

    assertFalse(OsmSafePlaceFinder.isSafePublicSpot("Hospital General", listOf("hospital"), "OPERATIONAL"))
    assertFalse(OsmSafePlaceFinder.isSafePublicSpot("Estación de Policía", listOf("police"), "OPERATIONAL"))
    assertFalse(OsmSafePlaceFinder.isSafePublicSpot("Gasolinera Norte", listOf("fuel"), "OPERATIONAL"))
    assertFalse(OsmSafePlaceFinder.isSafePublicSpot("Condominio Privado", listOf("park"), "OPERATIONAL"))
    assertFalse(OsmSafePlaceFinder.isSafePublicSpot("Parque Cerrado", listOf("park"), "CLOSED_TEMPORARILY"))
  }

  @Test
  fun generateRandomMissions_anchorsOntoOsmSafePublicSpotsWhenAvailable() {
    val safeAnchors = listOf(
      SafePublicSpot("Parque Lincoln", 19.4301, -99.1972, "park", true),
      SafePublicSpot("Plaza Río de Janeiro", 19.4216, -99.1604, "plaza", true),
    )
    val missions = GeoUtils.generateRandomMissions(
      baseLat = 19.4326,
      baseLon = -99.1332,
      seed = 42L,
      safeAnchors = safeAnchors,
    )
    assertTrue(missions.any { it.placeLabel == "Parque Lincoln" || it.placeLabel == "Plaza Río de Janeiro" })
  }
}
