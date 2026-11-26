package com.example.roulette

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class RouletteDisplayView(val label: String) {
  CLASSIC("Vista Clásica"),
  HORIZONTAL("Vista Horizontal"),
}

enum class RouletteRarity(
  val label: String,
  val accentColor: Color,
  val badgeBgColor: Color,
) {
  COMMON("Común", Color(0xFF94A3B8), Color(0xFF1E293B)),
  UNCOMMON("Poco Común", Color(0xFF34D399), Color(0xFF0A2E23)),
  RARE("Raro", Color(0xFF38BDF8), Color(0xFF0C273D)),
  EPIC("Épico", Color(0xFFA78BFA), Color(0xFF231842)),
  LEGENDARY("Legendario", Color(0xFFFBBF24), Color(0xFF332308)),
  MYTHIC("Mítico", Color(0xFFF43F5E), Color(0xFF3B101D)),
}

enum class RoulettePrizeCategory(val label: String) {
  XP("XP Escalonada"),
  FREE_LEVEL("Nivel Gratis"),
  EXCLUSIVE_TITLE("Título Exclusivo"),
  RANDOM_MISSION("Misión Aleatoria"),
  HARD_MISSION("Misión Alto Riesgo"),
}

enum class OscillationTier(val label: String) {
  HIGH_VOLATILITY("Sacudida fuerte"),
  MODERATE("Ajuste moderado"),
  CONTAINED("Cambio suave"),
}

// OPT: @Immutable garantiza estabilidad fuerte en Compose y evita recomposiciones si el premio no cambia.
@Immutable
data class RoulettePrize(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: RoulettePrizeCategory,
  val rarity: RouletteRarity,
  val baseWeight: Double,
  val xpAmount: Long = 0L,
  val freeLevels: Int = 0,
  val unlockedTitle: String? = null,
  val isHighRisk: Boolean = false,
  val floorChargeBoost: Int = 0,
  val wheelShortLabel: String = "",
)

@Immutable
data class RouletteTitleVariant(
  val id: String,
  val title: String,
  val shortLabel: String,
  val rarity: RouletteRarity,
  val accentColor: Color,
  val duplicateXpCompensation: Long,
)

// OPT: @Immutable evita recomponer toda la tabla de probabilidades cuando solo cambia otro campo de UI.
@Immutable
data class PrizeProbabilitySnapshot(
  val prize: RoulettePrize,
  val currentWeight: Double,
  val probability: Double,
  val previousProbability: Double,
  val oscillationTier: OscillationTier,
) {
  val probabilityPercent: Double get() = probability * 100.0
  val deltaPercent: Double get() = (probability - previousProbability) * 100.0
}

// OPT: @Immutable estabiliza las tarjetas de las 5 misiones de giros.
@Immutable
data class RouletteSpinMission(
  val id: String,
  val stepOrder: Int,
  val title: String,
  val description: String,
  val targetSpins: Int,
  val rewardXp: Long,
  val bonusLabel: String? = null,
  val currentSpins: Int = 0,
  val isCompleted: Boolean = false,
) {
  val progressFraction: Float
    get() = if (targetSpins <= 0) 1f else (currentSpins.toFloat() / targetSpins.toFloat()).coerceIn(0f, 1f)
}

// OPT: @Immutable estabiliza el historial reciente de tiradas.
@Immutable
data class RouletteSpinRecord(
  val spinNumber: Int,
  val prize: RoulettePrize,
  val bonusFloorXp: Long,
  val viewUsed: RouletteDisplayView,
  val wasSimulatedBatch: Boolean,
  val timestampMs: Long,
  val winningProbabilityPercent: Double,
  val claimableTitle: String? = null,
  val totalXpGranted: Long = 0L,
)

// OPT: @Immutable con colecciones de solo lectura permite a Compose saltar subárboles sin cambios.
@Immutable
data class RouletteUiState(
  val activeView: RouletteDisplayView = RouletteDisplayView.CLASSIC,
  val isSpinning: Boolean = false,
  val totalSpins: Int = 0,
  val accumulatedXp: Long = 0L,
  val bonusLevelsWon: Int = 0,
  val floorChargePercent: Int = 0,
  val floorBonusTriggers: Int = 0,
  val unlockedExclusiveTitles: List<String> = emptyList(),
  val pendingClaimTitles: List<String> = emptyList(),
  val equippedProfileTitle: String = "",
  val activeTitleVariants: List<String> = emptyList(),
  val mainProfileLevel: Long = 1L,
  val mainProfileXp: Long = 0L,
  val activeRandomMissions: List<String> = emptyList(),
  val activeHardMissions: List<String> = emptyList(),
  val prizeProbabilities: List<PrizeProbabilitySnapshot> = emptyList(),
  val spinMissions: List<RouletteSpinMission> = emptyList(),
  val spinHistory: List<RouletteSpinRecord> = emptyList(),
  val lastWonRecord: RouletteSpinRecord? = null,
  val lastCompletedSpinMissionTitle: String? = null,
  val spinAnimationNonce: Long = 0L,
  val pendingTargetPrizeIndex: Int = 0,
  val debugPanelExpanded: Boolean = true,
)

object RouletteCatalogDefaults {
  const val TITLE_SLOT_A_ID = "title_oracle"
  const val TITLE_SLOT_B_ID = "title_luck_lord"

  val specialTitleVariants: List<RouletteTitleVariant> = listOf(
    RouletteTitleVariant(
      id = "var_oracle",
      title = "Oráculo de Veyra",
      shortLabel = "Oráculo",
      rarity = RouletteRarity.EPIC,
      accentColor = Color(0xFFA78BFA),
      duplicateXpCompensation = 200L,
    ),
    RouletteTitleVariant(
      id = "var_luck_lord",
      title = "Señor del Azar",
      shortLabel = "Azar",
      rarity = RouletteRarity.LEGENDARY,
      accentColor = Color(0xFFFBBF24),
      duplicateXpCompensation = 300L,
    ),
    RouletteTitleVariant(
      id = "var_star_tracker",
      title = "Rastreador Estelar",
      shortLabel = "Estelar",
      rarity = RouletteRarity.EPIC,
      accentColor = Color(0xFF38BDF8),
      duplicateXpCompensation = 200L,
    ),
    RouletteTitleVariant(
      id = "var_pulse_walker",
      title = "Caminante del Pulso",
      shortLabel = "Pulso",
      rarity = RouletteRarity.EPIC,
      accentColor = Color(0xFF34D399),
      duplicateXpCompensation = 200L,
    ),
    RouletteTitleVariant(
      id = "var_neon_cipher",
      title = "Cifra Neón",
      shortLabel = "Neón",
      rarity = RouletteRarity.LEGENDARY,
      accentColor = Color(0xFFF472B6),
      duplicateXpCompensation = 300L,
    ),
    RouletteTitleVariant(
      id = "var_abyss_compass",
      title = "Brújula Abisal",
      shortLabel = "Abisal",
      rarity = RouletteRarity.EPIC,
      accentColor = Color(0xFF60A5FA),
      duplicateXpCompensation = 200L,
    ),
    RouletteTitleVariant(
      id = "var_solar_warden",
      title = "Guardián Solar",
      shortLabel = "Solar",
      rarity = RouletteRarity.LEGENDARY,
      accentColor = Color(0xFFFB923C),
      duplicateXpCompensation = 300L,
    ),
    RouletteTitleVariant(
      id = "var_chronos_runner",
      title = "Corredor de Cronos",
      shortLabel = "Cronos",
      rarity = RouletteRarity.EPIC,
      accentColor = Color(0xFF2DD4BF),
      duplicateXpCompensation = 200L,
    ),
    RouletteTitleVariant(
      id = "var_eclipse_sage",
      title = "Sabio del Eclipse",
      shortLabel = "Eclipse",
      rarity = RouletteRarity.LEGENDARY,
      accentColor = Color(0xFFC084FC),
      duplicateXpCompensation = 300L,
    ),
    RouletteTitleVariant(
      id = "var_destiny_ace",
      title = "As del Destino",
      shortLabel = "Destino",
      rarity = RouletteRarity.MYTHIC,
      accentColor = Color(0xFFF43F5E),
      duplicateXpCompensation = 350L,
    ),
  )

  fun findTitleVariant(title: String?): RouletteTitleVariant? {
    val clean = title?.trim().orEmpty()
    if (clean.isBlank()) return null
    return specialTitleVariants.firstOrNull { it.title.equals(clean, ignoreCase = true) }
  }

  fun prizeForTitleSlot(
    slotId: String,
    baseWeight: Double,
    variant: RouletteTitleVariant,
  ): RoulettePrize = RoulettePrize(
    id = slotId,
    title = "Título: ${variant.title}",
    subtitle = "Reclamable de ruleta (${variant.rarity.label}) • +${variant.duplicateXpCompensation} XP si repetido",
    category = RoulettePrizeCategory.EXCLUSIVE_TITLE,
    rarity = variant.rarity,
    baseWeight = baseWeight,
    xpAmount = variant.duplicateXpCompensation,
    unlockedTitle = variant.title,
    wheelShortLabel = variant.shortLabel,
  )

  val defaultPrizes: List<RoulettePrize> = listOf(
    RoulettePrize(
      id = "xp_1_floor",
      title = "1 XP + Carga Base",
      subtitle = "Piso equilibrado: +25% de Carga de Impulso",
      category = RoulettePrizeCategory.XP,
      rarity = RouletteRarity.COMMON,
      baseWeight = 34.0,
      xpAmount = 1L,
      floorChargeBoost = 25,
      wheelShortLabel = "1 XP+",
    ),
    RoulettePrize(
      id = "xp_25",
      title = "25 XP de Campo",
      subtitle = "Progreso constante (+10% Carga)",
      category = RoulettePrizeCategory.XP,
      rarity = RouletteRarity.COMMON,
      baseWeight = 22.0,
      xpAmount = 25L,
      floorChargeBoost = 10,
      wheelShortLabel = "25 XP",
    ),
    RoulettePrize(
      id = "xp_100",
      title = "100 XP Tácticos",
      subtitle = "Impulso sólido para subir de nivel",
      category = RoulettePrizeCategory.XP,
      rarity = RouletteRarity.UNCOMMON,
      baseWeight = 15.0,
      xpAmount = 100L,
      floorChargeBoost = 5,
      wheelShortLabel = "100 XP",
    ),
    RoulettePrize(
      id = "mission_random",
      title = "Misión Aleatoria",
      subtitle = "Ruta rápida sorpresa (+40 XP inmediatos)",
      category = RoulettePrizeCategory.RANDOM_MISSION,
      rarity = RouletteRarity.UNCOMMON,
      baseWeight = 11.0,
      xpAmount = 40L,
      wheelShortLabel = "Misión",
    ),
    RoulettePrize(
      id = "xp_350",
      title = "350 XP Sobrecarga",
      subtitle = "Salto fuerte de experiencia al perfil",
      category = RoulettePrizeCategory.XP,
      rarity = RouletteRarity.RARE,
      baseWeight = 7.5,
      xpAmount = 350L,
      wheelShortLabel = "350 XP",
    ),
    RoulettePrize(
      id = "mission_hard",
      title = "Misión Alto Riesgo",
      subtitle = "Dificultad extrema • Recompensa alta (+120 XP)",
      category = RoulettePrizeCategory.HARD_MISSION,
      rarity = RouletteRarity.RARE,
      baseWeight = 5.2,
      xpAmount = 120L,
      isHighRisk = true,
      wheelShortLabel = "Riesgo!",
    ),
    prizeForTitleSlot(
      slotId = TITLE_SLOT_A_ID,
      baseWeight = 2.6,
      variant = specialTitleVariants[0],
    ),
    prizeForTitleSlot(
      slotId = TITLE_SLOT_B_ID,
      baseWeight = 1.4,
      variant = specialTitleVariants[1],
    ),
    RoulettePrize(
      id = "xp_1000",
      title = "1,000 XP Mega Núcleo",
      subtitle = "Máxima descarga directa de experiencia",
      category = RoulettePrizeCategory.XP,
      rarity = RouletteRarity.LEGENDARY,
      baseWeight = 0.9,
      xpAmount = 1000L,
      wheelShortLabel = "1K XP",
    ),
    RoulettePrize(
      id = "free_level_jackpot",
      title = "¡Nivel Gratis (+1 NV)!",
      subtitle = "Premio mayor más raro (+1 Nivel directo a tu perfil)",
      category = RoulettePrizeCategory.FREE_LEVEL,
      rarity = RouletteRarity.MYTHIC,
      baseWeight = 0.4,
      xpAmount = 0L,
      freeLevels = 1,
      wheelShortLabel = "+1 NV",
    ),
  )

  fun defaultSpinMissions(): List<RouletteSpinMission> = listOf(
    RouletteSpinMission(
      id = "spin_m_1",
      stepOrder = 1,
      title = "Misión I: Primer Giro",
      description = "Haz girar la ruleta 3 veces",
      targetSpins = 3,
      rewardXp = 75L,
    ),
    RouletteSpinMission(
      id = "spin_m_2",
      stepOrder = 2,
      title = "Misión II: Ritmo Constante",
      description = "Acumula 10 tiradas totales",
      targetSpins = 10,
      rewardXp = 180L,
    ),
    RouletteSpinMission(
      id = "spin_m_3",
      stepOrder = 3,
      title = "Misión III: Cazador del Azar",
      description = "Alcanza 25 giros en la ruleta",
      targetSpins = 25,
      rewardXp = 400L,
    ),
    RouletteSpinMission(
      id = "spin_m_4",
      stepOrder = 4,
      title = "Misión IV: Estratega Nocturno",
      description = "Completa 50 tiradas",
      targetSpins = 50,
      rewardXp = 850L,
    ),
    RouletteSpinMission(
      id = "spin_m_5",
      stepOrder = 5,
      title = "Misión V: Maestro de la Ruleta",
      description = "Llega a 100 giros totales",
      targetSpins = 100,
      rewardXp = 1500L,
      bonusLabel = "Insignia Maestro",
    ),
  )

  val randomMissionPool: List<String> = listOf(
    "Escaneo relámpago de plaza cercana",
    "Captura de arte urbano en tu cuadra",
    "Caminata rápida de 350 m en radar",
    "Registro fotográfico de zona verde",
  )

  val hardMissionPool: List<String> = listOf(
    "ALTO RIESGO: Circuito cronometrado de 3 balizas (60s)",
    "ALTO RIESGO: Desafío nocturno de precisión sin fallos",
    "ALTO RIESGO: Ruta triple en solitario contra reloj",
  )
}
