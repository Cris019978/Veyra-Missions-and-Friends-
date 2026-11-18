package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.PlayerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.accountPrefsDataStore by preferencesDataStore(name = "veyra_account_prefs")

data class LocalAccountSnapshot(
  val isSessionSaved: Boolean = false,
  val authMode: String = "none",
  val explicitlySignedOut: Boolean = false,
  val sessionId: String = "",
  val email: String = "",
  val savedProfile: PlayerProfile? = null,
)

class LocalAccountStore(private val context: Context) {

  private object Keys {
    val IS_SESSION_SAVED = booleanPreferencesKey("is_session_saved")
    val AUTH_MODE = stringPreferencesKey("auth_mode")
    val EXPLICITLY_SIGNED_OUT = booleanPreferencesKey("explicitly_signed_out")
    val SESSION_ID = stringPreferencesKey("session_id")
    val EMAIL = stringPreferencesKey("saved_email")

    val USER_ID = stringPreferencesKey("profile_user_id")
    val USERNAME = stringPreferencesKey("profile_username")
    val CALLSIGN = stringPreferencesKey("profile_callsign")
    val AVATAR_ICON = stringPreferencesKey("profile_avatar_icon")
    val AVATAR_COLOR_HEX = stringPreferencesKey("profile_avatar_color_hex")
    val AVATAR_PHOTO_BASE64 = stringPreferencesKey("profile_avatar_photo_base64")
    val BANNER_PHOTO_BASE64 = stringPreferencesKey("profile_banner_photo_base64")
    val BANNER_PRESET = stringPreferencesKey("profile_banner_preset")
    val STATUS_MESSAGE = stringPreferencesKey("profile_status_message")
    val SEEKING_HELP = booleanPreferencesKey("profile_seeking_help")
    val IS_ONLINE = booleanPreferencesKey("profile_is_online")
    val LATITUDE_STR = stringPreferencesKey("profile_latitude")
    val LONGITUDE_STR = stringPreferencesKey("profile_longitude")
    val XP = longPreferencesKey("profile_xp")
    val LEVEL = longPreferencesKey("profile_level")
    val MISSIONS_COMPLETED = longPreferencesKey("profile_missions_completed")
    val HELPS_GIVEN = longPreferencesKey("profile_helps_given")
    val DUELS_WON = longPreferencesKey("profile_duels_won")
  }

  val accountFlow: Flow<LocalAccountSnapshot> = context.accountPrefsDataStore.data.map { prefs ->
    val isSaved = prefs[Keys.IS_SESSION_SAVED] ?: false
    val uid = prefs[Keys.USER_ID].orEmpty()
    val profile = if (uid.isNotBlank()) {
      PlayerProfile(
        id = uid,
        userId = uid,
        username = prefs[Keys.USERNAME].orEmpty(),
        callsign = prefs[Keys.CALLSIGN].orEmpty().ifBlank { "Explorador" },
        avatarIcon = prefs[Keys.AVATAR_ICON].orEmpty().ifBlank { "explore" },
        avatarColorHex = prefs[Keys.AVATAR_COLOR_HEX].orEmpty().ifBlank { "#00BFA5" },
        avatarPhotoBase64 = prefs[Keys.AVATAR_PHOTO_BASE64].orEmpty(),
        bannerPhotoBase64 = prefs[Keys.BANNER_PHOTO_BASE64].orEmpty(),
        bannerPreset = prefs[Keys.BANNER_PRESET].orEmpty().ifBlank { "topo" },
        statusMessage = prefs[Keys.STATUS_MESSAGE].orEmpty().ifBlank { "Listo para misiones" },
        seekingHelp = prefs[Keys.SEEKING_HELP] ?: false,
        isOnline = prefs[Keys.IS_ONLINE] ?: true,
        latitude = prefs[Keys.LATITUDE_STR]?.toDoubleOrNull() ?: 0.0,
        longitude = prefs[Keys.LONGITUDE_STR]?.toDoubleOrNull() ?: 0.0,
        xp = prefs[Keys.XP] ?: 0L,
        level = prefs[Keys.LEVEL] ?: 1L,
        missionsCompleted = prefs[Keys.MISSIONS_COMPLETED] ?: 0L,
        helpsGiven = prefs[Keys.HELPS_GIVEN] ?: 0L,
        duelsWon = prefs[Keys.DUELS_WON] ?: 0L,
      )
    } else {
      null
    }

    LocalAccountSnapshot(
      isSessionSaved = isSaved,
      authMode = prefs[Keys.AUTH_MODE] ?: "none",
      explicitlySignedOut = prefs[Keys.EXPLICITLY_SIGNED_OUT] ?: false,
      sessionId = prefs[Keys.SESSION_ID].orEmpty(),
      email = prefs[Keys.EMAIL].orEmpty(),
      savedProfile = profile,
    )
  }

  suspend fun getSnapshotOnce(): LocalAccountSnapshot = accountFlow.first()

  suspend fun markSessionSaved(
    authMode: String,
    userId: String,
    email: String = "",
    defaultDisplayName: String = "Explorador",
  ) {
    context.accountPrefsDataStore.edit { prefs ->
      val currentSessionId = prefs[Keys.SESSION_ID].orEmpty()
      val shouldRenewSessionId = currentSessionId.isBlank() ||
        prefs[Keys.USER_ID] != userId ||
        prefs[Keys.AUTH_MODE] != authMode
      if (shouldRenewSessionId) {
        prefs[Keys.SESSION_ID] = UUID.randomUUID().toString().take(12)
      }
      prefs[Keys.IS_SESSION_SAVED] = true
      prefs[Keys.AUTH_MODE] = authMode
      prefs[Keys.EXPLICITLY_SIGNED_OUT] = false
      if (email.isNotBlank()) {
        prefs[Keys.EMAIL] = email
      }
      if (prefs[Keys.USER_ID].isNullOrBlank() || prefs[Keys.USER_ID] != userId) {
        prefs[Keys.USER_ID] = userId
        if (prefs[Keys.CALLSIGN].isNullOrBlank()) {
          prefs[Keys.CALLSIGN] = defaultDisplayName.ifBlank { "Explorador" }.take(32)
        }
        if (prefs[Keys.USERNAME].isNullOrBlank()) {
          prefs[Keys.USERNAME] = defaultDisplayName.lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
            .ifBlank { "explorador" }
            .take(18)
        }
      }
    }
  }

  suspend fun saveProfile(profile: PlayerProfile) {
    context.accountPrefsDataStore.edit { prefs ->
      if (prefs[Keys.SESSION_ID].isNullOrBlank()) {
        prefs[Keys.SESSION_ID] = UUID.randomUUID().toString().take(12)
      }
      prefs[Keys.IS_SESSION_SAVED] = true
      prefs[Keys.EXPLICITLY_SIGNED_OUT] = false
      if (prefs[Keys.AUTH_MODE].isNullOrBlank() || prefs[Keys.AUTH_MODE] == "none") {
        prefs[Keys.AUTH_MODE] = "local"
      }
      prefs[Keys.USER_ID] = profile.userId.ifBlank { profile.id }.ifBlank { "local_explorer" }
      prefs[Keys.USERNAME] = profile.username
      prefs[Keys.CALLSIGN] = profile.callsign.ifBlank { "Explorador" }
      prefs[Keys.AVATAR_ICON] = profile.avatarIcon.ifBlank { "explore" }
      prefs[Keys.AVATAR_COLOR_HEX] = profile.avatarColorHex.ifBlank { "#00BFA5" }
      prefs[Keys.AVATAR_PHOTO_BASE64] = profile.avatarPhotoBase64.take(80000)
      prefs[Keys.BANNER_PHOTO_BASE64] = profile.bannerPhotoBase64.take(80000)
      prefs[Keys.BANNER_PRESET] = profile.bannerPreset.ifBlank { "topo" }
      prefs[Keys.STATUS_MESSAGE] = profile.statusMessage
      prefs[Keys.SEEKING_HELP] = profile.seekingHelp
      prefs[Keys.IS_ONLINE] = profile.isOnline
      prefs[Keys.LATITUDE_STR] = profile.latitude.toString()
      prefs[Keys.LONGITUDE_STR] = profile.longitude.toString()
      prefs[Keys.XP] = profile.xp
      prefs[Keys.LEVEL] = profile.level
      prefs[Keys.MISSIONS_COMPLETED] = profile.missionsCompleted
      prefs[Keys.HELPS_GIVEN] = profile.helpsGiven
      prefs[Keys.DUELS_WON] = profile.duelsWon
    }
  }

  suspend fun clearSavedAccount(markExplicitlySignedOut: Boolean = true) {
    context.accountPrefsDataStore.edit { prefs ->
      prefs.clear()
      if (markExplicitlySignedOut) {
        prefs[Keys.EXPLICITLY_SIGNED_OUT] = true
      }
    }
  }
}
