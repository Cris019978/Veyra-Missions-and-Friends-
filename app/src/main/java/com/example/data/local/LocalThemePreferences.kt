package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.radarPrefsDataStore by preferencesDataStore(name = "veyra_theme_prefs")

data class ThemePrefsSnapshot(
  val themeMode: String = "system",
  val paletteKey: String = "dynamic",
  val radarStyle: String = "neon",
  val cornerStyle: String = "soft",
  val searchRadiusMeters: Long = 2000L,
  val animationTier: String = "high",
  val notificationsEnabled: Boolean = true,
  val playStyle: String = "balanced",
  val favoriteCategories: List<String> = emptyList(),
  val privacyComfort: String = "radius",
  val missionPace: String = "casual",
  val gestureNavEnabled: Boolean = false,
)

class LocalThemePreferences(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PALETTE_KEY = stringPreferencesKey("palette_key")
    val RADAR_STYLE = stringPreferencesKey("radar_style")
    val CORNER_STYLE = stringPreferencesKey("corner_style")
    val SEARCH_RADIUS = longPreferencesKey("search_radius_meters")
    val ANIMATION_TIER = stringPreferencesKey("animation_tier")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val PLAY_STYLE = stringPreferencesKey("play_style")
    val FAVORITE_CATEGORIES = stringPreferencesKey("favorite_categories")
    val PRIVACY_COMFORT = stringPreferencesKey("privacy_comfort")
    val MISSION_PACE = stringPreferencesKey("mission_pace")
    val GESTURE_NAV_ENABLED = booleanPreferencesKey("gesture_nav_enabled")
    fun onboardingDoneKey(uid: String) = booleanPreferencesKey("onboarding_done_$uid")
  }

  val prefsFlow: Flow<ThemePrefsSnapshot> = context.radarPrefsDataStore.data.map { prefs ->
    val rawCats = prefs[Keys.FAVORITE_CATEGORIES].orEmpty()
    val cats = rawCats.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    ThemePrefsSnapshot(
      themeMode = prefs[Keys.THEME_MODE] ?: "system",
      paletteKey = prefs[Keys.PALETTE_KEY] ?: "dynamic",
      radarStyle = prefs[Keys.RADAR_STYLE] ?: "neon",
      cornerStyle = prefs[Keys.CORNER_STYLE] ?: "soft",
      searchRadiusMeters = prefs[Keys.SEARCH_RADIUS] ?: 2000L,
      animationTier = prefs[Keys.ANIMATION_TIER] ?: "high",
      notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
      playStyle = prefs[Keys.PLAY_STYLE] ?: "balanced",
      favoriteCategories = cats,
      privacyComfort = prefs[Keys.PRIVACY_COMFORT] ?: "radius",
      missionPace = prefs[Keys.MISSION_PACE] ?: "casual",
      gestureNavEnabled = prefs[Keys.GESTURE_NAV_ENABLED] ?: false,
    )
  }

  fun observeOnboardingCompleted(uid: String): Flow<Boolean> =
    context.radarPrefsDataStore.data.map { prefs ->
      prefs[Keys.onboardingDoneKey(uid)] ?: false
    }

  suspend fun setOnboardingCompleted(uid: String, completed: Boolean) {
    context.radarPrefsDataStore.edit { prefs ->
      prefs[Keys.onboardingDoneKey(uid)] = completed
    }
  }

  suspend fun save(snapshot: ThemePrefsSnapshot) {
    context.radarPrefsDataStore.edit { prefs ->
      prefs[Keys.THEME_MODE] = snapshot.themeMode
      prefs[Keys.PALETTE_KEY] = snapshot.paletteKey
      prefs[Keys.RADAR_STYLE] = snapshot.radarStyle
      prefs[Keys.CORNER_STYLE] = snapshot.cornerStyle
      prefs[Keys.SEARCH_RADIUS] = snapshot.searchRadiusMeters
      prefs[Keys.ANIMATION_TIER] = snapshot.animationTier
      prefs[Keys.NOTIFICATIONS_ENABLED] = snapshot.notificationsEnabled
      prefs[Keys.PLAY_STYLE] = snapshot.playStyle
      prefs[Keys.FAVORITE_CATEGORIES] = snapshot.favoriteCategories.distinct().take(6).joinToString(",")
      prefs[Keys.PRIVACY_COMFORT] = snapshot.privacyComfort
      prefs[Keys.MISSION_PACE] = snapshot.missionPace
      prefs[Keys.GESTURE_NAV_ENABLED] = snapshot.gestureNavEnabled
    }
  }

  suspend fun clearAll() {
    context.radarPrefsDataStore.edit { prefs ->
      prefs.clear()
    }
  }
}
