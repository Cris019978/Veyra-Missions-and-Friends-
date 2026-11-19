package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StreakDayDot(
  val dayKey: String,
  val dayInitial: String,
  val dayNumber: String,
  val isToday: Boolean,
  val isCompleted: Boolean,
)

data class DailyStreakState(
  val currentStreakDays: Int = 0,
  val bestStreakDays: Int = 0,
  val completedToday: Boolean = false,
  val missionsCompletedToday: Int = 0,
  val totalActiveDays: Int = 0,
  val lastCompletedDayKey: String = "",
  val completedDayKeys: Set<String> = emptySet(),
  val recentSevenDays: List<StreakDayDot> = emptyList(),
  val nextMilestoneDays: Int = 3,
  val xpBoostLabel: String = "1.0x",
)

/**
 * Tracks consecutive daily mission completions locally per user in SharedPreferences.
 * Uses Calendar + yyyy-MM-dd keys for full API 24+ compatibility without desugaring issues.
 */
class DailyStreakTracker(
  context: Context,
  private val userId: String,
  private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val safeUid = userId.trim().ifBlank { "local_explorer" }
  private val _streakFlow = MutableStateFlow(computeCurrentState(nowMillisProvider()))
  val streakFlow: StateFlow<DailyStreakState> = _streakFlow.asStateFlow()

  fun refresh(): DailyStreakState {
    val updated = computeCurrentState(nowMillisProvider())
    _streakFlow.value = updated
    return updated
  }

  fun recordMissionCompleted(atEpochMillis: Long = nowMillisProvider()): DailyStreakState {
    val todayKey = dayKeyFor(atEpochMillis)
    val yesterdayKey = previousDayKey(todayKey)
    val storedLastKey = prefs.getString(keyLastDay(), "").orEmpty()
    val storedCurrent = prefs.getInt(keyCurrentStreak(), 0)
    val storedBest = prefs.getInt(keyBestStreak(), 0)
    val storedTodayCount = prefs.getInt(keyTodayCount(), 0)
    val storedTodayKey = prefs.getString(keyTodayCountDate(), "").orEmpty()
    val storedHistory = prefs.getStringSet(keyHistoryDays(), emptySet()).orEmpty().toMutableSet()

    val newTodayCount = if (storedTodayKey == todayKey) storedTodayCount + 1 else 1
    val newCurrentStreak = when (storedLastKey) {
      todayKey -> storedCurrent.coerceAtLeast(1)
      yesterdayKey -> storedCurrent.coerceAtLeast(0) + 1
      else -> 1
    }
    val newBestStreak = maxOf(storedBest, newCurrentStreak)
    storedHistory.add(todayKey)
    val trimmedHistory = storedHistory.sortedDescending().take(90).toSet()

    prefs.edit()
      .putString(keyLastDay(), todayKey)
      .putInt(keyCurrentStreak(), newCurrentStreak)
      .putInt(keyBestStreak(), newBestStreak)
      .putInt(keyTodayCount(), newTodayCount)
      .putString(keyTodayCountDate(), todayKey)
      .putStringSet(keyHistoryDays(), trimmedHistory)
      .commit()

    val updated = computeCurrentState(atEpochMillis)
    _streakFlow.value = updated
    return updated
  }

  fun clearForTest() {
    prefs.edit()
      .remove(keyLastDay())
      .remove(keyCurrentStreak())
      .remove(keyBestStreak())
      .remove(keyTodayCount())
      .remove(keyTodayCountDate())
      .remove(keyHistoryDays())
      .commit()
    _streakFlow.value = computeCurrentState(nowMillisProvider())
  }

  private fun computeCurrentState(nowMillis: Long): DailyStreakState {
    val todayKey = dayKeyFor(nowMillis)
    val yesterdayKey = previousDayKey(todayKey)
    val storedLastKey = prefs.getString(keyLastDay(), "").orEmpty()
    val storedCurrent = prefs.getInt(keyCurrentStreak(), 0)
    val storedBest = prefs.getInt(keyBestStreak(), 0)
    val storedTodayKey = prefs.getString(keyTodayCountDate(), "").orEmpty()
    val storedTodayCount = if (storedTodayKey == todayKey) prefs.getInt(keyTodayCount(), 0) else 0
    val history = prefs.getStringSet(keyHistoryDays(), emptySet()).orEmpty()

    val completedToday = storedLastKey == todayKey
    val effectiveStreak = when (storedLastKey) {
      todayKey, yesterdayKey -> storedCurrent.coerceAtLeast(if (completedToday) 1 else 0)
      else -> 0
    }
    val effectiveBest = maxOf(storedBest, effectiveStreak)
    val recentSeven = buildRecentSevenDays(nowMillis, history, todayKey)

    return DailyStreakState(
      currentStreakDays = effectiveStreak,
      bestStreakDays = effectiveBest,
      completedToday = completedToday,
      missionsCompletedToday = storedTodayCount,
      totalActiveDays = history.size,
      lastCompletedDayKey = storedLastKey,
      completedDayKeys = history,
      recentSevenDays = recentSeven,
      nextMilestoneDays = nextMilestoneFor(effectiveStreak),
      xpBoostLabel = xpBoostFor(effectiveStreak),
    )
  }

  private fun buildRecentSevenDays(
    nowMillis: Long,
    history: Set<String>,
    todayKey: String,
  ): List<StreakDayDot> {
    val result = ArrayList<StreakDayDot>(7)
    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    cal.add(Calendar.DAY_OF_YEAR, -6)
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val numFmt = SimpleDateFormat("d", Locale.US)
    for (i in 0 until 7) {
      val dk = fmt.format(Date(cal.timeInMillis))
      result.add(
        StreakDayDot(
          dayKey = dk,
          dayInitial = spanishDayInitial(cal.get(Calendar.DAY_OF_WEEK)),
          dayNumber = numFmt.format(Date(cal.timeInMillis)),
          isToday = dk == todayKey,
          isCompleted = dk in history,
        )
      )
      cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return result
  }

  private fun keyLastDay() = "streak_last_day_$safeUid"
  private fun keyCurrentStreak() = "streak_current_$safeUid"
  private fun keyBestStreak() = "streak_best_$safeUid"
  private fun keyTodayCount() = "streak_today_count_$safeUid"
  private fun keyTodayCountDate() = "streak_today_date_$safeUid"
  private fun keyHistoryDays() = "streak_history_$safeUid"

  companion object {
    private const val PREFS_NAME = "veyra_daily_streak_prefs"

    fun dayKeyFor(epochMillis: Long): String {
      val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      return fmt.format(Date(epochMillis))
    }

    fun previousDayKey(dayKey: String): String {
      val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
      val parsed = runCatching { fmt.parse(dayKey) }.getOrNull() ?: return ""
      val cal = Calendar.getInstance().apply {
        time = parsed
        add(Calendar.DAY_OF_YEAR, -1)
      }
      return fmt.format(cal.time)
    }

    fun nextMilestoneFor(streakDays: Int): Int = when {
      streakDays < 3 -> 3
      streakDays < 7 -> 7
      streakDays < 14 -> 14
      streakDays < 30 -> 30
      streakDays < 50 -> 50
      streakDays < 100 -> 100
      else -> ((streakDays / 50) + 1) * 50
    }

    fun xpBoostFor(streakDays: Int): String = when {
      streakDays >= 14 -> "2.0x"
      streakDays >= 7 -> "1.5x"
      streakDays >= 3 -> "1.2x"
      else -> "1.0x"
    }

    private fun spanishDayInitial(dayOfWeek: Int): String = when (dayOfWeek) {
      Calendar.MONDAY -> "L"
      Calendar.TUESDAY -> "M"
      Calendar.WEDNESDAY -> "X"
      Calendar.THURSDAY -> "J"
      Calendar.FRIDAY -> "V"
      Calendar.SATURDAY -> "S"
      else -> "D"
    }
  }
}
