package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Challenge
import com.example.data.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarQuestRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun saveAndObserveUserSettings_authenticatedOwner_succeeds() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = RadarQuestRepository(firestore, auth)

    val saveResult = withTimeout(TIMEOUT_MS) {
      repo.saveUserSettings(
        UserSettings(
          userId = uid,
          themeMode = "dark",
          paletteKey = "coral",
          radarStyle = "neon",
          cornerStyle = "pill",
          searchRadiusMeters = 3000L,
          animationTier = "low",
          notificationsEnabled = true,
          onboardingCompleted = true,
          playStyle = "explorer",
          favoriteCategories = listOf("explore", "fitness", "creative"),
          privacyComfort = "close_only",
          missionPace = "daily",
          gestureNavEnabled = true,
        )
      )
    }
    assertTrue(saveResult.isSuccess)

    val loaded = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeUserSettings(uid).first { it != null && it.onboardingCompleted }
    }
    assertNotNull(loaded)
    assertEquals("coral", loaded?.paletteKey)
    assertEquals("low", loaded?.animationTier)
    assertEquals("explorer", loaded?.playStyle)
    assertEquals(true, loaded?.gestureNavEnabled)
    assertEquals(listOf("explore", "fitness", "creative"), loaded?.favoriteCategories)
  }

  @Test
  fun playerProfileAndMissionWorkflow_authenticatedUsers_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = RadarQuestRepository(firestore, auth)

    val profileRes = withTimeout(TIMEOUT_MS) {
      repo.ensureOrUpdatePlayerProfile(
        username = "alice_radar",
        callsign = "AliceRadar",
        avatarIcon = "explore",
        avatarColorHex = "#00BFA5",
        avatarPhotoBase64 = "sample_avatar_b64",
        bannerPhotoBase64 = "sample_banner_b64",
        bannerPreset = "sunset",
        statusMessage = "En ruta",
        seekingHelp = true,
        isOnline = true,
        latitude = 37.422,
        longitude = -122.084,
      )
    }
    assertTrue(profileRes.isSuccess)

    val missionId = withTimeout(TIMEOUT_MS) {
      repo.createMission(
        creatorName = "AliceRadar",
        title = "Caminata urbana",
        description = "Camina hasta una plaza y confírmalo en foto",
        category = "fitness",
        xpReward = 150L,
        latitude = 37.422,
        longitude = -122.084,
      ).getOrThrow()
    }
    assertTrue(missionId.isNotEmpty())

    val bobUid = signInTestUser(BOB_EMAIL)
    val onlinePlayers = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeOnlinePlayers(bobUid).first { list -> list.any { it.userId == aliceUid } }
    }
    assertTrue(onlinePlayers.any { it.userId == aliceUid && it.seekingHelp })

    val completionId = withTimeout(TIMEOUT_MS) {
      repo.submitMissionCompletion(
        userName = "BobRunner",
        missionId = missionId,
        missionTitle = "Caminata urbana",
        category = "fitness",
        xpEarned = 150L,
        caption = "Foto lista!",
        photoThumbnailBase64 = "base64_sample_jpeg_payload_ok",
        latitude = 37.423,
        longitude = -122.085,
        isPublic = true,
      ).getOrThrow()
    }
    assertTrue(completionId.isNotEmpty())
  }

  @Test
  fun challengeJoinAndInteraction_twoPlayers_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repo = RadarQuestRepository(firestore, auth)

    val challengeId = withTimeout(TIMEOUT_MS) {
      repo.createChallenge(
        creatorName = "AliceRadar",
        mode = "duel",
        title = "Carrera fotográfica",
        missionId = "daily_park",
        xpStake = 200L,
      ).getOrThrow()
    }

    val bobUid = signInTestUser(BOB_EMAIL)
    val openList = withTimeout(FLOW_TIMEOUT_MS) {
      repo.observeOpenChallenges(bobUid).first { list -> list.any { it.id == challengeId } }
    }
    val target: Challenge = openList.first { it.id == challengeId }
    val joinRes = withTimeout(TIMEOUT_MS) { repo.joinChallenge(target, "BobRunner") }
    assertTrue(joinRes.isSuccess)

    val intRes = withTimeout(TIMEOUT_MS) {
      repo.sendInteraction(
        fromUserName = "BobRunner",
        toUserId = aliceUid,
        toUserName = "AliceRadar",
        type = "help",
        message = "¡Ánimo en el duelo!",
        xpBonus = 25L,
      )
    }
    assertTrue(intRes.isSuccess)
  }

  @Test
  fun observeOnlinePlayers_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = RadarQuestRepository(firestore, auth)

    var caught: Throwable? = null
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeOnlinePlayers("unauth_uid").first()
      }
    } catch (e: Throwable) {
      caught = e
    }
    assertNotNull(caught)
    val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $caught"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice_radar@test.com"
    const val BOB_EMAIL = "bob_radar@test.com"
    const val TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
