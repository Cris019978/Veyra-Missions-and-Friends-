package com.example.roulette

import android.content.Context
import android.content.SharedPreferences

data class RoulettePersistedSnapshot(
  val totalSpins: Int = 0,
  val accumulatedXp: Long = 0L,
  val bonusLevelsWon: Int = 0,
  val floorChargePercent: Int = 0,
  val floorBonusTriggers: Int = 0,
  val slotAVariantIndex: Int = 0,
  val slotBVariantIndex: Int = 1,
  val lastWonVariantIndex: Int = -1,
  val unlockedExclusiveTitles: List<String> = emptyList(),
  val pendingClaimTitles: List<String> = emptyList(),
  val spinMissionsProgress: Map<String, Pair<Int, Boolean>> = emptyMap(),
)

/**
 * Persistencia local ligera por usuario para el estado de la ruleta (títulos reclamables,
 * misiones de giros, variantes activas y carga de impulso).
 */
class RouletteLocalStore(
  context: Context,
  userId: String,
) {
  private val cleanUid = userId.trim().lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "local" }
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences("veyra_roulette_$cleanUid", Context.MODE_PRIVATE)

  fun load(): RoulettePersistedSnapshot {
    val missionsRaw = prefs.getString(KEY_MISSIONS_STATE, "").orEmpty()
    val missionsMap = buildMap {
      missionsRaw.split(";").forEach { entry ->
        val parts = entry.split(":")
        if (parts.size == 3) {
          val id = parts[0]
          val spins = parts[1].toIntOrNull() ?: 0
          val done = parts[2] == "1"
          if (id.isNotBlank()) put(id, spins to done)
        }
      }
    }
    return RoulettePersistedSnapshot(
      totalSpins = prefs.getInt(KEY_TOTAL_SPINS, 0),
      accumulatedXp = prefs.getLong(KEY_ACCUMULATED_XP, 0L),
      bonusLevelsWon = prefs.getInt(KEY_BONUS_LEVELS_WON, 0),
      floorChargePercent = prefs.getInt(KEY_FLOOR_CHARGE, 0),
      floorBonusTriggers = prefs.getInt(KEY_FLOOR_TRIGGERS, 0),
      slotAVariantIndex = prefs.getInt(KEY_SLOT_A_IDX, 0),
      slotBVariantIndex = prefs.getInt(KEY_SLOT_B_IDX, 1),
      lastWonVariantIndex = prefs.getInt(KEY_LAST_WON_IDX, -1),
      unlockedExclusiveTitles = decodeList(prefs.getString(KEY_UNLOCKED_TITLES, "").orEmpty()),
      pendingClaimTitles = decodeList(prefs.getString(KEY_PENDING_TITLES, "").orEmpty()),
      spinMissionsProgress = missionsMap,
    )
  }

  fun save(snapshot: RoulettePersistedSnapshot) {
    val encodedMissions = snapshot.spinMissionsProgress.entries.joinToString(";") { (id, state) ->
      "$id:${state.first}:${if (state.second) "1" else "0"}"
    }
    prefs.edit()
      .putInt(KEY_TOTAL_SPINS, snapshot.totalSpins)
      .putLong(KEY_ACCUMULATED_XP, snapshot.accumulatedXp)
      .putInt(KEY_BONUS_LEVELS_WON, snapshot.bonusLevelsWon)
      .putInt(KEY_FLOOR_CHARGE, snapshot.floorChargePercent)
      .putInt(KEY_FLOOR_TRIGGERS, snapshot.floorBonusTriggers)
      .putInt(KEY_SLOT_A_IDX, snapshot.slotAVariantIndex)
      .putInt(KEY_SLOT_B_IDX, snapshot.slotBVariantIndex)
      .putInt(KEY_LAST_WON_IDX, snapshot.lastWonVariantIndex)
      .putString(KEY_UNLOCKED_TITLES, snapshot.unlockedExclusiveTitles.distinct().joinToString("|"))
      .putString(KEY_PENDING_TITLES, snapshot.pendingClaimTitles.distinct().joinToString("|"))
      .putString(KEY_MISSIONS_STATE, encodedMissions)
      .apply()
  }

  fun clear() {
    prefs.edit().clear().apply()
  }

  private fun decodeList(raw: String): List<String> =
    raw.split("|").map { it.trim() }.filter { it.isNotBlank() }.distinct()

  private companion object {
    const val KEY_TOTAL_SPINS = "total_spins"
    const val KEY_ACCUMULATED_XP = "accumulated_xp"
    const val KEY_BONUS_LEVELS_WON = "bonus_levels_won"
    const val KEY_FLOOR_CHARGE = "floor_charge"
    const val KEY_FLOOR_TRIGGERS = "floor_triggers"
    const val KEY_SLOT_A_IDX = "slot_a_idx"
    const val KEY_SLOT_B_IDX = "slot_b_idx"
    const val KEY_LAST_WON_IDX = "last_won_idx"
    const val KEY_UNLOCKED_TITLES = "unlocked_titles"
    const val KEY_PENDING_TITLES = "pending_titles"
    const val KEY_MISSIONS_STATE = "missions_state"
  }
}
