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
import com.example.data.model.MissionCompletion
import com.example.data.model.NearbyMissionPopup
import com.example.data.model.UserSettings
import com.example.ui.components.FloatingBottomNavBar
import com.example.ui.components.NearbyMissionPopupOverlay
import com.example.ui.screens.CustomizeStyleScreen
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
}
