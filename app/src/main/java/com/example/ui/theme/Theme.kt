package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeModeOption(val key: String, val label: String) {
  SYSTEM("system", "Sistema"),
  LIGHT("light", "Claro"),
  DARK("dark", "Oscuro");

  companion object {
    fun fromKey(key: String): ThemeModeOption = entries.firstOrNull { it.key == key } ?: SYSTEM
  }
}

enum class PaletteOption(val key: String, val label: String) {
  DYNAMIC("dynamic", "Material You (Fondo)"),
  EMERALD("emerald", "Radar Esmeralda"),
  CORAL("coral", "Pulso Coral"),
  INDIGO("indigo", "Índigo Cósmico"),
  AMBER("amber", "Solar Dorado");

  companion object {
    fun fromKey(key: String): PaletteOption = entries.firstOrNull { it.key == key } ?: DYNAMIC
  }
}

enum class RadarStyleOption(val key: String, val label: String) {
  NEON("neon", "Pulso Neón"),
  CLASSIC("classic", "Clásico"),
  COMPASS("compass", "Brújula Táctica");

  companion object {
    fun fromKey(key: String): RadarStyleOption = entries.firstOrNull { it.key == key } ?: NEON
  }
}

enum class CornerStyleOption(val key: String, val label: String) {
  SOFT("soft", "Suave"),
  PILL("pill", "Ultra Redondo"),
  CRISP("crisp", "Táctico");

  companion object {
    fun fromKey(key: String): CornerStyleOption = entries.firstOrNull { it.key == key } ?: SOFT
  }
}

enum class AnimationTierOption(val key: String, val label: String, val desc: String) {
  LOW("low", "Gama baja", "Sin transiciones ni sombras; solo barrido esencial"),
  MEDIUM("medium", "Gama media", "Transiciones ligeras sin ondas ni sombras"),
  HIGH("high", "Gama alta", "Físicas suaves, sonar y confeti 34–48 sin sombras");

  companion object {
    fun fromKey(key: String): AnimationTierOption = entries.firstOrNull { it.key == key } ?: HIGH
  }
}

@Composable
fun RadarQuestTheme(
  themeMode: String = "system",
  paletteKey: String = "dynamic",
  cornerStyle: String = "soft",
  content: @Composable () -> Unit,
) {
  val isDark = when (ThemeModeOption.fromKey(themeMode)) {
    ThemeModeOption.SYSTEM -> isSystemInDarkTheme()
    ThemeModeOption.LIGHT -> false
    ThemeModeOption.DARK -> true
  }

  val context = LocalContext.current
  val colorScheme = when (PaletteOption.fromKey(paletteKey)) {
    PaletteOption.DYNAMIC -> {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      } else {
        if (isDark) EmeraldDark else EmeraldLight
      }
    }
    PaletteOption.EMERALD -> if (isDark) EmeraldDark else EmeraldLight
    PaletteOption.CORAL -> if (isDark) CoralDark else CoralLight
    PaletteOption.INDIGO -> if (isDark) IndigoDark else IndigoLight
    PaletteOption.AMBER -> if (isDark) AmberDark else AmberLight
  }

  val shapes = when (CornerStyleOption.fromKey(cornerStyle)) {
    CornerStyleOption.SOFT -> Shapes(
      small = RoundedCornerShape(14.dp),
      medium = RoundedCornerShape(22.dp),
      large = RoundedCornerShape(28.dp),
      extraLarge = RoundedCornerShape(36.dp),
    )
    CornerStyleOption.PILL -> Shapes(
      small = RoundedCornerShape(20.dp),
      medium = RoundedCornerShape(28.dp),
      large = RoundedCornerShape(34.dp),
      extraLarge = RoundedCornerShape(42.dp),
    )
    CornerStyleOption.CRISP -> Shapes(
      small = RoundedCornerShape(10.dp),
      medium = RoundedCornerShape(14.dp),
      large = RoundedCornerShape(18.dp),
      extraLarge = RoundedCornerShape(24.dp),
    )
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = RadarQuestTypography,
    shapes = shapes,
    content = content,
  )
}
