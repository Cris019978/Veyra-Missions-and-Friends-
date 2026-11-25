package com.example.roulette

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Motor de probabilidades recalibrables anti-fórmula.
 *
 * Características clave:
 * - No depende de tablas estáticas predecibles por ingeniería inversa.
 * - Combina osciladores no lineales con frecuencias irracionales (razón áurea),
 *   saturación hiperbólica (tanh), memoria de fase mutante y ruido estocástico acotado.
 * - Regla de oscilación por banda:
 *   1) La opción con mayor probabilidad actual sufre sacudidas fuertes tras cada giro
 *      (puede caer bruscamente o dispararse).
 *   2) Las opciones intermedias ajustan su peso de forma moderada.
 *   3) Las opciones con menor probabilidad (Nivel Gratis, 1,000 XP, Títulos Exclusivos)
 *      solo fluctúan de forma muy suave y contenida.
 */
class AntiFormulaProbabilityEngine(
  private val prizes: List<RoulettePrize> = RouletteCatalogDefaults.defaultPrizes,
  private val random: Random = Random.Default,
) {

  private var currentWeights: DoubleArray = DoubleArray(prizes.size) { prizes[it].baseWeight }
  private var phaseVector: DoubleArray = DoubleArray(prizes.size) { idx ->
    (idx + 1) * GOLDEN_RATIO + random.nextDouble(0.0, PI)
  }
  private var spinCounter: Int = 0
  private var lastWinnerIndex: Int = -1
  private var previousProbabilities: DoubleArray = normalize(currentWeights)

  fun currentSnapshots(): List<PrizeProbabilitySnapshot> {
    val probs = normalize(currentWeights)
    val tiers = assignOscillationTiers(probs)
    return prizes.mapIndexed { idx, prize ->
      PrizeProbabilitySnapshot(
        prize = prize,
        currentWeight = currentWeights[idx],
        probability = probs[idx],
        previousProbability = previousProbabilities[idx],
        oscillationTier = tiers[idx],
      )
    }
  }

  /**
   * Ejecuta un giro ponderado con las probabilidades actuales y luego recalibra
   * todos los pesos con la función no lineal anti-fórmula.
   */
  fun rollAndRecalibrate(pityBoostReady: Boolean = false): Pair<Int, List<PrizeProbabilitySnapshot>> {
    val probsBeforeSpin = normalize(currentWeights)
    previousProbabilities = probsBeforeSpin.copyOf()

    val winnerIndex = pickWeightedIndex(probsBeforeSpin)
    spinCounter += 1
    lastWinnerIndex = winnerIndex

    recalibrateAfterSpin(
      winnerIndex = winnerIndex,
      probsBeforeSpin = probsBeforeSpin,
      pityBoostReady = pityBoostReady,
    )

    return winnerIndex to currentSnapshots()
  }

  fun reset(newSeed: Long? = null) {
    val rng = if (newSeed != null) Random(newSeed) else random
    currentWeights = DoubleArray(prizes.size) { prizes[it].baseWeight }
    phaseVector = DoubleArray(prizes.size) { idx ->
      (idx + 1) * GOLDEN_RATIO + rng.nextDouble(0.0, PI)
    }
    spinCounter = 0
    lastWinnerIndex = -1
    previousProbabilities = normalize(currentWeights)
  }

  private fun pickWeightedIndex(probs: DoubleArray): Int {
    val roll = random.nextDouble()
    var cumulative = 0.0
    for (i in probs.indices) {
      cumulative += probs[i]
      if (roll <= cumulative) return i
    }
    return probs.lastIndex
  }

  private fun recalibrateAfterSpin(
    winnerIndex: Int,
    probsBeforeSpin: DoubleArray,
    pityBoostReady: Boolean,
  ) {
    val tiers = assignOscillationTiers(probsBeforeSpin)
    val entropyPulse = sin(spinCounter * GOLDEN_RATIO + winnerIndex * 0.73)
    val updatedWeights = DoubleArray(prizes.size)

    for (i in prizes.indices) {
      val base = prizes[i].baseWeight
      val prevWeight = currentWeights[i]
      val tier = tiers[i]

      // Avanzamos la fase de manera no lineal para evitar ciclos periódicos simples
      val nonlinearDrift = 0.6180339887 * (i + 1) + 0.37 * tanh(entropyPulse * (i % 3 + 1))
      phaseVector[i] = (phaseVector[i] + nonlinearDrift + random.nextDouble(0.15, 0.85)) % (20.0 * PI)

      val wave = sin(phaseVector[i])
      val noise = random.nextDouble(-1.0, 1.0)
      val winnerDamping = if (i == lastWinnerIndex) -0.22 else 0.06

      updatedWeights[i] = when (tier) {
        OscillationTier.HIGH_VOLATILITY -> {
          // Sacudida fuerte en la opción dominante: puede caer hasta ~0.42x o subir hasta ~1.88x
          val rawShock = tanh(0.85 * wave + 0.75 * noise + winnerDamping + 0.45 * entropyPulse)
          val multiplier = if (rawShock >= 0) {
            1.0 + rawShock * 0.88
          } else {
            1.0 + rawShock * 0.58
          }
          val blendedAnchor = base * 0.55 + prevWeight * 0.45
          (blendedAnchor * multiplier).coerceIn(base * 0.38, base * 1.95)
        }

        OscillationTier.MODERATE -> {
          // Ajuste moderado en premios intermedios (~0.80x .. ~1.24x)
          val rawWave = tanh(0.55 * wave + 0.45 * noise + winnerDamping * 0.5)
          val multiplier = 1.0 + rawWave * 0.22
          val blendedAnchor = base * 0.68 + prevWeight * 0.32
          (blendedAnchor * multiplier).coerceIn(base * 0.72, base * 1.32)
        }

        OscillationTier.CONTAINED -> {
          // Cambio muy suave y contenido en los premios más raros (~0.90x .. ~1.12x)
          val gentleWave = tanh(0.30 * wave + 0.25 * noise) * 0.08
          val pityNudge = if (pityBoostReady) 0.05 else 0.0
          val multiplier = 1.0 + gentleWave + pityNudge
          val blendedAnchor = base * 0.88 + prevWeight * 0.12
          (blendedAnchor * multiplier).coerceIn(base * 0.86, base * 1.16)
        }
      }
    }

    currentWeights = updatedWeights
  }

  private fun assignOscillationTiers(probs: DoubleArray): List<OscillationTier> {
    if (probs.isEmpty()) return emptyList()
    val maxProbIdx = probs.indices.maxByOrNull { probs[it] } ?: 0

    // Ordenamos de menor a mayor para identificar claramente el bloque más raro
    val sortedIndices = probs.indices.sortedBy { probs[it] }
    val rareCutoffCount = (probs.size / 3).coerceAtLeast(3).coerceAtMost(probs.size - 1)
    val containedIndices = sortedIndices.take(rareCutoffCount).toSet()

    return probs.indices.map { idx ->
      when {
        idx == maxProbIdx -> OscillationTier.HIGH_VOLATILITY
        idx in containedIndices || prizes[idx].baseWeight <= 3.0 -> OscillationTier.CONTAINED
        else -> OscillationTier.MODERATE
      }
    }
  }

  private fun normalize(weights: DoubleArray): DoubleArray {
    val total = weights.sum().coerceAtLeast(1e-9)
    return DoubleArray(weights.size) { weights[it] / total }
  }

  private companion object {
    const val GOLDEN_RATIO = 1.618033988749895
  }
}
