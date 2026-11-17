package com.example

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Mission
import com.example.data.model.MissionCompletion
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyMissionPopup
import com.example.data.model.UserSettings
import com.example.data.remote.OsmSafePlaceFinder
import com.example.ui.components.ExpandedRadarMapDialog
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.NearbyMissionPopupOverlay
import com.example.ui.components.QueuedMissionPromptDialog
import com.example.ui.components.TilePuzzleGameDialog
import com.example.ui.components.WireConnectGameDialog
import com.example.ui.screens.CustomizeStyleScreen
import com.example.ui.screens.RadarHomeScreen
import com.example.ui.screens.SocialFeedScreen
import com.example.ui.theme.RadarQuestTheme
import com.example.ui.viewmodel.RadarQuestUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    val pubName = context.getString(R.string.publication_name)
    assertEquals("Veyra", appName)
    assertEquals("Veyra: Missions and Friends", pubName)
  }

  @Test
  fun `nearby mission popup has no accept reject buttons and dismisses on left or right swipe`() {
    val dismissed = mutableStateListOf<String>()
    val popups = listOf(
      NearbyMissionPopup(
        missionId = "m_left",
        title = "Mural del parque",
        category = "creative",
        distanceMeters = 95f,
        xpReward = 140L,
      ),
      NearbyMissionPopup(
        missionId = "m_right",
        title = "Fuente central",
        category = "explore",
        distanceMeters = 160f,
        xpReward = 100L,
      ),
    )

    composeTestRule.setContent {
      RadarQuestTheme {
        val visiblePopups = popups.filterNot { it.missionId in dismissed }
        NearbyMissionPopupOverlay(
          popups = visiblePopups,
          disableAnimations = true,
          onDismissPopup = { dismissed.add(it) },
        )
      }
    }

    composeTestRule.onNodeWithText("Mural del parque").assertIsDisplayed()
    composeTestRule.onNodeWithText("Aceptar").assertDoesNotExist()
    composeTestRule.onNodeWithText("Rechazar").assertDoesNotExist()

    composeTestRule.onNodeWithTag("nearby_mission_popup_m_left").performTouchInput {
      swipeLeft()
    }
    composeTestRule.waitForIdle()
    assertTrue("m_left" in dismissed)

    composeTestRule.onNodeWithText("Fuente central").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nearby_mission_popup_m_right").performTouchInput {
      swipeRight()
    }
    composeTestRule.waitForIdle()
    assertTrue("m_right" in dismissed)
  }

  @Test
  fun `social feed screen shows public photo completion and allows cheering`() {
    var cheeredTarget: MissionCompletion? = null
    val completion = MissionCompletion(
      id = "comp_social_1",
      userId = "other_user",
      userName = "Luna",
      missionId = "m_eco_1",
      missionTitle = "Árbol centenario",
      category = "eco",
      xpEarned = 160L,
      caption = "¡Confirmada cerca del lago!",
      photoThumbnail = "invalid_placeholder_ok",
      isPublic = true,
    )

    composeTestRule.setContent {
      RadarQuestTheme {
        SocialFeedScreen(
          uiState = RadarQuestUiState(publicCompletions = listOf(completion)),
          currentUserId = "me_user",
          onCheerCompletion = { cheeredTarget = it },
        )
      }
    }

    composeTestRule.onNodeWithText("Red Social").assertIsDisplayed()
    composeTestRule.onNodeWithText("Luna").assertIsDisplayed()
    composeTestRule.onNodeWithText("Árbol centenario")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("cheer_post_button_comp_social_1")
      .performScrollTo()
      .performClick()
    assertEquals("comp_social_1", cheeredTarget?.id)
  }

  @Test
  fun `floating bottom nav bar renders all destinations and switches tabs`() {
    var selected = -1
    composeTestRule.setContent {
      RadarQuestTheme {
        FloatingBottomNavBar(
          selectedTab = 0,
          disableAnimations = true,
          onSelectTab = { selected = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("floating_bottom_nav_bar").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_social_feed").performClick()
    assertEquals(2, selected)
  }

  @Test
  fun `customize style screen toggles gesture navigation mode`() {
    var toggledValue: Boolean? = null
    composeTestRule.setContent {
      RadarQuestTheme {
        CustomizeStyleScreen(
          uiState = RadarQuestUiState(settings = UserSettings(gestureNavEnabled = false)),
          onSaveTheme = { _, _, _, _ -> },
          onSaveAnimationTier = {},
          onToggleNotifications = {},
          onToggleGestureNav = { toggledValue = it },
          onRequestNotificationPermission = {},
          onSaveProfile = { _, _, _, _, _ -> },
          onReopenOnboardingQuest = {},
          onSignOut = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("gesture_nav_toggle_switch")
      .performScrollTo()
      .performClick()
    assertEquals(true, toggledValue)
  }

  @Test
  fun `wire connect mini game completes when all 5 color wires are paired`() {
    var completedCaption: String? = null
    val mission = Mission(
      id = "quest_wires_solar",
      title = "Circuito Solar Urbano",
      missionType = "wires",
      iconKey = "bolt",
      xpReward = 155L,
    )
    composeTestRule.setContent {
      RadarQuestTheme {
        WireConnectGameDialog(
          mission = mission,
          isSubmitting = false,
          onDismiss = {},
          onComplete = { caption, _ -> completedCaption = caption },
        )
      }
    }

    listOf("red", "blue", "green", "amber", "violet").forEach { colorId ->
      composeTestRule.onNodeWithTag("wire_left_$colorId").performClick()
      composeTestRule.onNodeWithTag("wire_right_$colorId").performClick()
    }
    composeTestRule.onNodeWithTag("wire_complete_mission_button").performClick()
    assertTrue(completedCaption != null)
  }

  @Test
  fun `tile puzzle mini game renders board and dialog`() {
    val mission = Mission(
      id = "quest_puzzle_compass",
      title = "Brújula Fragmentada",
      missionType = "puzzle",
      iconKey = "compass",
      xpReward = 160L,
    )
    composeTestRule.setContent {
      RadarQuestTheme {
        TilePuzzleGameDialog(
          mission = mission,
          isSubmitting = false,
          onDismiss = {},
          onComplete = { _, _ -> },
        )
      }
    }

    composeTestRule.onNodeWithTag("tile_puzzle_game_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Rompecabezas 3×3").assertIsDisplayed()
    composeTestRule.onNodeWithTag("puzzle_tile_1").assertIsDisplayed()
  }

  @Test
  fun `queued mission prompt dialog triggers accept or reject callbacks`() {
    var accepted = false
    var rejected = false
    val mission = MissionWithDistance(
      mission = Mission(
        id = "q_wait_1",
        title = "Enlace de Baliza",
        category = "explore",
        missionType = "wires",
        iconKey = "hub",
        xpReward = 150L,
      ),
      distanceMeters = 90f,
      bearingDegrees = 40f,
      isCompletedByMe = false,
      isWithinProximity = true,
    )

    composeTestRule.setContent {
      RadarQuestTheme {
        QueuedMissionPromptDialog(
          queuedMission = mission,
          remainingQueueCount = 1,
          onAcceptNow = { accepted = true },
          onReject = { rejected = true },
          onKeepWaiting = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("queued_mission_prompt_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithTag("queued_prompt_accept_button").performClick()
    assertTrue(accepted)

    composeTestRule.onNodeWithTag("queued_prompt_reject_button").performClick()
    assertTrue(rejected)
  }

  @Test
  fun `radar home screen mission cards expose accept wait and reject actions`() {
    var lastAction = ""
    val sampleMission = MissionWithDistance(
      mission = Mission(
        id = "m_card_1",
        title = "Circuito Solar Urbano",
        description = "Reconecta los cables",
        category = "eco",
        missionType = "wires",
        iconKey = "bolt",
        xpReward = 155L,
      ),
      distanceMeters = 80f,
      bearingDegrees = 30f,
      isCompletedByMe = false,
      isWithinProximity = true,
    )

    composeTestRule.setContent {
      RadarQuestTheme {
        RadarHomeScreen(
          uiState = RadarQuestUiState(
            missions = listOf(sampleMission),
            hasLocationPermission = true,
          ),
          onRequestLocationPermission = {},
          onOpenNotifications = {},
          onRefresh = {},
          onToggleBroadcast = {},
          onToggleSeekingHelp = {},
          onCycleRadius = {},
          onHelpPlayer = {},
          onSendPulse = {},
          onCompeteDuel = { _, _ -> },
          onPlayCoop = { _, _ -> },
          onCreateMission = { _, _, _, _, _, _ -> },
          onConfirmMissionPhoto = { _, _, _, _ -> },
          onAcceptMission = { lastAction = "accept_$it" },
          onWaitMission = { lastAction = "wait_$it" },
          onRejectMission = { lastAction = "reject_$it" },
        )
      }
    }

    composeTestRule.onNodeWithTag("accept_mission_button_m_card_1")
      .performScrollTo()
      .performClick()
    assertEquals("accept_m_card_1", lastAction)

    composeTestRule.onNodeWithTag("wait_mission_button_m_card_1")
      .performScrollTo()
      .performClick()
    assertEquals("wait_m_card_1", lastAction)

    composeTestRule.onNodeWithTag("reject_mission_button_m_card_1")
      .performScrollTo()
      .performClick()
    assertEquals("reject_m_card_1", lastAction)
  }

  @Test
  fun `mission detail sheet renders demonstrated geo point, 1-minute countdown clock, and solves wires puzzle inline`() {
    var inlineCompletedCaption: String? = null
    val timedPuzzle = MissionWithDistance(
      mission = Mission(
        id = "quest_puzzle_clock_wires",
        title = "Desactivación Relámpago",
        description = "Conecta los cables dentro del menú en 1 minuto.",
        category = "puzzle",
        missionType = "wires",
        iconKey = "bolt",
        timeLimitSeconds = 60,
        placeLabel = "Plaza cívica abierta",
        isSafePublicVerified = true,
        xpReward = 190L,
        latitude = 19.43260,
        longitude = -99.13320,
      ),
      distanceMeters = 45f,
      bearingDegrees = 20f,
      isCompletedByMe = false,
      isWithinProximity = true,
    )

    composeTestRule.setContent {
      RadarQuestTheme {
        com.example.ui.components.MissionDetailSheet(
          item = timedPuzzle,
          hasLocationPermission = true,
          showExpandMapButton = true,
          isSubmitting = false,
          onDismiss = {},
          onStartMissionAction = {},
          onCompleteMiniGame = { caption, _ -> inlineCompletedCaption = caption },
          onOpenExpandedMap = {},
          onRequestLocationPermission = {},
        )
      }
    }

    composeTestRule.onNodeWithTag("mission_sheet_geo_point_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("Punto geográfico demostrado: Plaza cívica abierta").assertIsDisplayed()
    composeTestRule.onNodeWithTag("inline_mission_puzzle_panel")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("inline_puzzle_timer_badge")
      .performScrollTo()
      .assertIsDisplayed()

    listOf("red", "blue", "green", "amber", "violet").forEach { colorId ->
      composeTestRule.onNodeWithTag("inline_wire_left_$colorId")
        .performScrollTo()
        .performClick()
      composeTestRule.onNodeWithTag("inline_wire_right_$colorId")
        .performScrollTo()
        .performClick()
    }
    composeTestRule.onNodeWithTag("inline_puzzle_complete_button")
      .performScrollTo()
      .performClick()
    assertTrue(inlineCompletedCaption != null)
  }

  @Test
  fun `osmSafePlaceFinder parses Overpass JSON and filters private access`() {
    val rawOverpass = """
      {
        "elements": [
          {
            "type": "node",
            "lat": 19.4310,
            "lon": -99.1680,
            "tags": { "name": "Parque Lincoln", "leisure": "park" }
          },
          {
            "type": "way",
            "center": { "lat": 19.4220, "lon": -99.1610 },
            "tags": { "name": "Residencial Privado", "leisure": "garden", "access": "private" }
          }
        ]
      }
    """.trimIndent()
    val parsed = OsmSafePlaceFinder.parseOverpassResponse(rawOverpass)
    assertEquals(1, parsed.size)
    assertEquals("Parque Lincoln", parsed.first().name)
    assertTrue(parsed.first().isFromOpenStreetMap)
  }

  @Test
  fun `expanded radar map dialog shows OpenStreetMap title, user location marker, and mission controls`() {
    var closed = false
    var shuffled = false
    val mission = MissionWithDistance(
      mission = Mission(
        id = "osm_m_1",
        title = "Parque Central",
        category = "eco",
        missionType = "photo",
        iconKey = "park",
        xpReward = 130L,
        latitude = 19.4335,
        longitude = -99.1340,
      ),
      distanceMeters = 105f,
      bearingDegrees = 35f,
      isCompletedByMe = false,
      isWithinProximity = true,
    )

    composeTestRule.setContent {
      RadarQuestTheme {
        ExpandedRadarMapDialog(
          myProfile = null,
          nearbyPlayers = emptyList(),
          missions = listOf(mission),
          searchRadiusMeters = 1500L,
          hasLocationPermission = true,
          safePublicAnchorsCount = 4,
          onDismiss = { closed = true },
          onMissionClick = {},
          onPlayerClick = {},
          onRespawnRandomMissions = { shuffled = true },
        )
      }
    }

    composeTestRule.onNodeWithTag("expanded_radar_map_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("OpenStreetMap (1 misiones)").assertIsDisplayed()
    composeTestRule.onNodeWithText("Tú").assertIsDisplayed()
    composeTestRule.onNodeWithTag("expanded_map_shuffle_button").performClick()
    assertTrue(shuffled)
    composeTestRule.onNodeWithTag("close_expanded_map_button").performClick()
    assertTrue(closed)
  }
}
