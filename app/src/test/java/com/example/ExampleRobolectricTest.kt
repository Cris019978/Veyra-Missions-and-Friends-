package com.example

import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.widget.Button
import android.widget.TextView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalAccountStore
import com.example.data.location.LocationTracker
import com.example.data.model.Mission
import com.example.data.model.MissionCompletion
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyMissionPopup
import com.example.data.model.UserSettings
import com.example.data.remote.OsmSafePlaceFinder
import com.example.data.repository.RadarQuestRepository
import com.example.ui.components.ExpandedRadarMapDialog
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.NearbyMissionPopupOverlay
import com.example.ui.components.QueuedMissionPromptDialog
import com.example.ui.components.TilePuzzleGameDialog
import com.example.ui.components.WireConnectGameDialog
import com.example.ui.crash.CrashReportActivity
import com.example.ui.screens.CustomizeStyleScreen
import com.example.ui.screens.RadarHomeScreen
import com.example.ui.screens.SocialFeedScreen
import com.example.ui.theme.RadarQuestTheme
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.SafeRuntimeBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
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
  fun `social feed screen shows public photo completion with role badge and allows cheering`() {
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
      authorRole = "beta_tester",
      authorRoleTitle = "Beta Tester",
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
    composeTestRule.onNodeWithTag("social_post_role_badge_comp_social_1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Beta Tester").assertIsDisplayed()
    composeTestRule.onNodeWithTag("social_post_contribution_score_comp_social_1").assertIsDisplayed()
    composeTestRule.onNodeWithText("Contribución: 1").assertIsDisplayed()
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

  @Test
  fun `safeRuntimeBootstrap initializes osmdroid into private cache and handles missing Firebase safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(SafeRuntimeBootstrap.initOsmdroidSafely(context))
    val osmdroidBase = org.osmdroid.config.Configuration.getInstance().osmdroidBasePath
    assertNotNull(osmdroidBase)
    assertTrue(osmdroidBase.absolutePath.startsWith(context.cacheDir.absolutePath))

    val firebaseState = SafeRuntimeBootstrap.tryInitFirebase(context, "")
    assertFalse(firebaseState.isAvailable)
  }

  @Test
  fun `radarQuestRepository operates cleanly in local exploration mode without Firebase`() = runBlocking {
    val repo = RadarQuestRepository(db = null)
    assertFalse(repo.isCloudAvailable)

    val saveProfileRes = repo.ensureOrUpdatePlayerProfile(
      username = "explorador_local",
      callsign = "VeyraLocal",
      avatarIcon = "explore",
      avatarColorHex = "#00BFA5",
      statusMessage = "Probando modo local",
      seekingHelp = false,
      isOnline = true,
      latitude = 19.4326,
      longitude = -99.1332,
    )
    assertTrue(saveProfileRes.isSuccess)
    assertEquals("VeyraLocal", repo.observeMyProfile(RadarQuestRepository.LOCAL_USER_ID).first()?.callsign)

    val saveSettingsRes = repo.saveUserSettings(UserSettings(paletteKey = "emerald", onboardingCompleted = true))
    assertTrue(saveSettingsRes.isSuccess)
    assertEquals("emerald", repo.observeUserSettings(RadarQuestRepository.LOCAL_USER_ID).first()?.paletteKey)

    val createRes = repo.createMission(
      creatorName = "VeyraLocal",
      title = "Fuente del parque",
      description = "Captura la fuente central",
      category = "explore",
      xpReward = 120L,
      latitude = 19.4330,
      longitude = -99.1330,
    )
    assertTrue(createRes.isSuccess)
    assertEquals(1, repo.observeActiveMissions(RadarQuestRepository.LOCAL_USER_ID).first().size)

    val compRes = repo.submitMissionCompletion(
      userName = "VeyraLocal",
      missionId = createRes.getOrThrow(),
      missionTitle = "Fuente del parque",
      category = "explore",
      xpEarned = 120L,
      caption = "Completada sin nube",
      photoThumbnailBase64 = "",
      latitude = 19.4330,
      longitude = -99.1330,
      isPublic = true,
    )
    assertTrue(compRes.isSuccess)
    repo.awardPlayerStats(addXp = 120L, addMissionsCompleted = 1L)
    assertEquals(120L, repo.observeMyProfile(RadarQuestRepository.LOCAL_USER_ID).first()?.xp)
  }

  @Test
  fun `locationTracker falls back automatically to native LocationManager when Play Services unavailable`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val shadowLm = Shadows.shadowOf(lm)
    val gpsLoc = Location(LocationManager.GPS_PROVIDER).apply {
      latitude = 19.4326
      longitude = -99.1332
      accuracy = 8f
      time = System.currentTimeMillis()
    }
    shadowLm.setLastKnownLocation(LocationManager.GPS_PROVIDER, gpsLoc)

    val tracker = LocationTracker(
      context = context,
      playServicesAvailabilityChecker = { false },
    )
    val point = tracker.observeLocationUpdates().first()
    assertEquals(19.4326, point.latitude, 0.0001)
    assertEquals(-99.1332, point.longitude, 0.0001)
  }

  @Test
  fun `crashReportActivity shows scrollable trace and supports copy and retry actions`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = SafeRuntimeBootstrap.formatCrashReport(
      threadName = "main",
      throwable = IllegalStateException("Simulated Firebase init error"),
    )
    val intent = CrashReportActivity.createIntent(context, "main", report)
    val controller = Robolectric.buildActivity(CrashReportActivity::class.java, intent).setup()
    val activity = controller.get()

    val traceView = activity.findViewById<TextView>(CrashReportActivity.TRACE_TEXT_VIEW_ID)
    val copyBtn = activity.findViewById<Button>(CrashReportActivity.COPY_BUTTON_ID)
    val retryBtn = activity.findViewById<Button>(CrashReportActivity.RETRY_BUTTON_ID)

    assertTrue(traceView.text.toString().contains("Simulated Firebase init error"))
    assertEquals("Copiar", copyBtn.text.toString())
    assertEquals("Reintentar", retryBtn.text.toString())

    copyBtn.performClick()
    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertTrue(
      clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.contains("Simulated Firebase init error") == true
    )

    retryBtn.performClick()
    assertTrue(activity.isFinishing)
  }

  @Test
  fun `radar home screen shows local offline mode banner when running without Firebase`() {
    composeTestRule.setContent {
      RadarQuestTheme {
        RadarHomeScreen(
          uiState = RadarQuestUiState(isLocalOfflineMode = true),
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
        )
      }
    }

    composeTestRule.onNodeWithTag("local_offline_mode_banner").assertIsDisplayed()
  }

  @Test
  fun `safeRuntimeBootstrap checkGooglePlayServices returns structured status without throwing`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val status = SafeRuntimeBootstrap.checkGooglePlayServices(context)
    // In Robolectric without GMS APK, GoogleApiAvailability reports unavailable with a clear Spanish message
    assertFalse(status.isAvailable)
    assertNotNull(status.statusMessage)
  }

  @Test
  fun `auth screen toggles login and register with 2 primary fields and validates PIN`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authManager = com.example.data.local.LocalAuthManager(context)
    authManager.clearAllAccountsForTest()

    composeTestRule.setContent {
      RadarQuestTheme(themeMode = "dark") {
        com.example.ui.auth.AuthScreen(
          onLogin = { u, p -> authManager.login(u, p) },
          onRegister = { u, p, code -> authManager.registerAccount(u, p, code) },
        )
      }
    }

    // Default Login mode shows 2 primary inputs: username & PIN
    composeTestRule.onNodeWithTag("auth_username_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_pin_input").assertIsDisplayed()

    // Short PIN rejected
    composeTestRule.onNodeWithTag("auth_username_input").performScrollTo().performTextInput("@explorador")
    composeTestRule.onNodeWithTag("auth_pin_input").performScrollTo().performTextInput("12")
    composeTestRule.onNodeWithTag("veyra_submit_auth_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("veyra_auth_error_banner").assertIsDisplayed()
    assertFalse(authManager.isLoggedIn())

    // Switch to Crear Cuenta tab & create account with creator handle
    composeTestRule.onNodeWithTag("auth_mode_register_tab").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("auth_username_input").performScrollTo().performTextReplacement("@veyra")
    composeTestRule.onNodeWithTag("auth_pin_input").performScrollTo().performTextReplacement("4321")
    composeTestRule.onNodeWithTag("veyra_submit_auth_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertTrue(authManager.isLoggedIn())
    val session = authManager.getCurrentSession()
    assertEquals("veyra", session?.username)
    assertEquals("creator", session?.role)
    assertEquals("Creador", session?.roleTitle)
  }

  @Test
  fun `localAuthManager hashes PIN with SHA-256, persists session, assigns roles, and clears isLoggedIn on logout`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = com.example.data.local.LocalAuthManager(context)
    manager.clearAllAccountsForTest()

    // 1. Default explorer role + SHA-256 PIN verification
    val regUser = manager.registerAccount("@ana_radar", "9876")
    assertTrue(regUser is com.example.data.local.LocalAuthResult.Success)
    val userSession = (regUser as com.example.data.local.LocalAuthResult.Success).session
    assertEquals("explorer", userSession.role)
    assertEquals("Explorador", userSession.roleTitle)
    assertFalse(userSession.pinHash == "9876")
    assertEquals(64, userSession.pinHash.length)

    // Verify SharedPreferences does NOT store plaintext PIN anywhere
    val rawPrefs = context.getSharedPreferences("veyra_local_auth_prefs", Context.MODE_PRIVATE).all.values.joinToString(",")
    assertFalse(rawPrefs.contains("9876"))

    // 2. Beta tester by invite code
    manager.logout()
    assertFalse(manager.isLoggedIn())

    val regBeta = manager.registerAccount("@luis_beta", "2025", "VEYRA-BETA")
    assertTrue(regBeta is com.example.data.local.LocalAuthResult.Success)
    assertEquals("Beta Tester", (regBeta as com.example.data.local.LocalAuthResult.Success).session.roleTitle)

    // 3. Creator role by admin username
    manager.logout()
    val regCreator = manager.registerAccount("@veyra", "7777")
    assertTrue(regCreator is com.example.data.local.LocalAuthResult.Success)
    assertEquals("creator", (regCreator as com.example.data.local.LocalAuthResult.Success).session.role)
    assertEquals("Creador", regCreator.session.roleTitle)

    // 4. Re-opening app (brand-new LocalAuthManager instance) keeps active session
    manager.updateActiveStats(xp = 420L, level = 2L)
    val reopened = com.example.data.local.LocalAuthManager(context)
    assertTrue(reopened.isLoggedIn())
    assertEquals("veyra", reopened.getCurrentSession()?.username)
    assertEquals(420L, reopened.getCurrentSession()?.xp)

    // 5. Wrong PIN fails and right PIN succeeds after logout
    reopened.logout()
    assertFalse(reopened.isLoggedIn())
    assertNull(reopened.getCurrentSession())
    assertTrue(reopened.login("@veyra", "0000") is com.example.data.local.LocalAuthResult.Error)
    assertTrue(reopened.login("@veyra", "7777") is com.example.data.local.LocalAuthResult.Success)
    assertTrue(reopened.isLoggedIn())
  }

  @Test
  fun `profile screen displays Creador role title badge alongside username`() {
    val profile = com.example.data.model.PlayerProfile(
      userId = "veyra",
      username = "veyra",
      callsign = "Veyra",
      role = "creator",
      roleTitle = "Creador",
    )
    composeTestRule.setContent {
      RadarQuestTheme {
        com.example.ui.screens.ProfileScreen(
          uiState = RadarQuestUiState(myProfile = profile, isLoading = false),
          onSaveProfile = { _, _, _, _, _, _, _, _, _ -> },
          onQuickSelectPalette = {},
          onOpenStyleCustomization = {},
        )
      }
    }

    composeTestRule.onNodeWithText("@veyra").assertIsDisplayed()
    composeTestRule.onNodeWithTag("profile_role_badge").assertIsDisplayed()
    composeTestRule.onNodeWithText("Creador").assertIsDisplayed()
  }

  @Test
  fun `localAccountStore persists saved account across repository instances and clears on sign out`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val store = LocalAccountStore(context)
    store.clearSavedAccount(markExplicitlySignedOut = false)

    store.markSessionSaved(
      authMode = "local",
      userId = RadarQuestRepository.LOCAL_USER_ID,
      defaultDisplayName = "CapitanaVeyra",
    )
    val repoFirstLaunch = RadarQuestRepository(db = null, localAccountStore = store)
    repoFirstLaunch.ensureOrUpdatePlayerProfile(
      username = "capitana_veyra",
      callsign = "CapitanaVeyra",
      avatarIcon = "bolt",
      avatarColorHex = "#5C6BC0",
      statusMessage = "Lista para explorar",
      seekingHelp = false,
      isOnline = true,
      latitude = 19.4326,
      longitude = -99.1332,
      role = "beta_tester",
      roleTitle = "Beta Tester",
    )
    repoFirstLaunch.awardPlayerStats(addXp = 310L, addMissionsCompleted = 2L)

    // Simulate reopening the app with a brand-new repository instance backed by LocalAccountStore
    val repoReopen = RadarQuestRepository(db = null, localAccountStore = store)
    val restoredSnapshot = store.getSnapshotOnce()
    val restoredProfile = repoReopen.observeMyProfile(RadarQuestRepository.LOCAL_USER_ID).first()

    assertTrue(restoredSnapshot.isSessionSaved)
    assertFalse(restoredSnapshot.explicitlySignedOut)
    assertEquals("CapitanaVeyra", restoredProfile?.callsign)
    assertEquals("Beta Tester", restoredProfile?.roleTitle)
    assertEquals(310L, restoredProfile?.xp)
    assertEquals(2L, restoredProfile?.missionsCompleted)

    // Sign out deletes saved local account data and marks explicit sign-out
    store.clearSavedAccount(markExplicitlySignedOut = true)
    val clearedSnapshot = store.getSnapshotOnce()
    assertFalse(clearedSnapshot.isSessionSaved)
    assertTrue(clearedSnapshot.explicitlySignedOut)
    assertNull(clearedSnapshot.savedProfile)
  }

  @Test
  fun `customize style screen shows sign out confirmation dialog with No Regresar and Si Acepto`() {
    var signOutCalls = 0
    composeTestRule.setContent {
      RadarQuestTheme {
        CustomizeStyleScreen(
          uiState = RadarQuestUiState(isLocalOfflineMode = true),
          onSaveTheme = { _, _, _, _ -> },
          onSaveAnimationTier = {},
          onToggleNotifications = {},
          onToggleGestureNav = {},
          onRequestNotificationPermission = {},
          onSaveProfile = { _, _, _, _, _ -> },
          onReopenOnboardingQuest = {},
          onSignOut = { signOutCalls += 1 },
        )
      }
    }

    composeTestRule.onNodeWithTag("customize_style_list")
      .performScrollToNode(hasTestTag("sign_out_button"))
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sign_out_button")
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("sign_out_confirmation_dialog").assertIsDisplayed()
    composeTestRule.onNodeWithText("Realmente quieres cerrar la sesión").assertIsDisplayed()

    // Pressing "No, Regresar" closes only the confirmation dialog without signing out
    composeTestRule.onNodeWithTag("cancel_sign_out_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("sign_out_confirmation_dialog").assertDoesNotExist()
    assertEquals(0, signOutCalls)

    // Reopen and confirm "Si Acepto"
    composeTestRule.onNodeWithTag("sign_out_button")
      .assertIsDisplayed()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("confirm_sign_out_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(1, signOutCalls)
  }

  @Test
  fun `localMissionFeedRepository stores and reactively emits posts in Room with author roles`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.db.VeyraLocalDatabase::class.java,
    ).allowMainThreadQueries().build()

    try {
      val feedRepo = com.example.data.local.LocalMissionFeedRepository(
        dao = db.localMissionPostDao(),
        contributionDao = db.authorContributionDao(),
      )
      assertEquals(0, feedRepo.postsFlow.first().size)

      // Initial seed inserts 5 localized posts with Creator, Beta Tester, and Explorer roles
      feedRepo.seedInitialPostsIfEmpty(baseLat = 19.4326, baseLon = -99.1332)
      val seeded = feedRepo.postsFlow.first()
      assertEquals(5, seeded.size)
      assertTrue(seeded.any { it.authorRoleTitle == "Creador" })
      assertTrue(seeded.any { it.authorRoleTitle == "Beta Tester" })
      val initialScores = feedRepo.contributionScoresFlow.first()
      assertEquals(14L, initialScores["veyra"])
      assertEquals(8L, initialScores["tester1"])

      // Recording completed/verified missions increments the author's persisted contribution score
      feedRepo.recordCompletedOrVerifiedMission(authorUserId = "veyra", authorUsername = "veyra")
      assertEquals(15L, feedRepo.contributionScoresFlow.first()["veyra"])

      // Publishing a user mission post triggers reactive Flow update immediately
      val customPost = feedRepo.publishMissionPost(
        title = "Baliza en Alameda",
        description = "Fotografía la glorieta principal desde la acera norte.",
        category = "explore",
        xpReward = 200L,
        authorUserId = "veyra",
        authorDisplayName = "Veyra Admin",
        authorUsername = "veyra",
        authorRole = "creator",
        authorRoleTitle = "Creador",
        latitude = 19.4350,
        longitude = -99.1440,
        placeLabel = "Alameda Central",
      )

      val updated = feedRepo.postsFlow.first()
      assertEquals(6, updated.size)
      assertEquals(customPost.id, updated.first().id)
      assertEquals("Creador", updated.first().authorRoleTitle)
      assertEquals("Fotografía la glorieta principal desde la acera norte.", updated.first().description)

      // Generating a fresh nearby batch appends 3 more proximity missions
      val batch = feedRepo.generateNewNearbyBatch(baseLat = 19.4326, baseLon = -99.1332)
      assertEquals(3, batch.size)
      assertEquals(9, feedRepo.postsFlow.first().size)
    } finally {
      db.close()
    }
  }

  @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
  @Test
  fun `radarQuestViewModel computes live GPS proximity and dynamically updates local mission feed`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.local.db.VeyraLocalDatabase::class.java,
    ).allowMainThreadQueries().build()

    val dispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
      val feedRepo = com.example.data.local.LocalMissionFeedRepository(
        dao = db.localMissionPostDao(),
        contributionDao = db.authorContributionDao(),
      )
      val themePrefs = com.example.data.local.LocalThemePreferences(context)
      val localRepo = RadarQuestRepository(db = null)
      val viewModel = com.example.ui.viewmodel.RadarQuestViewModel(
        repository = localRepo,
        localPrefs = themePrefs,
        currentUserId = "explorer_contrib",
        defaultDisplayName = "Explorer Contrib",
        isLocalOfflineMode = true,
        accountRole = "beta_tester",
        accountRoleTitle = "Beta Tester",
        localMissionFeedRepository = feedRepo,
      )

      // Activate SharingStarted.WhileSubscribed collector
      val collectJob = kotlinx.coroutines.CoroutineScope(dispatcher).launch {
        viewModel.uiState.collect {}
      }

      // Publish a post at an exact known coordinate (19.4326, -99.1332)
      feedRepo.publishMissionPost(
        title = "Fuente del Centro",
        description = "Verifica la fuente histórica del zócalo.",
        category = "explore",
        xpReward = 170L,
        authorUserId = "explorer_contrib",
        authorDisplayName = "Explorer Contrib",
        authorUsername = "explorer_contrib",
        authorRole = "beta_tester",
        authorRoleTitle = "Beta Tester",
        latitude = 19.4326,
        longitude = -99.1332,
        placeLabel = "Zócalo",
      )

      // Move GPS ~500m away first -> NOT immediate proximity
      viewModel.onLocationUpdated(
        com.example.data.location.LiveLocationPoint(
          latitude = 19.4371,
          longitude = -99.1332,
          accuracyMeters = 6f,
        )
      )
      val farItem = viewModel.uiState.first { state ->
        state.localMissionFeed.any { it.post.title == "Fuente del Centro" }
      }.localMissionFeed.first { it.post.title == "Fuente del Centro" }
      assertTrue(farItem.distanceMeters > 350f)
      assertFalse(farItem.isImmediateProximity)
      assertEquals(0L, farItem.authorContributionScore)

      // Move GPS within ~30m -> updates dynamically to immediate proximity (<= 120m)
      viewModel.onLocationUpdated(
        com.example.data.location.LiveLocationPoint(
          latitude = 19.4328,
          longitude = -99.1332,
          accuracyMeters = 4f,
        )
      )
      val closeItem = viewModel.uiState.first { state ->
        state.localMissionFeed.firstOrNull { it.post.title == "Fuente del Centro" }?.isImmediateProximity == true
      }.localMissionFeed.first { it.post.title == "Fuente del Centro" }
      assertTrue(closeItem.distanceMeters <= 120f)
      assertTrue(closeItem.isImmediateProximity)

      // Completing/verifying missions dynamically increments author's Contribution Score in feed (0 -> 1 -> 2)
      viewModel.confirmMissionWithPhoto(
        mission = closeItem.post.toRadarMission(),
        caption = "Foto verificada",
        photoThumbnailBase64 = "",
        isPublic = true,
      )
      val afterFirstVerify = viewModel.uiState.first { state ->
        state.localMissionFeed.firstOrNull { it.post.title == "Fuente del Centro" }?.authorContributionScore == 1L
      }.localMissionFeed.first { it.post.title == "Fuente del Centro" }
      assertEquals(1L, afterFirstVerify.authorContributionScore)

      // Publish directly through ViewModel and complete a second mission -> score increments to 2
      viewModel.publishLocalFeedMission(
        title = "Mural Nuevo",
        description = "Captura el nuevo mural junto al cruce.",
        category = "creative",
        xpReward = 250L,
        placeLabel = "Cruce Norte",
      )
      val afterPublish = viewModel.uiState.first { state ->
        state.localMissionFeed.any { it.post.title == "Mural Nuevo" }
      }.localMissionFeed.first { it.post.title == "Mural Nuevo" }
      assertEquals("Beta Tester", afterPublish.post.authorRoleTitle)
      assertTrue(afterPublish.isImmediateProximity)
      assertEquals(1L, afterPublish.authorContributionScore)

      viewModel.completeMiniGameMission(
        mission = afterPublish.post.toRadarMission(),
        caption = "Puzzle resuelto",
        isPublic = true,
      )
      val afterSecondComplete = viewModel.uiState.first { state ->
        state.localMissionFeed.firstOrNull { it.post.title == "Mural Nuevo" }?.authorContributionScore == 2L
      }.localMissionFeed.first { it.post.title == "Mural Nuevo" }
      assertEquals(2L, afterSecondComplete.authorContributionScore)

      collectJob.cancel()
    } finally {
      Dispatchers.resetMain()
      db.close()
    }
  }

  @Test
  fun `socialFeedScreen displays real-time local mission feed with role, timestamp, GPS distance and publish dialog`() {
    val now = System.currentTimeMillis()
    val closePost = com.example.data.model.LocalMissionPost(
      id = "feed_close_1",
      title = "Mirador del Lago",
      description = "Toma una foto del reflejo del puente desde el sendero oeste.",
      category = "eco",
      xpReward = 190L,
      authorUserId = "veyra",
      authorDisplayName = "Veyra HQ",
      authorUsername = "veyra",
      authorRole = "creator",
      authorRoleTitle = "Creador",
      latitude = 19.4326,
      longitude = -99.1332,
      placeLabel = "Lago Mayor",
      createdAtMillis = now - 5 * 60_000L,
    )
    val farPost = com.example.data.model.LocalMissionPost(
      id = "feed_far_1",
      title = "Arco del Sur",
      description = "Explora la entrada sur y registra el arco conmemorativo.",
      category = "explore",
      xpReward = 140L,
      authorUserId = "tester1",
      authorDisplayName = "Tester Alfa",
      authorUsername = "tester1",
      authorRole = "beta_tester",
      authorRoleTitle = "Beta Tester",
      latitude = 19.4360,
      longitude = -99.1332,
      placeLabel = "Paseo Sur",
      createdAtMillis = now - 2 * 3_600_000L,
    )
    val feedItems = listOf(
      com.example.data.model.LocalMissionFeedItem(
        post = closePost,
        distanceMeters = 64f,
        bearingDegrees = 45f,
        isImmediateProximity = true,
        authorContributionScore = 14L,
      ),
      com.example.data.model.LocalMissionFeedItem(
        post = farPost,
        distanceMeters = 380f,
        bearingDegrees = 180f,
        isImmediateProximity = false,
        authorContributionScore = 8L,
      ),
    )

    var publishedTitle = ""
    var publishedDesc = ""

    composeTestRule.setContent {
      RadarQuestTheme {
        SocialFeedScreen(
          uiState = RadarQuestUiState(
            localMissionFeed = feedItems,
            currentLocation = com.example.data.location.LiveLocationPoint(19.4326, -99.1332, 5f),
          ),
          currentUserId = "other_me",
          onCheerCompletion = {},
          onPublishLocalMission = { title, desc, _, _, _ ->
            publishedTitle = title
            publishedDesc = desc
          },
        )
      }
    }

    // Verify immediate proximity card renders mission description, author role, contribution score, timestamp, and GPS distance
    composeTestRule.onNodeWithTag("local_feed_live_banner").assertIsDisplayed()
    composeTestRule.onNodeWithText("Mirador del Lago").performScrollTo().assertIsDisplayed()
    composeTestRule.onNodeWithText("Toma una foto del reflejo del puente desde el sendero oeste.")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("feed_post_role_badge_feed_close_1")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Creador").assertIsDisplayed()
    composeTestRule.onNodeWithTag("feed_post_contribution_score_feed_close_1")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Contribución: 14").assertIsDisplayed()
    composeTestRule.onNodeWithTag("feed_post_timestamp_feed_close_1")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("Hace 5 min").assertIsDisplayed()
    composeTestRule.onNodeWithTag("feed_post_distance_feed_close_1")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("64 m • ¡En tu rango!").assertIsDisplayed()

    // Filter to "Muy cerca" -> hides farPost (>120 m) and keeps closePost
    composeTestRule.onNodeWithTag("feed_mode_immediate").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Mirador del Lago").assertIsDisplayed()
    composeTestRule.onNodeWithText("Arco del Sur").assertDoesNotExist()

    // Open publish dialog and create a local mission post
    composeTestRule.onNodeWithTag("create_local_feed_post_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("feed_post_title_input").performTextInput("Kiosco Históric")
    composeTestRule.onNodeWithTag("feed_post_description_input").performTextInput("Captura el techo del kiosco central.")
    composeTestRule.onNodeWithTag("confirm_publish_feed_post_button").performClick()
    composeTestRule.waitForIdle()

    assertEquals("Kiosco Históric", publishedTitle)
    assertEquals("Captura el techo del kiosco central.", publishedDesc)
  }

  @Test
  fun `auth screen exposes scrollable IME container and MainActivity declares adjustResize`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageInfo = context.packageManager.getPackageInfo(
      context.packageName,
      android.content.pm.PackageManager.GET_ACTIVITIES,
    )
    val mainActivityInfo = packageInfo.activities?.firstOrNull { it.name.endsWith(".MainActivity") }
    assertNotNull(mainActivityInfo)
    assertEquals(
      android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
      mainActivityInfo!!.softInputMode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST,
    )

    composeTestRule.setContent {
      RadarQuestTheme(themeMode = "dark") {
        com.example.ui.auth.AuthScreen(
          onLogin = { _, _ -> com.example.data.local.LocalAuthResult.Error("none") },
          onRegister = { _, _, _ -> com.example.data.local.LocalAuthResult.Error("none") },
        )
      }
    }

    composeTestRule.onNodeWithTag("auth_scrollable_container").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_username_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("auth_pin_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("veyra_submit_auth_button").assertIsDisplayed()
  }

  @Test
  fun `expandedRadarMapDialog null-checks detached MapView repository when building overlays`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    SafeRuntimeBootstrap.initOsmdroidSafely(context)
    val detachedMap = org.osmdroid.views.MapView(context)
    // A MapView not attached to a window must never attempt Polygon/BasicInfoWindow repository access
    assertFalse(com.example.ui.components.isOsmMapRepositoryReady(null))
    assertFalse(com.example.ui.components.isOsmMapRepositoryReady(detachedMap))
    assertNull(com.example.ui.components.createSafePolygon(null))
    assertNull(com.example.ui.components.createSafePolygon(detachedMap))
    assertNull(com.example.ui.components.createSafePolyline(detachedMap))
    assertNull(com.example.ui.components.createSafeMarker(detachedMap))
    detachedMap.onDetach()
    assertNull(com.example.ui.components.createSafePolygon(detachedMap))
  }

  @Test
  fun `socialFeedScreen Verify button triggers confetti particle celebration overlay`() {
    val now = System.currentTimeMillis()
    val closePost = com.example.data.model.LocalMissionPost(
      id = "feed_verify_1",
      title = "Fuente Histórica",
      description = "Verifica la fuente del parque norte.",
      category = "explore",
      xpReward = 180L,
      authorUserId = "veyra",
      authorDisplayName = "Veyra HQ",
      authorUsername = "veyra",
      authorRole = "creator",
      authorRoleTitle = "Creador",
      latitude = 19.4326,
      longitude = -99.1332,
      placeLabel = "Parque Norte",
      createdAtMillis = now - 60_000L,
    )
    var verifiedId = ""

    composeTestRule.setContent {
      RadarQuestTheme {
        SocialFeedScreen(
          uiState = RadarQuestUiState(
            localMissionFeed = listOf(
              com.example.data.model.LocalMissionFeedItem(
                post = closePost,
                distanceMeters = 48f,
                bearingDegrees = 15f,
                isImmediateProximity = true,
                authorContributionScore = 10L,
              )
            ),
            settings = UserSettings(animationTier = "low"),
            currentLocation = com.example.data.location.LiveLocationPoint(19.4326, -99.1332, 4f),
          ),
          currentUserId = "me_user",
          onCheerCompletion = {},
          onVerifyFeedMission = { verifiedId = it.id },
        )
      }
    }

    composeTestRule.onNodeWithTag("confetti_celebration_overlay").assertDoesNotExist()
    composeTestRule.onNodeWithTag("verify_feed_mission_button_feed_verify_1")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()

    assertEquals("feed_verify_1", verifiedId)
    composeTestRule.onNodeWithTag("confetti_celebration_overlay").assertIsDisplayed()
  }

  @Test
  fun `radarHomeScreen preserves immutable selectedMission detail sheet even if background list changes`() {
    val targetMission = MissionWithDistance(
      mission = Mission(
        id = "immutable_detail_m1",
        title = "Obelisco del Lago",
        description = "Visita el obelisco público del lago.",
        category = "explore",
        missionType = "photo",
        iconKey = "explore",
        xpReward = 150L,
        placeLabel = "Paseo del Lago",
      ),
      distanceMeters = 90f,
      bearingDegrees = 45f,
      isCompletedByMe = false,
      isWithinProximity = true,
    )
    val missionsState = androidx.compose.runtime.mutableStateOf(listOf(targetMission))
    var dialogOpenReported = false

    composeTestRule.setContent {
      RadarQuestTheme {
        RadarHomeScreen(
          uiState = RadarQuestUiState(
            missions = missionsState.value,
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
          onDialogVisibilityChanged = { dialogOpenReported = it },
        )
      }
    }

    composeTestRule.onNodeWithTag("radar_mission_pin_immutable_detail_m1").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("mission_detail_sheet").assertIsDisplayed()
    assertTrue(dialogOpenReported)

    // Mutate/clear background mission list while sheet is open -> immutable selectedMission stays visible
    missionsState.value = emptyList()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("mission_detail_sheet").assertIsDisplayed()
    composeTestRule.onNodeWithText("Obelisco del Lago").assertIsDisplayed()
  }

  @Test
  fun `hapticFeedbackHelper uses VibrationEffect for radar pin tap and mission verification and VIBRATE permission is declared`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val packageInfo = context.packageManager.getPackageInfo(
      context.packageName,
      android.content.pm.PackageManager.GET_PERMISSIONS,
    )
    val requestedPermissions = packageInfo.requestedPermissions?.toSet() ?: emptySet()
    assertTrue(android.Manifest.permission.VIBRATE in requestedPermissions)

    val vibrator = com.example.util.HapticFeedbackHelper.resolveVibrator(context)
    assertNotNull(vibrator)
    val shadowVibrator = org.robolectric.Shadows.shadowOf(vibrator)
    shadowVibrator.setHasVibrator(true)
    vibrator!!.cancel()
    assertFalse(shadowVibrator.isVibrating)

    com.example.util.HapticFeedbackHelper.performRadarPinTap(context)
    assertTrue(shadowVibrator.isVibrating)

    vibrator.cancel()
    assertFalse(shadowVibrator.isVibrating)

    com.example.util.HapticFeedbackHelper.performMissionVerified(context)
    assertTrue(shadowVibrator.isVibrating)
  }

  @Test
  fun `tapping radar mission pin and Verify button trigger tactile haptic feedback`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vibrator = com.example.util.HapticFeedbackHelper.resolveVibrator(context)
    assertNotNull(vibrator)
    val shadowVibrator = org.robolectric.Shadows.shadowOf(vibrator)
    shadowVibrator.setHasVibrator(true)

    val radarMission = MissionWithDistance(
      mission = Mission(
        id = "haptic_pin_m1",
        title = "Baliza Táctil",
        description = "Toca el pin en el radar.",
        category = "explore",
        missionType = "photo",
        iconKey = "explore",
        xpReward = 120L,
        placeLabel = "Plaza Central",
      ),
      distanceMeters = 80f,
      bearingDegrees = 45f,
      isCompletedByMe = false,
      isWithinProximity = true,
    )
    val now = System.currentTimeMillis()
    val feedPost = com.example.data.model.LocalMissionPost(
      id = "haptic_verify_1",
      title = "Torre Táctil",
      description = "Verifica la torre para sentir la vibración.",
      category = "explore",
      xpReward = 160L,
      authorUserId = "veyra",
      authorDisplayName = "Veyra HQ",
      authorUsername = "veyra",
      authorRole = "creator",
      authorRoleTitle = "Creador",
      latitude = 19.4326,
      longitude = -99.1332,
      placeLabel = "Centro",
      createdAtMillis = now - 30_000L,
    )

    val showFeed = androidx.compose.runtime.mutableStateOf(false)
    var tappedMissionId = ""
    composeTestRule.setContent {
      RadarQuestTheme {
        if (!showFeed.value) {
          com.example.ui.components.RoundRadarMap(
            myProfile = null,
            nearbyPlayers = emptyList(),
            missions = listOf(radarMission),
            searchRadiusMeters = 500L,
            radarStyle = "classic",
            animationTier = "low",
            isBroadcasting = true,
            hasLocationPermission = true,
            onPlayerClick = {},
            onMissionClick = { tappedMissionId = it.mission.id },
            onCycleRadius = {},
            onExpandMap = {},
          )
        } else {
          SocialFeedScreen(
            uiState = RadarQuestUiState(
              localMissionFeed = listOf(
                com.example.data.model.LocalMissionFeedItem(
                  post = feedPost,
                  distanceMeters = 50f,
                  bearingDegrees = 0f,
                  isImmediateProximity = true,
                  authorContributionScore = 5L,
                )
              ),
              settings = UserSettings(animationTier = "low"),
              currentLocation = com.example.data.location.LiveLocationPoint(19.4326, -99.1332, 4f),
            ),
            currentUserId = "other",
            onCheerCompletion = {},
            onVerifyFeedMission = {},
          )
        }
      }
    }

    vibrator!!.cancel()
    assertFalse(shadowVibrator.isVibrating)
    composeTestRule.onNodeWithTag("radar_mission_pin_haptic_pin_m1").performClick()
    composeTestRule.waitForIdle()
    assertEquals("haptic_pin_m1", tappedMissionId)
    assertTrue(shadowVibrator.isVibrating)

    vibrator.cancel()
    assertFalse(shadowVibrator.isVibrating)
    showFeed.value = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("verify_feed_mission_button_haptic_verify_1")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    assertTrue(shadowVibrator.isVibrating)
  }
}
