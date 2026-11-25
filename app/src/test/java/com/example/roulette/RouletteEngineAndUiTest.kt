package com.example.roulette

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import com.example.ui.theme.RadarQuestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-420dpi")
class RouletteEngineAndUiTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val testDispatcher = UnconfinedTestDispatcher()
  private val vmStore = ViewModelStore()

  @Before
  fun setupMainDispatcher() {
    Dispatchers.setMain(testDispatcher)
  }

  @After
  fun tearDownMainDispatcher() {
    vmStore.clear()
    Dispatchers.resetMain()
  }

  @Test
  fun `roulette screen switches classic and horizontal views and runs debug batch simulation`() {
    val manager = RouletteStateManager(random = Random(99L))
    vmStore.put("roulette_ui_test", manager)

    composeTestRule.setContent {
      RadarQuestTheme(themeMode = "dark") {
        RouletteTestScreen(stateManager = manager)
      }
    }

    composeTestRule.onNodeWithTag("roulette_classic_wheel").assertIsDisplayed()

    // Switch live to Horizontal Tape view
    composeTestRule.onNodeWithTag("roulette_view_horizontal").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("roulette_horizontal_tape").assertIsDisplayed()

    // Scroll to Debug Panel and simulate 10 spins
    composeTestRule.onNodeWithTag("roulette_lazy_list").performScrollToNode(hasTestTag("roulette_debug_panel"))
    composeTestRule.onNodeWithTag("roulette_debug_sim_10").performClick()
    composeTestRule.waitForIdle()

    assertEquals(10, manager.uiState.value.totalSpins)
    assertEquals(RouletteDisplayView.HORIZONTAL, manager.uiState.value.activeView)

    // Scroll back up to view switcher & stats chip
    composeTestRule.onNodeWithTag("roulette_lazy_list").performScrollToNode(hasTestTag("roulette_stat_spins"))
    composeTestRule.onNodeWithTag("roulette_stat_spins").assertTextContains("10")

    // Switch back to Classic without losing spin count
    composeTestRule.onNodeWithTag("roulette_view_classic").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("roulette_classic_wheel").assertIsDisplayed()
    assertEquals(10, manager.uiState.value.totalSpins)

    // Reset state via debug panel
    composeTestRule.onNodeWithTag("roulette_lazy_list").performScrollToNode(hasTestTag("roulette_debug_panel"))
    composeTestRule.onNodeWithTag("roulette_debug_reset").performClick()
    composeTestRule.waitForIdle()
    assertEquals(0, manager.uiState.value.totalSpins)
    composeTestRule.onNodeWithTag("roulette_lazy_list").performScrollToNode(hasTestTag("roulette_stat_spins"))
    composeTestRule.onNodeWithTag("roulette_stat_spins").assertTextContains("0")
  }
}
