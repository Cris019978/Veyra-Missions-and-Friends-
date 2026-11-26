package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ConfettiShape {
  RIBBON,
  CIRCLE,
  DIAMOND,
}

private data class ConfettiParticle(
  val startXFrac: Float,
  val startYFrac: Float,
  val velocityX: Float,
  val velocityY: Float,
  val gravity: Float,
  val sizePx: Float,
  val spinSpeedDeg: Float,
  val initialRotationDeg: Float,
  val wobblePhase: Float,
  val wobbleAmplitude: Float,
  val color: Color,
  val shape: ConfettiShape,
)

private val CONFETTI_COLORS = listOf(
  Color(0xFF00E5C3), // Veyra Teal
  Color(0xFF10B981), // Emerald
  Color(0xFFFBBF24), // Gold
  Color(0xFFF59E0B), // Amber
  Color(0xFFF43F5E), // Coral
  Color(0xFF38BDF8), // Sky
  Color(0xFFA78BFA), // Violet
)

@Composable
fun ConfettiCelebrationOverlay(
  triggerKey: Long,
  disableAnimations: Boolean = false,
  onFinished: () -> Unit = {},
  modifier: Modifier = Modifier,
  animationTier: String = if (disableAnimations) "low" else "high",
) {
  val isLowTier = disableAnimations || animationTier == "low"
  val isMediumTier = !isLowTier && animationTier == "medium"
  val particleCount = when {
    isLowTier -> 18
    isMediumTier -> 28
    else -> 68
  }
  val animDurationMs = if (isMediumTier) 1250 else 2100

  var activeBurstKey by remember { mutableStateOf<Long?>(null) }
  val progress = remember { Animatable(0f) }

  val particles = remember(activeBurstKey, particleCount) {
    val seed = activeBurstKey ?: return@remember emptyList()
    val rng = Random(seed * 31L + 7919L)
    List(particleCount) { idx ->
      // Mix of top-center fountain and mid-screen celebratory pop
      val isTopBurst = idx % 3 != 0
      val angleDeg = if (isTopBurst) {
        rng.nextFloat() * 140f + 20f // downward spread 20..160 deg
      } else {
        rng.nextFloat() * 130f - 155f // upward burst from lower-center
      }
      val rad = angleDeg * (PI.toFloat() / 180f)
      val speed = if (isTopBurst) {
        rng.nextFloat() * 520f + 220f
      } else {
        rng.nextFloat() * 780f + 460f
      }
      ConfettiParticle(
        startXFrac = if (isTopBurst) rng.nextFloat() * 0.72f + 0.14f else 0.5f + (rng.nextFloat() - 0.5f) * 0.25f,
        startYFrac = if (isTopBurst) -0.02f + rng.nextFloat() * 0.10f else 0.68f,
        velocityX = cos(rad) * speed * (if (rng.nextBoolean()) 1f else -1f).let {
          if (isTopBurst) (rng.nextFloat() * 2f - 1f) * 0.75f else 1f
        },
        velocityY = if (isTopBurst) sin(rad) * (speed * 0.55f) else sin(rad) * speed,
        gravity = rng.nextFloat() * 480f + 620f,
        sizePx = rng.nextFloat() * 12f + 9f,
        spinSpeedDeg = (rng.nextFloat() * 540f + 180f) * if (rng.nextBoolean()) 1f else -1f,
        initialRotationDeg = rng.nextFloat() * 360f,
        wobblePhase = rng.nextFloat() * (2f * PI.toFloat()),
        wobbleAmplitude = rng.nextFloat() * 26f + 8f,
        color = CONFETTI_COLORS[idx % CONFETTI_COLORS.size],
        shape = ConfettiShape.entries[idx % ConfettiShape.entries.size],
      )
    }
  }

  LaunchedEffect(triggerKey) {
    if (triggerKey <= 0L) return@LaunchedEffect
    activeBurstKey = triggerKey
    if (isLowTier) {
      progress.snapTo(0.28f)
      delay(800L)
    } else {
      progress.snapTo(0f)
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = animDurationMs, easing = LinearEasing),
      )
    }
    activeBurstKey = null
    onFinished()
  }

  if (activeBurstKey != null) {
    val t = progress.value
    val durationSec = (animDurationMs / 1000f) * t
    val fadeAlpha = when {
      t < 0.72f -> 1f
      else -> (1f - (t - 0.72f) / 0.28f).coerceIn(0f, 1f)
    }

    Box(
      modifier = modifier
        .fillMaxSize()
        .testTag("confetti_celebration_overlay"),
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        if (fadeAlpha <= 0.01f) return@Canvas
        val w = size.width
        val h = size.height

        particles.forEach { p ->
          val dragFactor = 1f - (0.28f * t)
          val wobbleX = sin(durationSec * 6.5f + p.wobblePhase) * p.wobbleAmplitude
          val x = p.startXFrac * w + p.velocityX * durationSec * dragFactor + wobbleX
          val y = p.startYFrac * h + p.velocityY * durationSec + 0.5f * p.gravity * durationSec * durationSec

          if (x < -40f || x > w + 40f || y > h + 40f) return@forEach

          val drawColor = p.color.copy(alpha = fadeAlpha)
          val angle = p.initialRotationDeg + p.spinSpeedDeg * durationSec

          rotate(degrees = angle, pivot = Offset(x, y)) {
            when (p.shape) {
              ConfettiShape.RIBBON -> {
                val ribbonW = p.sizePx * 1.45f
                val ribbonH = p.sizePx * 0.65f
                drawRect(
                  color = drawColor,
                  topLeft = Offset(x - ribbonW / 2f, y - ribbonH / 2f),
                  size = Size(ribbonW, ribbonH),
                )
              }

              ConfettiShape.CIRCLE -> {
                drawCircle(
                  color = drawColor,
                  radius = p.sizePx * 0.45f,
                  center = Offset(x, y),
                )
              }

              ConfettiShape.DIAMOND -> {
                val half = p.sizePx * 0.65f
                val diamondPath = Path().apply {
                  moveTo(x, y - half)
                  lineTo(x + half * 0.78f, y)
                  lineTo(x, y + half)
                  lineTo(x - half * 0.78f, y)
                  close()
                }
                drawPath(path = diamondPath, color = drawColor)
              }
            }
          }
        }
      }
    }
  }
}
