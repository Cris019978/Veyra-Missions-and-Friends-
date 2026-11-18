package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class LocalAuthSession(
  val isLoggedIn: Boolean = false,
  val username: String = "",
  val pinHash: String = "",
  val role: String = LocalAuthManager.ROLE_EXPLORER,
  val roleTitle: String = LocalAuthManager.TITLE_EXPLORER,
  val xp: Long = 0L,
  val level: Long = 1L,
)

sealed class LocalAuthResult {
  data class Success(val session: LocalAuthSession) : LocalAuthResult()
  data class Error(val message: String) : LocalAuthResult()
}

/**
 * 100% local, zero-dependency identity verification using Android SharedPreferences + SHA-256 PIN hashing.
 */
class LocalAuthManager(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _sessionFlow = MutableStateFlow(readActiveSession())
  val sessionFlow: StateFlow<LocalAuthSession?> = _sessionFlow.asStateFlow()

  fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false) &&
    prefs.getString(KEY_ACTIVE_USERNAME, "").orEmpty().isNotBlank()

  fun getCurrentSession(): LocalAuthSession? = readActiveSession()

  fun userExists(rawUsername: String): Boolean {
    val clean = normalizeUsername(rawUsername)
    return clean.isNotBlank() && !prefs.getString(userPinHashKey(clean), null).isNullOrBlank()
  }

  fun registerAccount(
    rawUsername: String,
    pin: String,
    inviteCode: String = "",
  ): LocalAuthResult {
    val username = normalizeUsername(rawUsername)
    if (username.length !in 3..24 || !USERNAME_REGEX.matches(username)) {
      return LocalAuthResult.Error(
        "El usuario debe tener entre 3 y 24 caracteres (solo letras, números o _)."
      )
    }
    if (pin.trim().length < 4) {
      return LocalAuthResult.Error(
        "El PIN o contraseña corta debe tener mínimo 4 caracteres."
      )
    }
    if (userExists(username)) {
      return LocalAuthResult.Error(
        "El usuario @$username ya existe. Cambia a Iniciar Sesión o elige otro."
      )
    }

    val pinHash = sha256(pin.trim())
    val (role, roleTitle) = resolveRoleAndTitle(username, inviteCode)
    val initialXp = 0L
    val initialLevel = 1L

    prefs.edit()
      .putString(userPinHashKey(username), pinHash)
      .putString(userRoleKey(username), role)
      .putString(userTitleKey(username), roleTitle)
      .putLong(userXpKey(username), initialXp)
      .putLong(userLevelKey(username), initialLevel)
      .putBoolean(KEY_IS_LOGGED_IN, true)
      .putString(KEY_ACTIVE_USERNAME, username)
      .putString(KEY_ACTIVE_PIN_HASH, pinHash)
      .putString(KEY_ACTIVE_ROLE, role)
      .putString(KEY_ACTIVE_ROLE_TITLE, roleTitle)
      .putLong(KEY_ACTIVE_XP, initialXp)
      .putLong(KEY_ACTIVE_LEVEL, initialLevel)
      .commit()

    val session = LocalAuthSession(
      isLoggedIn = true,
      username = username,
      pinHash = pinHash,
      role = role,
      roleTitle = roleTitle,
      xp = initialXp,
      level = initialLevel,
    )
    _sessionFlow.value = session
    return LocalAuthResult.Success(session)
  }

  fun login(
    rawUsername: String,
    pin: String,
  ): LocalAuthResult {
    val username = normalizeUsername(rawUsername)
    if (username.length !in 3..24 || !USERNAME_REGEX.matches(username)) {
      return LocalAuthResult.Error(
        "Ingresa un nombre de usuario válido (ej. @explorador)."
      )
    }
    if (pin.trim().length < 4) {
      return LocalAuthResult.Error(
        "Ingresa tu PIN o contraseña corta (mínimo 4 caracteres)."
      )
    }

    val storedHash = prefs.getString(userPinHashKey(username), null)
    if (storedHash.isNullOrBlank()) {
      return LocalAuthResult.Error(
        "No existe @$username en este dispositivo. Toca 'Crear Cuenta' para registrarlo."
      )
    }

    val providedHash = sha256(pin.trim())
    if (storedHash != providedHash) {
      return LocalAuthResult.Error("PIN o contraseña incorrecta para @$username.")
    }

    val (defaultRole, defaultTitle) = resolveRoleAndTitle(username, "")
    val role = prefs.getString(userRoleKey(username), defaultRole) ?: defaultRole
    val roleTitle = prefs.getString(userTitleKey(username), defaultTitle) ?: defaultTitle
    val xp = prefs.getLong(userXpKey(username), 0L)
    val level = prefs.getLong(userLevelKey(username), 1L)

    prefs.edit()
      .putBoolean(KEY_IS_LOGGED_IN, true)
      .putString(KEY_ACTIVE_USERNAME, username)
      .putString(KEY_ACTIVE_PIN_HASH, storedHash)
      .putString(KEY_ACTIVE_ROLE, role)
      .putString(KEY_ACTIVE_ROLE_TITLE, roleTitle)
      .putLong(KEY_ACTIVE_XP, xp)
      .putLong(KEY_ACTIVE_LEVEL, level)
      .commit()

    val session = LocalAuthSession(
      isLoggedIn = true,
      username = username,
      pinHash = storedHash,
      role = role,
      roleTitle = roleTitle,
      xp = xp,
      level = level,
    )
    _sessionFlow.value = session
    return LocalAuthResult.Success(session)
  }

  fun updateActiveStats(xp: Long, level: Long) {
    val current = readActiveSession() ?: return
    val safeXp = xp.coerceAtLeast(0L)
    val safeLevel = level.coerceAtLeast(1L)
    prefs.edit()
      .putLong(KEY_ACTIVE_XP, safeXp)
      .putLong(KEY_ACTIVE_LEVEL, safeLevel)
      .putLong(userXpKey(current.username), safeXp)
      .putLong(userLevelKey(current.username), safeLevel)
      .apply()
    _sessionFlow.value = current.copy(xp = safeXp, level = safeLevel)
  }

  fun logout() {
    prefs.edit()
      .putBoolean(KEY_IS_LOGGED_IN, false)
      .remove(KEY_ACTIVE_USERNAME)
      .remove(KEY_ACTIVE_PIN_HASH)
      .remove(KEY_ACTIVE_ROLE)
      .remove(KEY_ACTIVE_ROLE_TITLE)
      .remove(KEY_ACTIVE_XP)
      .remove(KEY_ACTIVE_LEVEL)
      .commit()
    _sessionFlow.value = null
  }

  fun clearAllAccountsForTest() {
    prefs.edit().clear().commit()
    _sessionFlow.value = null
  }

  private fun readActiveSession(): LocalAuthSession? {
    val loggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    val username = prefs.getString(KEY_ACTIVE_USERNAME, "").orEmpty()
    if (!loggedIn || username.isBlank()) return null
    val role = prefs.getString(KEY_ACTIVE_ROLE, ROLE_EXPLORER) ?: ROLE_EXPLORER
    val roleTitle = prefs.getString(KEY_ACTIVE_ROLE_TITLE, TITLE_EXPLORER) ?: TITLE_EXPLORER
    return LocalAuthSession(
      isLoggedIn = true,
      username = username,
      pinHash = prefs.getString(KEY_ACTIVE_PIN_HASH, "").orEmpty(),
      role = role,
      roleTitle = roleTitle,
      xp = prefs.getLong(KEY_ACTIVE_XP, 0L),
      level = prefs.getLong(KEY_ACTIVE_LEVEL, 1L),
    )
  }

  companion object {
    private const val PREFS_NAME = "veyra_local_auth_prefs"
    const val KEY_IS_LOGGED_IN = "isLoggedIn"
    private const val KEY_ACTIVE_USERNAME = "active_username"
    private const val KEY_ACTIVE_PIN_HASH = "active_pin_hash"
    private const val KEY_ACTIVE_ROLE = "active_role"
    private const val KEY_ACTIVE_ROLE_TITLE = "active_role_title"
    private const val KEY_ACTIVE_XP = "active_xp"
    private const val KEY_ACTIVE_LEVEL = "active_level"

    const val ROLE_EXPLORER = "explorer"
    const val ROLE_CREATOR = "creator"
    const val ROLE_BETA_TESTER = "beta_tester"

    const val TITLE_EXPLORER = "Explorador"
    const val TITLE_CREATOR = "Creador"
    const val TITLE_BETA_TESTER = "Beta Tester"

    // Admin / Creator handles (strip @, lowercase)
    val CREATOR_USERNAMES = setOf("veyra", "admin_veyra", "cesquivel")

    // Predefined Beta Tester usernames & invite codes
    val BETA_TESTER_USERNAMES = setOf("tester1", "tester2", "beta_tester", "veyra_beta")
    val BETA_INVITE_CODES = setOf("VEYRA-BETA", "BETA2025", "VEYRA2026")

    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,24}$")

    fun normalizeUsername(raw: String): String =
      raw.trim().removePrefix("@").lowercase()

    fun resolveRoleAndTitle(normalizedUsername: String, inviteCode: String = ""): Pair<String, String> {
      val cleanCode = inviteCode.trim().uppercase()
      return when {
        normalizedUsername in CREATOR_USERNAMES -> ROLE_CREATOR to TITLE_CREATOR
        normalizedUsername in BETA_TESTER_USERNAMES || cleanCode in BETA_INVITE_CODES ->
          ROLE_BETA_TESTER to TITLE_BETA_TESTER
        else -> ROLE_EXPLORER to TITLE_EXPLORER
      }
    }

    fun sha256(input: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }

    private fun userPinHashKey(username: String) = "user_pin_hash_$username"
    private fun userRoleKey(username: String) = "user_role_$username"
    private fun userTitleKey(username: String) = "user_title_$username"
    private fun userXpKey(username: String) = "user_xp_$username"
    private fun userLevelKey(username: String) = "user_level_$username"
  }
}
