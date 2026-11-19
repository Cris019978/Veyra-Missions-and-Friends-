package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Provides tactile VibrationEffect feedback for radar pin interactions and
 * celebratory mission verifications across Android API 24+.
 */
object HapticFeedbackHelper {

  internal fun resolveVibrator(context: Context): Vibrator? {
    val appContext = context.applicationContext ?: context
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = appContext.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator ?: @Suppress("DEPRECATION") (appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
      } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
    }.getOrNull()
  }

  /**
   * Subtle crisp click feedback when tapping a mission or player pin on the radar map.
   */
  fun performRadarPinTap(context: Context) {
    val vibrator = resolveVibrator(context) ?: return
    runCatching {
      if (!vibrator.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = if (
          Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
          runCatching { vibrator.areAllEffectsSupported(VibrationEffect.EFFECT_CLICK) }.getOrNull() ==
          Vibrator.VIBRATION_EFFECT_SUPPORT_YES
        ) {
          VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
          val amp = if (runCatching { vibrator.hasAmplitudeControl() }.getOrDefault(false)) {
            150
          } else {
            VibrationEffect.DEFAULT_AMPLITUDE
          }
          VibrationEffect.createOneShot(18L, amp)
        }
        vibrator.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(18L)
      }
    }
  }

  /**
   * Celebratory double-pulse haptic waveform when a mission is successfully verified.
   */
  fun performMissionVerified(context: Context) {
    val vibrator = resolveVibrator(context) ?: return
    runCatching {
      if (!vibrator.hasVibrator()) return
      val timings = longArrayOf(0L, 34L, 55L, 74L)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val hasAmp = runCatching { vibrator.hasAmplitudeControl() }.getOrDefault(false)
        val effect = if (hasAmp) {
          VibrationEffect.createWaveform(timings, intArrayOf(0, 175, 0, 255), -1)
        } else {
          VibrationEffect.createWaveform(timings, -1)
        }
        vibrator.vibrate(effect)
      } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(timings, -1)
      }
    }
  }
}
