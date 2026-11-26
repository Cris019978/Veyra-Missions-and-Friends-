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

enum class SecretCodeSection(val title: String) {
  BETA_TESTERS("Beta Testers"),
  SPECIAL_TITLES("Títulos Especiales"),
}

data class SecretCodeEntry(
  val code: String,
  val role: String,
  val roleTitle: String,
  val section: SecretCodeSection,
  val description: String,
)

/**
 * 100% local, zero-dependency identity verification using Android SharedPreferences + SHA-256 PIN hashing.
 */
class LocalAuthManager(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _sessionFlow = MutableStateFlow(readActiveSession())
  val sessionFlow: StateFlow<LocalAuthSession?> = _sessionFlow.asStateFlow()

  private val _savedUsernamesFlow = MutableStateFlow(readSavedUsernames())
  val savedUsernamesFlow: StateFlow<List<String>> = _savedUsernamesFlow.asStateFlow()

  fun getSavedUsernames(): List<String> = _savedUsernamesFlow.value

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

    val updatedUsernames = buildUpdatedSavedUsernames(username)
    prefs.edit()
      .putString(userPinHashKey(username), pinHash)
      .putString(userRoleKey(username), role)
      .putString(userTitleKey(username), roleTitle)
      .putLong(userXpKey(username), initialXp)
      .putLong(userLevelKey(username), initialLevel)
      .putString(KEY_SAVED_USERNAMES, updatedUsernames.joinToString(","))
      .putBoolean(KEY_IS_LOGGED_IN, true)
      .putString(KEY_ACTIVE_USERNAME, username)
      .putString(KEY_ACTIVE_PIN_HASH, pinHash)
      .putString(KEY_ACTIVE_ROLE, role)
      .putString(KEY_ACTIVE_ROLE_TITLE, roleTitle)
      .putLong(KEY_ACTIVE_XP, initialXp)
      .putLong(KEY_ACTIVE_LEVEL, initialLevel)
      .apply()

    val session = LocalAuthSession(
      isLoggedIn = true,
      username = username,
      pinHash = pinHash,
      role = role,
      roleTitle = roleTitle,
      xp = initialXp,
      level = initialLevel,
    )
    _savedUsernamesFlow.value = updatedUsernames
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
    val (role, roleTitle) = if (defaultRole != ROLE_EXPLORER) {
      prefs.edit()
        .putString(userRoleKey(username), defaultRole)
        .putString(userTitleKey(username), defaultTitle)
        .apply()
      defaultRole to defaultTitle
    } else {
      val storedRole = prefs.getString(userRoleKey(username), defaultRole) ?: defaultRole
      val storedTitle = prefs.getString(userTitleKey(username), defaultTitle) ?: defaultTitle
      storedRole to storedTitle
    }
    val xp = prefs.getLong(userXpKey(username), 0L)
    val level = prefs.getLong(userLevelKey(username), 1L)

    val updatedUsernames = buildUpdatedSavedUsernames(username)
    prefs.edit()
      .putString(KEY_SAVED_USERNAMES, updatedUsernames.joinToString(","))
      .putBoolean(KEY_IS_LOGGED_IN, true)
      .putString(KEY_ACTIVE_USERNAME, username)
      .putString(KEY_ACTIVE_PIN_HASH, storedHash)
      .putString(KEY_ACTIVE_ROLE, role)
      .putString(KEY_ACTIVE_ROLE_TITLE, roleTitle)
      .putLong(KEY_ACTIVE_XP, xp)
      .putLong(KEY_ACTIVE_LEVEL, level)
      .apply()

    val session = LocalAuthSession(
      isLoggedIn = true,
      username = username,
      pinHash = storedHash,
      role = role,
      roleTitle = roleTitle,
      xp = xp,
      level = level,
    )
    _savedUsernamesFlow.value = updatedUsernames
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

  fun updateActiveRole(role: String, roleTitle: String) {
    val current = readActiveSession() ?: return
    val safeRole = role.ifBlank { ROLE_EXPLORER }
    val safeTitle = roleTitle.ifBlank { TITLE_EXPLORER }
    prefs.edit()
      .putString(KEY_ACTIVE_ROLE, safeRole)
      .putString(KEY_ACTIVE_ROLE_TITLE, safeTitle)
      .putString(userRoleKey(current.username), safeRole)
      .putString(userTitleKey(current.username), safeTitle)
      .apply()
    _sessionFlow.value = current.copy(role = safeRole, roleTitle = safeTitle)
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
      .apply()
    _sessionFlow.value = null
  }

  fun clearAllAccountsForTest() {
    prefs.edit().clear().commit()
    _savedUsernamesFlow.value = emptyList()
    _sessionFlow.value = null
  }

  private fun readSavedUsernames(): List<String> {
    val csv = prefs.getString(KEY_SAVED_USERNAMES, "").orEmpty()
    val fromCsv = csv.split(",")
      .map { it.trim() }
      .filter { it.isNotBlank() && !prefs.getString(userPinHashKey(it), null).isNullOrBlank() }
    if (fromCsv.isNotEmpty()) return fromCsv.distinct().take(4)

    return prefs.all.keys
      .asSequence()
      .filter { it.startsWith("user_pin_hash_") }
      .map { it.removePrefix("user_pin_hash_") }
      .filter { it.isNotBlank() }
      .distinct()
      .take(4)
      .toList()
  }

  private fun buildUpdatedSavedUsernames(latestUsername: String): List<String> {
    val clean = normalizeUsername(latestUsername)
    if (clean.isBlank()) return _savedUsernamesFlow.value
    return (listOf(clean) + _savedUsernamesFlow.value.filterNot { it == clean }).take(4)
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
    private const val KEY_SAVED_USERNAMES = "saved_usernames_csv"
    private const val KEY_ACTIVE_USERNAME = "active_username"
    private const val KEY_ACTIVE_PIN_HASH = "active_pin_hash"
    private const val KEY_ACTIVE_ROLE = "active_role"
    private const val KEY_ACTIVE_ROLE_TITLE = "active_role_title"
    private const val KEY_ACTIVE_XP = "active_xp"
    private const val KEY_ACTIVE_LEVEL = "active_level"

    const val ROLE_EXPLORER = "explorer"
    const val ROLE_CREATOR = "creator"
    const val ROLE_BETA_TESTER = "beta_tester"
    const val ROLE_PALMGYM = "palmgym_cr"
    const val ROLE_CURIOUS_EXPLORER = "curious_explorer"
    const val ROLE_NIGHT_RANGER = "night_ranger"
    const val ROLE_VANGUARD = "vanguard"

    const val TITLE_EXPLORER = "Explorer"
    const val TITLE_CREATOR = "Creador"
    const val TITLE_BETA_TESTER = "Beta Tester"
    const val TITLE_PALMGYM = "Palmgym_CR"
    const val TITLE_CURIOUS_EXPLORER = "Explorador Trifuerza"
    const val TITLE_NIGHT_RANGER = "Centinela Aurora"
    const val TITLE_VANGUARD = "Vanguardia 1.0"

    const val MSG_BETA_TESTER_UNLOCKED = "Felicidades, Eres un beta tester"
    const val MSG_CURIOUS_EXPLORER_UNLOCKED = "Felicidades eres un Explorador curioso"

    val SECRET_CODE_CATALOG: List<SecretCodeEntry> = listOf(
      SecretCodeEntry(
        code = "VEYRA-BETA",
        role = ROLE_BETA_TESTER,
        roleTitle = TITLE_BETA_TESTER,
        section = SecretCodeSection.BETA_TESTERS,
        description = "Acceso clásico de pruebas tempranas",
      ),
      SecretCodeEntry(
        code = "BETA2025",
        role = ROLE_BETA_TESTER,
        roleTitle = TITLE_BETA_TESTER,
        section = SecretCodeSection.BETA_TESTERS,
        description = "Insignia de fundadores Beta 2025",
      ),
      SecretCodeEntry(
        code = "VEYRA2026",
        role = ROLE_BETA_TESTER,
        roleTitle = TITLE_BETA_TESTER,
        section = SecretCodeSection.BETA_TESTERS,
        description = "Cohorte de pruebas Veyra 2026",
      ),
      SecretCodeEntry(
        code = "BETA_VEYRA",
        role = ROLE_BETA_TESTER,
        roleTitle = TITLE_BETA_TESTER,
        section = SecretCodeSection.BETA_TESTERS,
        description = "Código directo Beta Tester Veyra",
      ),
      SecretCodeEntry(
        code = "PALMGYM-CR",
        role = ROLE_PALMGYM,
        roleTitle = TITLE_PALMGYM,
        section = SecretCodeSection.SPECIAL_TITLES,
        description = "Título oficial del club Palmgym",
      ),
      SecretCodeEntry(
        code = "TRIFORCE-7",
        role = ROLE_CURIOUS_EXPLORER,
        roleTitle = TITLE_CURIOUS_EXPLORER,
        section = SecretCodeSection.SPECIAL_TITLES,
        description = "Desbloqueado por el secreto triangular",
      ),
      SecretCodeEntry(
        code = "AURORA-XP",
        role = ROLE_NIGHT_RANGER,
        roleTitle = TITLE_NIGHT_RANGER,
        section = SecretCodeSection.SPECIAL_TITLES,
        description = "Rastreador de rutas ocultas",
      ),
      SecretCodeEntry(
        code = "VANGUARD-1",
        role = ROLE_VANGUARD,
        roleTitle = TITLE_VANGUARD,
        section = SecretCodeSection.SPECIAL_TITLES,
        description = "Pionero de la edición 1.0-Alpha",
      ),
    )

    // Admin / Creator handles (strip @, lowercase)
    val CREATOR_USERNAMES = setOf("veyra", "admin_veyra", "cesquivel")

    // Partner / Special club handles
    val PALMGYM_USERNAMES = setOf("palmgym")

    // Predefined Beta Tester usernames & invite codes
    val BETA_TESTER_USERNAMES = setOf("tester1", "tester2", "beta_tester", "veyra_beta")
    val BETA_INVITE_CODES = SECRET_CODE_CATALOG
      .filter { it.section == SecretCodeSection.BETA_TESTERS }
      .map { it.code }
      .toSet()

    private val USERNAME_REGEX = Regex("^[a-z0-9_]{3,24}$")

    fun normalizeUsername(raw: String): String =
      raw.trim().removePrefix("@").lowercase()

    fun findSecretCodeEntry(inviteCode: String): SecretCodeEntry? {
      val cleanCode = inviteCode.trim().uppercase()
      if (cleanCode.isBlank()) return null
      return SECRET_CODE_CATALOG.firstOrNull { it.code == cleanCode }
    }

    fun celebrationMessageForCode(inviteCode: String): String? {
      val entry = findSecretCodeEntry(inviteCode) ?: return null
      return when (entry.section) {
        SecretCodeSection.BETA_TESTERS -> MSG_BETA_TESTER_UNLOCKED
        SecretCodeSection.SPECIAL_TITLES -> MSG_CURIOUS_EXPLORER_UNLOCKED
      }
    }

    fun resolveRoleAndTitle(normalizedUsername: String, inviteCode: String = ""): Pair<String, String> {
      val cleanUsername = normalizeUsername(normalizedUsername)
      val matchedCode = findSecretCodeEntry(inviteCode)
      return when {
        cleanUsername in CREATOR_USERNAMES -> ROLE_CREATOR to TITLE_CREATOR
        cleanUsername in PALMGYM_USERNAMES -> ROLE_PALMGYM to TITLE_PALMGYM
        cleanUsername in BETA_TESTER_USERNAMES -> ROLE_BETA_TESTER to TITLE_BETA_TESTER
        matchedCode != null -> matchedCode.role to matchedCode.roleTitle
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
