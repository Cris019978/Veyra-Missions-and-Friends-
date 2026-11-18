package com.example.data.repository

import com.example.data.local.LocalAuthManager
import com.example.data.model.Challenge
import com.example.data.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarQuestRepositoryRuleTest {

  @Test
  fun saveAndObserveUserSettings_localUser_succeeds() = runBlocking {
    val uid = "explorador_uno"
    val repo = RadarQuestRepository(db = null, localUserId = uid)

    val saveResult = repo.saveUserSettings(
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
    assertTrue(saveResult.isSuccess)

    val loaded = repo.observeUserSettings(uid).first { it != null && it.onboardingCompleted }
    assertNotNull(loaded)
    assertEquals("coral", loaded?.paletteKey)
    assertEquals("low", loaded?.animationTier)
    assertEquals("explorer", loaded?.playStyle)
    assertEquals(true, loaded?.gestureNavEnabled)
    assertEquals(listOf("explore", "fitness", "creative"), loaded?.favoriteCategories)
  }

  @Test
  fun playerProfileAndMissionCompletion_preservesRoleAndTitle() = runBlocking {
    val repo = RadarQuestRepository(db = null, localUserId = "veyra")

    val profileRes = repo.ensureOrUpdatePlayerProfile(
      username = "veyra",
      callsign = "VeyraAdmin",
      avatarIcon = "bolt",
      avatarColorHex = "#00BFA5",
      statusMessage = "Creando misiones",
      seekingHelp = false,
      isOnline = true,
      latitude = 19.4326,
      longitude = -99.1332,
      role = LocalAuthManager.ROLE_CREATOR,
      roleTitle = LocalAuthManager.TITLE_CREATOR,
      initialXp = 500L,
      initialLevel = 3L,
    )
    assertTrue(profileRes.isSuccess)

    val profile = repo.observeMyProfile("veyra").first()
    assertEquals(LocalAuthManager.ROLE_CREATOR, profile?.role)
    assertEquals(LocalAuthManager.TITLE_CREATOR, profile?.roleTitle)
    assertEquals(500L, profile?.xp)

    val missionId = repo.createMission(
      creatorName = "VeyraAdmin",
      title = "Caminata urbana",
      description = "Camina hasta una plaza y confírmalo en foto",
      category = "fitness",
      xpReward = 150L,
      latitude = 19.4326,
      longitude = -99.1332,
    ).getOrThrow()

    val completionId = repo.submitMissionCompletion(
      userName = "VeyraAdmin",
      missionId = missionId,
      missionTitle = "Caminata urbana",
      category = "fitness",
      xpEarned = 150L,
      caption = "Foto lista!",
      photoThumbnailBase64 = "base64_sample",
      latitude = 19.4326,
      longitude = -99.1332,
      isPublic = true,
      authorRole = LocalAuthManager.ROLE_CREATOR,
      authorRoleTitle = LocalAuthManager.TITLE_CREATOR,
    ).getOrThrow()
    assertTrue(completionId.isNotEmpty())

    val publicPosts = repo.observePublicCompletions("veyra").first()
    assertEquals(1, publicPosts.size)
    assertEquals(LocalAuthManager.TITLE_CREATOR, publicPosts.first().authorRoleTitle)
  }

  @Test
  fun challengeAndInteractionWorkflow_localMode_succeeds() = runBlocking {
    val repo = RadarQuestRepository(db = null, localUserId = "tester1")
    assertFalse(repo.isCloudAvailable)

    val challengeId = repo.createChallenge(
      creatorName = "TesterUno",
      mode = "duel",
      title = "Carrera fotográfica",
      missionId = "daily_park",
      xpStake = 200L,
    ).getOrThrow()

    val openList = repo.observeOpenChallenges("tester1").first()
    val target: Challenge = openList.first { it.id == challengeId }
    val joinRes = repo.joinChallenge(target, "RivalBeta")
    assertTrue(joinRes.isSuccess)

    val intRes = repo.sendInteraction(
      fromUserName = "TesterUno",
      toUserId = "veyra",
      toUserName = "Veyra",
      type = "help",
      message = "¡Ánimo en el duelo!",
      xpBonus = 25L,
    )
    assertTrue(intRes.isSuccess)
  }
}
