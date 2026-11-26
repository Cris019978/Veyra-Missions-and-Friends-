package com.example.roulette

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pantalla completa de la Ruleta de Doble Vista conectada al perfil principal de Veyra.
 */
@Composable
fun RouletteTestScreen(
  stateManager: RouletteStateManager,
  onClose: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  animationTier: String = "high",
) {
  val uiState by stateManager.uiState.collectAsStateWithLifecycle()

  RouletteTestScreenContent(
    uiState = uiState,
    onSwitchView = stateManager::switchView,
    onSpinAnimated = { stateManager.spinOnceAnimated() },
    onSpinInstant = stateManager::spinOnceInstant,
    onSimulateBatch = stateManager::simulateBatchSpins,
    onClaimTitle = { title, equip -> stateManager.claimPendingTitle(title, equipAfterClaim = equip) },
    onClaimAllTitles = stateManager::claimAllPendingTitles,
    onEquipTitle = stateManager::equipProfileTitle,
    onToggleDebugPanel = stateManager::toggleDebugPanel,
    onReset = stateManager::resetAll,
    onClose = onClose,
    modifier = modifier,
    animationTier = animationTier,
  )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouletteTestScreenContent(
  uiState: RouletteUiState,
  onSwitchView: (RouletteDisplayView) -> Unit,
  onSpinAnimated: () -> Unit,
  onSpinInstant: () -> Unit,
  onSimulateBatch: (Int) -> Unit,
  onToggleDebugPanel: () -> Unit,
  onReset: () -> Unit,
  onClose: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  animationTier: String = "high",
  onClaimTitle: (title: String, equipAfterClaim: Boolean) -> Unit = { _, _ -> },
  onClaimAllTitles: () -> Unit = {},
  onEquipTitle: (String) -> Unit = {},
) {
  val prizes = remember(uiState.prizeProbabilities) {
    if (uiState.prizeProbabilities.isNotEmpty()) {
      uiState.prizeProbabilities.map { it.prize }
    } else {
      RouletteCatalogDefaults.defaultPrizes
    }
  }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .testTag("roulette_test_screen"),
  ) {
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier.fillMaxSize(),
    ) {
      LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 680.dp)
          .testTag("roulette_lazy_list"),
      ) {
        item(key = "header") {
          RouletteHeaderBar(
            uiState = uiState,
            onClose = onClose,
          )
        }

        item(key = "view_switcher") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(
              modifier = Modifier.fillMaxWidth(),
            ) {
              RouletteDisplayView.entries.forEachIndexed { index, viewMode ->
                SegmentedButton(
                  selected = uiState.activeView == viewMode,
                  onClick = { onSwitchView(viewMode) },
                  shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = RouletteDisplayView.entries.size,
                  ),
                  icon = {
                    Icon(
                      imageVector = if (viewMode == RouletteDisplayView.CLASSIC) {
                        Icons.Rounded.TrackChanges
                      } else {
                        Icons.Rounded.ViewCarousel
                      },
                      contentDescription = null,
                      modifier = Modifier.size(18.dp),
                    )
                  },
                  modifier = Modifier.testTag(
                    if (viewMode == RouletteDisplayView.CLASSIC) {
                      "roulette_view_classic"
                    } else {
                      "roulette_view_horizontal"
                    },
                  ),
                ) {
                  Text(
                    text = viewMode.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        item(key = "roulette_stage") {
          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            ) {
              when (uiState.activeView) {
                RouletteDisplayView.CLASSIC -> ClassicRouletteWheel(
                  prizes = prizes,
                  spinNonce = uiState.spinAnimationNonce,
                  targetIndex = uiState.pendingTargetPrizeIndex,
                  lastWonPrizeId = uiState.lastWonRecord?.prize?.id,
                  isSpinning = uiState.isSpinning,
                  animationTier = animationTier,
                )

                RouletteDisplayView.HORIZONTAL -> HorizontalRouletteTape(
                  prizes = prizes,
                  spinNonce = uiState.spinAnimationNonce,
                  targetIndex = uiState.pendingTargetPrizeIndex,
                  lastWonPrizeId = uiState.lastWonRecord?.prize?.id,
                  isSpinning = uiState.isSpinning,
                  animationTier = animationTier,
                )
              }

              FloorChargeBar(
                chargePercent = uiState.floorChargePercent,
                triggersCount = uiState.floorBonusTriggers,
              )

              Button(
                onClick = onSpinAnimated,
                enabled = !uiState.isSpinning,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("roulette_spin_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Casino,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = if (uiState.isSpinning) {
                    "Girando ruleta..."
                  } else {
                    "Girar Ruleta (${uiState.activeView.label})"
                  },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }
        }

        uiState.lastWonRecord?.let { latest ->
          item(key = "last_reward") {
            LastRewardBanner(
              record = latest,
              completedMissionTitle = uiState.lastCompletedSpinMissionTitle,
              pendingClaimTitles = uiState.pendingClaimTitles,
              unlockedTitles = uiState.unlockedExclusiveTitles,
              equippedTitle = uiState.equippedProfileTitle,
              onClaimTitle = onClaimTitle,
              onEquipTitle = onEquipTitle,
            )
          }
        }

        item(key = "unlocks_summary") {
          UnlocksAndMissionsSummaryCard(
            uiState = uiState,
            onClaimTitle = onClaimTitle,
            onClaimAllTitles = onClaimAllTitles,
            onEquipTitle = onEquipTitle,
          )
        }

        item(key = "spin_missions") {
          SpinMissionsCard(missions = uiState.spinMissions)
        }

        item(key = "debug_panel") {
          RouletteDebugPanel(
            uiState = uiState,
            animationTier = animationTier,
            onToggleExpand = onToggleDebugPanel,
            onSpinInstant = onSpinInstant,
            onSimulate10 = { onSimulateBatch(10) },
            onSimulate50 = { onSimulateBatch(50) },
            onReset = onReset,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouletteHeaderBar(
  uiState: RouletteUiState,
  onClose: (() -> Unit)?,
) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Ruleta Anti-Fórmula",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(
          text = "Perfil: NV ${uiState.mainProfileLevel} • ${uiState.mainProfileXp} XP (suma real)",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold,
        )
      }
      if (onClose != null) {
        IconButton(
          onClick = onClose,
          modifier = Modifier.testTag("roulette_close_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cerrar prueba de ruleta",
          )
        }
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      StatPillChip(label = "Giros", value = uiState.totalSpins.toString(), tag = "roulette_stat_spins")
      StatPillChip(label = "XP Ganado", value = "${uiState.accumulatedXp}", tag = "roulette_stat_xp")
      StatPillChip(label = "Niveles +", value = "+${uiState.bonusLevelsWon}", tag = "roulette_stat_levels")
      StatPillChip(
        label = "Títulos",
        value = "${uiState.unlockedExclusiveTitles.size}/${RouletteCatalogDefaults.specialTitleVariants.size}",
        tag = "roulette_stat_titles",
      )
    }
  }
}

@Composable
private fun StatPillChip(
  label: String,
  value: String,
  tag: String,
) {
  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier = Modifier.testTag(tag),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun FloorChargeBar(
  chargePercent: Int,
  triggersCount: Int,
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "Carga de Impulso (piso 1 XP)",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "$chargePercent% • +50 XP al 100% (x$triggersCount)",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    LinearProgressIndicator(
      progress = { (chargePercent / 100f).coerceIn(0f, 1f) },
      modifier = Modifier
        .fillMaxWidth()
        .height(8.dp)
        .clip(CircleShape),
    )
  }
}

// OPT: Modelo inmutable precalculado fuera de DrawScope para evitar trigonometría, colores y cadenas en cada frame.
@Immutable
private data class WheelSectorSpec(
  val startDeg: Float,
  val sweepDeg: Float,
  val midDeg: Float,
  val cosStart: Float,
  val sinStart: Float,
  val cosMid: Float,
  val sinMid: Float,
  val shortLabel: String,
  val fillColor: Color,
  val accentColor: Color,
  val labelColorArgb: Int,
)

@Composable
private fun ClassicRouletteWheel(
  prizes: List<RoulettePrize>,
  spinNonce: Long,
  targetIndex: Int,
  lastWonPrizeId: String?,
  isSpinning: Boolean,
  animationTier: String = "high",
) {
  val isLowTier = animationTier == "low"
  val isHighTier = animationTier != "low" && animationTier != "medium"
  val wheelAngle = remember { Animatable(0f) }

  LaunchedEffect(spinNonce, animationTier) {
    if (spinNonce > 0L && prizes.isNotEmpty()) {
      val sweep = 360f / prizes.size
      val desiredMod = ((360f - (targetIndex * sweep)) % 360f + 360f) % 360f
      val currentMod = ((wheelAngle.value % 360f) + 360f) % 360f
      val deltaToTarget = ((desiredMod - currentMod) + 360f) % 360f
      val subtleJitter = (((spinNonce % 5L).toInt() - 2) * (sweep * 0.11f))

      if (isLowTier) {
        wheelAngle.snapTo(wheelAngle.value + deltaToTarget + subtleJitter)
      } else {
        val extraSpins = 360f * (if (isHighTier) 4f else 2f)
        val durationMs = if (isHighTier) 1750 else 950
        val finalAngle = wheelAngle.value + extraSpins + deltaToTarget + subtleJitter
        wheelAngle.animateTo(
          targetValue = finalAngle,
          animationSpec = tween(
            durationMillis = durationMs,
            easing = CubicBezierEasing(0.10f, 0.90f, 0.18f, 1.0f),
          ),
        )
      }
    }
  }

  val surfaceBase = MaterialTheme.colorScheme.surfaceContainerHighest
  val rimColor = MaterialTheme.colorScheme.outlineVariant
  val hubBg = MaterialTheme.colorScheme.surface
  val pointerColor = MaterialTheme.colorScheme.primary
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  val textPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  // OPT: Precalcular geometría trigonométrica, colores y etiquetas fuera del ciclo de DrawScope.
  val sectorSpecs = remember(prizes, lastWonPrizeId, isSpinning, onSurfaceColor) {
    val count = prizes.size.coerceAtLeast(1)
    val sweep = 360f / count
    val highRiskTextArgb = Color(0xFFFCA5A5).toArgb()
    val normalTextArgb = onSurfaceColor.toArgb()
    prizes.mapIndexed { idx, prize ->
      val startDeg = -90f - (sweep / 2f) + (idx * sweep)
      val midDeg = startDeg + sweep / 2f
      val radStart = Math.toRadians(startDeg.toDouble())
      val midRad = Math.toRadians(midDeg.toDouble())
      val isLatestWinner = !isSpinning && lastWonPrizeId == prize.id
      val sectorFill = if (isLatestWinner) {
        prize.rarity.accentColor.copy(alpha = 0.44f)
      } else {
        prize.rarity.accentColor.copy(alpha = if (idx % 2 == 0) 0.22f else 0.14f)
      }
      WheelSectorSpec(
        startDeg = startDeg,
        sweepDeg = sweep,
        midDeg = midDeg,
        cosStart = cos(radStart).toFloat(),
        sinStart = sin(radStart).toFloat(),
        cosMid = cos(midRad).toFloat(),
        sinMid = sin(midRad).toFloat(),
        shortLabel = shortWheelLabel(prize),
        fillColor = sectorFill,
        accentColor = prize.rarity.accentColor,
        labelColorArgb = if (prize.isHighRisk) highRiskTextArgb else normalTextArgb,
      )
    }
  }

  // OPT: Reutilizar Path del puntero y lista de colores del halo sin instanciar en cada frame.
  val pointerPath = remember { Path() }
  val haloColors = remember(pointerColor) {
    listOf(pointerColor.copy(alpha = 0.15f), Color.Transparent)
  }

  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier
      .fillMaxWidth()
      .height(300.dp)
      .testTag("roulette_classic_wheel"),
  ) {
    // OPT: Capa del disco rotada exclusivamente en graphicsLayer (GPU) sin recomponer ni redibujar sectores por frame.
    Canvas(
      modifier = Modifier
        .size(284.dp)
        .graphicsLayer { rotationZ = wheelAngle.value },
    ) {
      val radius = size.minDimension / 2f - 14.dp.toPx()
      val center = Offset(size.width / 2f, size.height / 2f)
      val rimInset = 4.dp.toPx()
      val outerArcTopLeft = Offset(center.x - radius + rimInset, center.y - radius + rimInset)
      val outerArcSize = Size((radius - rimInset) * 2f, (radius - rimInset) * 2f)
      val baseTopLeft = Offset(center.x - radius, center.y - radius)
      val baseSize = Size(radius * 2f, radius * 2f)
      val rimStrokeWidth = 7.dp.toPx()
      val dividerStroke = 1.5.dp.toPx()
      val labelDist = radius * 0.62f
      textPaint.textSize = 10.5.sp.toPx()

      sectorSpecs.forEach { sector ->
        drawArc(
          color = surfaceBase,
          startAngle = sector.startDeg,
          sweepAngle = sector.sweepDeg,
          useCenter = true,
          topLeft = baseTopLeft,
          size = baseSize,
        )
        drawArc(
          color = sector.fillColor,
          startAngle = sector.startDeg,
          sweepAngle = sector.sweepDeg,
          useCenter = true,
          topLeft = baseTopLeft,
          size = baseSize,
        )

        drawArc(
          color = sector.accentColor,
          startAngle = sector.startDeg + 1f,
          sweepAngle = sector.sweepDeg - 2f,
          useCenter = false,
          topLeft = outerArcTopLeft,
          size = outerArcSize,
          style = Stroke(width = rimStrokeWidth, cap = StrokeCap.Butt),
        )

        drawLine(
          color = rimColor,
          start = center,
          end = Offset(
            x = center.x + (radius * sector.cosStart),
            y = center.y + (radius * sector.sinStart),
          ),
          strokeWidth = dividerStroke,
        )

        val lx = center.x + (labelDist * sector.cosMid)
        val ly = center.y + (labelDist * sector.sinMid)

        textPaint.color = sector.labelColorArgb
        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.translate(lx, ly)
        drawContext.canvas.nativeCanvas.rotate(sector.midDeg + 90f)
        drawContext.canvas.nativeCanvas.drawText(sector.shortLabel, 0f, 4f, textPaint)
        drawContext.canvas.nativeCanvas.restore()
      }

      drawCircle(
        color = rimColor,
        radius = radius,
        center = center,
        style = Stroke(width = 3.dp.toPx()),
      )
    }

    // OPT: Capa estática (halo exterior, eje metálico y puntero fijo) separada del giro del disco.
    Canvas(modifier = Modifier.size(284.dp)) {
      val radius = size.minDimension / 2f - 14.dp.toPx()
      val center = Offset(size.width / 2f, size.height / 2f)
      if (isHighTier) {
        val haloRadius = radius + 16.dp.toPx()
        drawCircle(
          brush = Brush.radialGradient(
            colors = haloColors,
            center = center,
            radius = haloRadius,
          ),
          radius = haloRadius,
          center = center,
        )
      }

      val hubRadius = 26.dp.toPx()
      drawCircle(color = hubBg, radius = hubRadius, center = center)
      drawCircle(
        color = pointerColor,
        radius = hubRadius,
        center = center,
        style = Stroke(width = 3.dp.toPx()),
      )
      drawCircle(color = pointerColor, radius = 8.dp.toPx(), center = center)

      val pointerTopY = center.y - radius - 12.dp.toPx()
      val pointerTipY = center.y - radius + 12.dp.toPx()
      val halfW = 11.dp.toPx()
      pointerPath.rewind()
      pointerPath.moveTo(center.x - halfW, pointerTopY)
      pointerPath.lineTo(center.x + halfW, pointerTopY)
      pointerPath.lineTo(center.x, pointerTipY)
      pointerPath.close()
      drawPath(path = pointerPath, color = pointerColor)
    }
  }
}

@Composable
private fun HorizontalRouletteTape(
  prizes: List<RoulettePrize>,
  spinNonce: Long,
  targetIndex: Int,
  lastWonPrizeId: String?,
  isSpinning: Boolean,
  animationTier: String = "high",
) {
  val isLowTier = animationTier == "low"
  val isHighTier = animationTier != "low" && animationTier != "medium"
  val density = LocalDensity.current
  val cardWidthDp = 126.dp
  val cardSpacingDp = 10.dp
  val stridePx = remember(density) { with(density) { (cardWidthDp + cardSpacingDp).toPx() } }
  val cardWidthPx = remember(density) { with(density) { cardWidthDp.toPx() } }
  val cycleSize = prizes.size.coerceAtLeast(1)
  val tapeOffsetPx = remember(cycleSize) { Animatable(cycleSize * stridePx) }

  LaunchedEffect(spinNonce, animationTier) {
    if (spinNonce > 0L && prizes.isNotEmpty()) {
      val currentSlotNormalized = ((tapeOffsetPx.value / stridePx).roundToInt() % cycleSize + cycleSize) % cycleSize
      val resetSlot = cycleSize + currentSlotNormalized
      tapeOffsetPx.snapTo(resetSlot * stridePx)

      val extraCycles = when {
        isLowTier -> 1
        isHighTier -> 4
        else -> 2
      }
      val destinationSlot = (cycleSize * extraCycles) + targetIndex
      if (isLowTier) {
        tapeOffsetPx.snapTo(destinationSlot * stridePx)
      } else {
        val durationMs = if (isHighTier) 1750 else 950
        tapeOffsetPx.animateTo(
          targetValue = destinationSlot * stridePx,
          animationSpec = tween(
            durationMillis = durationMs,
            easing = CubicBezierEasing(0.10f, 0.90f, 0.18f, 1.0f),
          ),
        )
      }
    }
  }

  // Pool fijo de 9 ranuras recicladas: nunca agrega ni destruye nodos Composable durante el giro.
  val poolSize = 9
  val halfPool = poolSize / 2
  val centerSlotIndex by remember(stridePx) {
    derivedStateOf {
      (tapeOffsetPx.value / stridePx).roundToInt()
    }
  }
  val offsetReader: () -> Float = remember(tapeOffsetPx) { { tapeOffsetPx.value } }

  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("roulette_horizontal_tape"),
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .height(148.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
        .clipToBounds(),
    ) {
      val viewportWidthPx = with(density) { maxWidth.toPx() }
      val viewportCenterShiftPx = (viewportWidthPx - cardWidthPx) / 2f

      for (poolIdx in 0 until poolSize) {
        key(poolIdx) {
          val approxMod = ((centerSlotIndex % poolSize) + poolSize) % poolSize
          val delta = ((poolIdx - approxMod + halfPool + poolSize) % poolSize) - halfPool
          val slotIdx = centerSlotIndex + delta
          val prizeIdx = ((slotIdx % cycleSize) + cycleSize) % cycleSize
          val prize = prizes[prizeIdx]
          val isCenteredWinner = !isSpinning && lastWonPrizeId == prize.id && slotIdx == centerSlotIndex
          val slotBasePx = slotIdx * stridePx + viewportCenterShiftPx

          TapePrizeTile(
            prize = prize,
            isHighlighted = isCenteredWinner,
            modifier = Modifier
              .width(cardWidthDp)
              .fillMaxHeight()
              .padding(vertical = 12.dp)
              .graphicsLayer {
                translationX = slotBasePx - offsetReader()
              },
          )
        }
      }

      // Marcador central vertical
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .width(3.dp)
          .fillMaxHeight()
          .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
      )
      Surface(
        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.align(Alignment.TopCenter),
      ) {
        Text(
          text = "OBJETIVO",
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
      }
    }
  }
}

@Composable
private fun TapePrizeTile(
  prize: RoulettePrize,
  isHighlighted: Boolean,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val borderStroke = remember(isHighlighted, prize.isHighRisk, prize.rarity, primaryColor) {
    val borderColor = when {
      isHighlighted -> primaryColor
      prize.isHighRisk -> Color(0xFFEF4444)
      else -> prize.rarity.accentColor.copy(alpha = 0.7f)
    }
    BorderStroke(if (isHighlighted) 2.5.dp else 1.5.dp, borderColor)
  }
  val rarityTagText = remember(prize.isHighRisk, prize.rarity) {
    if (prize.isHighRisk) "ALTO RIESGO" else prize.rarity.label.uppercase(Locale.getDefault())
  }
  val tileIcon = remember(prize.category) { prizeIcon(prize) }

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = borderStroke,
    shadowElevation = 0.dp,
    modifier = modifier,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxSize()
        .padding(8.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = prize.rarity.badgeBgColor,
      ) {
        Text(
          text = rarityTagText,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (prize.isHighRisk) Color(0xFFFCA5A5) else prize.rarity.accentColor,
          maxLines = 1,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
      }

      Icon(
        imageVector = tileIcon,
        contentDescription = prize.title,
        tint = prize.rarity.accentColor,
        modifier = Modifier.size(28.dp),
      )

      Text(
        text = prize.title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastRewardBanner(
  record: RouletteSpinRecord,
  completedMissionTitle: String?,
  pendingClaimTitles: List<String>,
  unlockedTitles: List<String>,
  equippedTitle: String,
  onClaimTitle: (title: String, equipAfterClaim: Boolean) -> Unit,
  onEquipTitle: (String) -> Unit,
) {
  val wonTitle = record.claimableTitle ?: record.prize.unlockedTitle
  val isPendingClaim = wonTitle != null && wonTitle in pendingClaimTitles
  val isClaimed = wonTitle != null && wonTitle in unlockedTitles
  val isEquipped = wonTitle != null && wonTitle == equippedTitle

  Card(
    colors = CardDefaults.cardColors(
      containerColor = record.prize.rarity.badgeBgColor.copy(alpha = 0.85f),
    ),
    border = BorderStroke(1.dp, record.prize.rarity.accentColor.copy(alpha = 0.75f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("roulette_last_reward_card"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Surface(
          shape = CircleShape,
          color = record.prize.rarity.accentColor.copy(alpha = 0.2f),
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = prizeIcon(record.prize),
              contentDescription = null,
              tint = record.prize.rarity.accentColor,
            )
          }
        }

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Giro #${record.spinNumber} • ${record.prize.title}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
          )
          Text(
            text = buildString {
              append(record.prize.subtitle)
              if (record.bonusFloorXp > 0L) {
                append(" • ¡Carga 100%: +${record.bonusFloorXp} XP!")
              }
              if (record.totalXpGranted > 0L) {
                append(" • +${record.totalXpGranted} XP al perfil")
              }
            },
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
          )
          if (!completedMissionTitle.isNullOrBlank()) {
            Text(
              text = "Misión completada: $completedMissionTitle",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = Color(0xFF6EE7B7),
            )
          }
        }
      }

      if (wonTitle != null && (isPendingClaim || isClaimed)) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (isPendingClaim) {
            Button(
              onClick = { onClaimTitle(wonTitle, false) },
              modifier = Modifier.testTag("roulette_claim_last_title_button"),
            ) {
              Text("Reclamar título")
            }
            FilledTonalButton(
              onClick = { onClaimTitle(wonTitle, true) },
              modifier = Modifier.testTag("roulette_claim_and_equip_button"),
            ) {
              Text("Reclamar y equipar")
            }
          } else if (isClaimed) {
            FilledTonalButton(
              onClick = { onEquipTitle(if (isEquipped) "" else wonTitle) },
              modifier = Modifier.testTag("roulette_equip_last_title_button"),
            ) {
              Text(if (isEquipped) "Equipado en perfil (Quitar)" else "Equipar en perfil")
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnlocksAndMissionsSummaryCard(
  uiState: RouletteUiState,
  onClaimTitle: (title: String, equipAfterClaim: Boolean) -> Unit,
  onClaimAllTitles: () -> Unit,
  onEquipTitle: (String) -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("roulette_titles_catalog_card"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Títulos Especiales (${uiState.unlockedExclusiveTitles.size}/${RouletteCatalogDefaults.specialTitleVariants.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
          )
          if (uiState.activeTitleVariants.isNotEmpty()) {
            Text(
              text = "En giro actual: ${uiState.activeTitleVariants.joinToString(" • ")}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }
        if (uiState.pendingClaimTitles.size > 1) {
          FilledTonalButton(
            onClick = onClaimAllTitles,
            modifier = Modifier.testTag("roulette_claim_all_titles_button"),
          ) {
            Text("Reclamar (${uiState.pendingClaimTitles.size})")
          }
        }
      }

      if (uiState.pendingClaimTitles.isNotEmpty()) {
        Text(
          text = "Disponibles para reclamar:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = Color(0xFFFBBF24),
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          uiState.pendingClaimTitles.forEach { title ->
            key("pending_$title") {
              FilledTonalButton(
                onClick = { onClaimTitle(title, true) },
                modifier = Modifier.testTag("roulette_claim_title_$title"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.WorkspacePremium,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reclamar: $title")
              }
            }
          }
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        RouletteCatalogDefaults.specialTitleVariants.forEach { variant ->
          key(variant.id) {
            val isClaimed = variant.title in uiState.unlockedExclusiveTitles
            val isPending = variant.title in uiState.pendingClaimTitles
            val isEquipped = uiState.equippedProfileTitle == variant.title
            FilterChip(
              selected = isEquipped,
              enabled = isClaimed || isPending,
              onClick = {
                when {
                  isPending -> onClaimTitle(variant.title, true)
                  isClaimed -> onEquipTitle(if (isEquipped) "" else variant.title)
                }
              },
              leadingIcon = {
                Icon(
                  imageVector = when {
                    isEquipped -> Icons.Rounded.CheckCircle
                    isPending -> Icons.Rounded.WorkspacePremium
                    isClaimed -> Icons.Rounded.MilitaryTech
                    else -> Icons.Rounded.Lock
                  },
                  contentDescription = null,
                  tint = when {
                    isClaimed || isPending -> variant.accentColor
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                  },
                  modifier = Modifier.size(16.dp),
                )
              },
              label = {
                Text(
                  text = when {
                    isEquipped -> "${variant.title} • Equipado"
                    isPending -> "${variant.title} (Reclamar)"
                    else -> variant.title
                  },
                  fontWeight = if (isClaimed || isPending) FontWeight.Bold else FontWeight.Normal,
                )
              },
              modifier = Modifier.testTag("roulette_variant_chip_${variant.id}"),
            )
          }
        }
      }

      if (uiState.activeHardMissions.isNotEmpty()) {
        Text(
          text = "Misiones de Alto Riesgo Activas",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.error,
        )
        uiState.activeHardMissions.take(2).forEachIndexed { idx, missionText ->
          key("hard_$idx") {
            Text(
              text = "• $missionText",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      if (uiState.activeRandomMissions.isNotEmpty()) {
        Text(
          text = "Misiones Aleatorias Recientes",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
        uiState.activeRandomMissions.take(2).forEachIndexed { idx, missionText ->
          key("rnd_$idx") {
            Text(
              text = "• $missionText",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SpinMissionsCard(missions: List<RouletteSpinMission>) {
  // OPT: Cachear conteo completado cuando cambia la lista de misiones.
  val doneCount = remember(missions) { missions.count { it.isCompleted } }
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("roulette_spin_missions_card"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "5 Misiones de Ruleta Escalonadas",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "$doneCount/${missions.size}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      missions.forEach { mission ->
        // OPT: key(mission.id) + remember de etiqueta evita formateo y recomposición de filas intactas.
        key(mission.id) {
          val rewardLabel = remember(mission.rewardXp, mission.bonusLabel) {
            buildString {
              append("+${mission.rewardXp} XP")
              if (mission.bonusLabel != null) {
                append(" • ${mission.bonusLabel}")
              }
            }
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = if (mission.isCompleted) {
                    Icons.Rounded.CheckCircle
                  } else {
                    Icons.Rounded.AutoAwesome
                  },
                  contentDescription = null,
                  tint = if (mission.isCompleted) {
                    Color(0xFF34D399)
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "${mission.title} (${mission.currentSpins}/${mission.targetSpins})",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (mission.isCompleted) FontWeight.Bold else FontWeight.Medium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Text(
                text = rewardLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
            LinearProgressIndicator(
              progress = { mission.progressFraction },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouletteDebugPanel(
  uiState: RouletteUiState,
  animationTier: String = "high",
  onToggleExpand: () -> Unit,
  onSpinInstant: () -> Unit,
  onSimulate10: () -> Unit,
  onSimulate50: () -> Unit,
  onReset: () -> Unit,
) {
  val isLowTier = animationTier == "low"
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("roulette_debug_panel"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = "Panel Debug (En Memoria)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Simulación rápida y monitoreo anti-fórmula",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(onClick = onToggleExpand) {
          Icon(
            imageVector = if (uiState.debugPanelExpanded) {
              Icons.Rounded.ExpandLess
            } else {
              Icons.Rounded.ExpandMore
            },
            contentDescription = if (uiState.debugPanelExpanded) "Ocultar debug" else "Mostrar debug",
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        FilledTonalButton(
          onClick = onSpinInstant,
          enabled = !uiState.isSpinning,
          modifier = Modifier.testTag("roulette_debug_spin_1"),
        ) {
          Text("Girar x1")
        }
        FilledTonalButton(
          onClick = onSimulate10,
          enabled = !uiState.isSpinning,
          modifier = Modifier.testTag("roulette_debug_sim_10"),
        ) {
          Text("Simular x10")
        }
        FilledTonalButton(
          onClick = onSimulate50,
          enabled = !uiState.isSpinning,
          modifier = Modifier.testTag("roulette_debug_sim_50"),
        ) {
          Text("Simular x50")
        }
        OutlinedButton(
          onClick = onReset,
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
          ),
          modifier = Modifier.testTag("roulette_debug_reset"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reset")
        }
      }

      val renderDebugBody: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          HorizontalDivider()
          Text(
            text = "Porcentajes en Vivo y Banda de Oscilación",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )

          uiState.prizeProbabilities.forEach { snap ->
            key(snap.prize.id) {
              ProbabilityRow(snapshot = snap)
            }
          }

          HorizontalDivider()
          Text(
            text = "Historial Reciente (${uiState.spinHistory.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )

          if (uiState.spinHistory.isEmpty()) {
            Text(
              text = "Sin tiradas todavía. Usa Girar x1 o Simular x10/x50.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            val visibleHistory = remember(uiState.spinHistory) { uiState.spinHistory.take(12) }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              visibleHistory.forEach { rec ->
                key(rec.spinNumber) {
                  val metaText = remember(rec.winningProbabilityPercent, rec.bonusFloorXp, rec.wasSimulatedBatch) {
                    buildString {
                      append(String.format(Locale.US, "%.2f%%", rec.winningProbabilityPercent))
                      if (rec.bonusFloorXp > 0L) append(" +50 XP")
                      if (rec.wasSimulatedBatch) append(" [SIM]")
                    }
                  }
                  Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    Text(
                      text = "#${rec.spinNumber} • ${rec.prize.title}",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                      modifier = Modifier.weight(1f),
                    )
                    Text(
                      text = metaText,
                      style = MaterialTheme.typography.labelSmall,
                      color = rec.prize.rarity.accentColor,
                    )
                  }
                }
              }
            }
          }
        }
      }

      if (isLowTier) {
        if (uiState.debugPanelExpanded) {
          renderDebugBody()
        }
      } else {
        AnimatedVisibility(visible = uiState.debugPanelExpanded) {
          renderDebugBody()
        }
      }
    }
  }
}

@Composable
private fun ProbabilityRow(snapshot: PrizeProbabilitySnapshot) {
  // OPT: Recordar textos formateados y colores para no ejecutar String.format en recomposiciones ajenas.
  val deltaText = remember(snapshot.probability, snapshot.previousProbability) {
    val delta = snapshot.deltaPercent
    when {
      delta > 0.005 -> String.format(Locale.US, "+%.2f%%", delta)
      delta < -0.005 -> String.format(Locale.US, "%.2f%%", delta)
      else -> "0.00%"
    }
  }
  val percentText = remember(snapshot.probability) {
    String.format(Locale.US, "%.2f%%", snapshot.probabilityPercent)
  }
  val deltaColor = when {
    snapshot.deltaPercent > 0.005 -> Color(0xFF34D399)
    snapshot.deltaPercent < -0.005 -> Color(0xFFF87171)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  val tierColor = when (snapshot.oscillationTier) {
    OscillationTier.HIGH_VOLATILITY -> Color(0xFFF59E0B)
    OscillationTier.MODERATE -> Color(0xFF38BDF8)
    OscillationTier.CONTAINED -> Color(0xFFA78BFA)
  }

  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.weight(1f),
      ) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(snapshot.prize.rarity.accentColor),
        )
        Text(
          text = snapshot.prize.title,
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = tierColor.copy(alpha = 0.16f),
        ) {
          Text(
            text = snapshot.oscillationTier.label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = tierColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
          )
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = deltaText,
          style = MaterialTheme.typography.labelSmall,
          color = deltaColor,
        )
        Text(
          text = percentText,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    LinearProgressIndicator(
      progress = { snapshot.probability.toFloat().coerceIn(0f, 1f) },
      color = snapshot.prize.rarity.accentColor,
      trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
      modifier = Modifier
        .fillMaxWidth()
        .height(4.dp)
        .clip(CircleShape),
    )
  }
}

private fun shortWheelLabel(prize: RoulettePrize): String =
  prize.wheelShortLabel.ifBlank {
    when (prize.id) {
      "xp_1_floor" -> "1 XP+"
      "xp_25" -> "25 XP"
      "xp_100" -> "100 XP"
      "mission_random" -> "Misión"
      "xp_350" -> "350 XP"
      "mission_hard" -> "Riesgo!"
      RouletteCatalogDefaults.TITLE_SLOT_A_ID -> "Título A"
      RouletteCatalogDefaults.TITLE_SLOT_B_ID -> "Título B"
      "xp_1000" -> "1K XP"
      "free_level_jackpot" -> "+1 NV"
      else -> prize.title.take(7)
    }
  }

private fun prizeIcon(prize: RoulettePrize): ImageVector = when (prize.category) {
  RoulettePrizeCategory.XP -> Icons.Rounded.Bolt
  RoulettePrizeCategory.FREE_LEVEL -> Icons.Rounded.WorkspacePremium
  RoulettePrizeCategory.EXCLUSIVE_TITLE -> Icons.Rounded.MilitaryTech
  RoulettePrizeCategory.RANDOM_MISSION -> Icons.Rounded.Explore
  RoulettePrizeCategory.HARD_MISSION -> Icons.Rounded.WarningAmber
}
