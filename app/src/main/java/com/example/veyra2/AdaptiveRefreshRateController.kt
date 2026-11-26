package com.example.veyra2

import android.app.Activity
import android.os.Build
import android.view.WindowManager

/**
 * Controlador ligero de tasa de refresco adaptativa para el modo táctico Veyra 2.
 * Ajusta la sugerencia de refresco de la ventana Android según el [AdaptiveRefreshTier] activo
 * (30 Hz en reposo, 60 Hz en movimiento, 120 Hz en gestos/joystick/rotación) sin bloquear el hilo UI.
 */
object AdaptiveRefreshRateController {

  private val isRobolectric: Boolean by lazy {
    Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
  }

  fun applyFrameRateHint(activity: Activity?, tier: AdaptiveRefreshTier) {
    if (activity == null || isRobolectric) return
    val window = activity.window ?: return

    runCatching {
      val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
      } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay
      }
      val supportedModes = display?.supportedModes.orEmpty()
      val attrs = window.attributes
      val targetHz = tier.targetHz

      if (supportedModes.isNotEmpty()) {
        val bestMode = supportedModes
          .sortedBy { kotlin.math.abs(it.refreshRate - targetHz) }
          .firstOrNull()
        val chosenHz = bestMode?.refreshRate ?: targetHz
        if (attrs.preferredRefreshRate != chosenHz) {
          attrs.preferredRefreshRate = chosenHz
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && bestMode != null) {
            attrs.preferredDisplayModeId = bestMode.modeId
          }
          window.attributes = attrs
        }
      } else if (attrs.preferredRefreshRate != targetHz) {
        attrs.preferredRefreshRate = targetHz
        window.attributes = attrs
      }
    }
  }

  fun resetFrameRateHint(activity: Activity?) {
    if (activity == null || isRobolectric) return
    val window = activity.window ?: return
    runCatching {
      val attrs: WindowManager.LayoutParams = window.attributes
      var changed = false
      if (attrs.preferredRefreshRate != 0f) {
        attrs.preferredRefreshRate = 0f
        changed = true
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && attrs.preferredDisplayModeId != 0) {
        attrs.preferredDisplayModeId = 0
        changed = true
      }
      if (changed) {
        window.attributes = attrs
      }
    }
  }
}
