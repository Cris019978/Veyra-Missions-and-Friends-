package com.example.maplab

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Pantalla autocontenida con arquitectura de 2 capas claramente separadas:
 * 1) Capa de Interfaz Principal (cabecera fija + barra base inferior + panel Debug discreto).
 * 2) Capa Única de Radar/Mapa (un solo componente con revelado progresivo del terreno 50 -> 75 -> 100).
 */
@Composable
fun MapScaleTestScreen(
  stateManager: MapScaleStateManager,
  onClose: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val uiState by stateManager.uiState.collectAsStateWithLifecycle()

  MapScaleTestScreenContent(
    uiState = uiState,
    onDragStarted = stateManager::onDragStarted,
    onDragDelta = stateManager::onDragDelta,
    onDragEnded = stateManager::onDragEnded,
    onDragCancelled = stateManager::onDragCancelled,
    onCycleScaleFromButton = stateManager::cycleScaleFromButton,
    onRestoreToggleButton = stateManager::restoreToggleButton,
    onForceScale = { stateManager.forceScale(it, source = "Debug") },
    onSimulateStep = stateManager::simulatePlayerStep,
    onSpawnNearMission = stateManager::spawnNearMission,
    onSpawnHardMission = stateManager::spawnHardDistantMission,
    onShuffleMissions = stateManager::shuffleMissions,
    onToggleDebugPanel = stateManager::toggleDebugPanel,
    onReset = stateManager::resetAll,
    onClose = onClose,
    modifier = modifier,
  )
}

@Composable
fun MapScaleTestScreenContent(
  uiState: MapScaleUiState,
  onDragStarted: () -> Unit,
  onDragDelta: (Float, Float) -> Unit,
  onDragEnded: () -> Unit,
  onDragCancelled: () -> Unit,
  onCycleScaleFromButton: () -> Unit,
  onRestoreToggleButton: () -> Unit,
  onForceScale: (MapViewScale) -> Unit,
  onSimulateStep: (Float, Float) -> Unit,
  onSpawnNearMission: () -> Unit,
  onSpawnHardMission: () -> Unit,
  onShuffleMissions: () -> Unit,
  onToggleDebugPanel: () -> Unit,
  onReset: () -> Unit,
  onClose: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val missionsInRadiusCount = remember(
    uiState.missions,
    uiState.playerPosition,
    uiState.effectiveViewRadiusMeters,
  ) {
    val radius = uiState.effectiveViewRadiusMeters.toFloat()
    uiState.missions.count { m ->
      hypot(
        m.position.xMeters - uiState.playerPosition.xMeters,
        m.position.yMeters - uiState.playerPosition.yMeters,
      ) <= radius
    }
  }

  val animatedReveal by animateFloatAsState(
    targetValue = uiState.mapRevealProgress,
    animationSpec = if (uiState.isSlidingBetween50And75) {
      tween(durationMillis = 45, easing = LinearEasing)
    } else {
      spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    },
    label = "layerRevealProgress",
  )

  val mainChromeBg by animateColorAsState(
    targetValue = if (animatedReveal > 0.72f) {
      MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
      MaterialTheme.colorScheme.background
    },
    animationSpec = tween(durationMillis = 240),
    label = "mainChromeDepth",
  )

  Surface(
    color = mainChromeBg,
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .testTag("map_lab_screen"),
  ) {
    Box(
      contentAlignment = Alignment.TopCenter,
      modifier = Modifier.fillMaxSize(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
      ) {
        // =====================================================================
        // CAPA 1A: INTERFAZ PRINCIPAL (Cabecera fija de la App - siempre visible)
        // =====================================================================
        MainAppHeaderLayer(
          uiState = uiState,
          missionsInRadiusCount = missionsInRadiusCount,
          onToggleDebugPanel = onToggleDebugPanel,
          onClose = onClose,
        )

        // =====================================================================
        // CAPA 2: CONTENEDOR DEDICADO DEL RADAR / MAPA ÚNICO (Separación visual)
        // =====================================================================
        BoxWithConstraints(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("map_layer_workspace"),
        ) {
          val minStageHeight = 250.dp.coerceAtMost(maxHeight * 0.85f)
          val midStageHeight = 330.dp.coerceAtMost(maxHeight * 0.92f)
          val maxStageHeight = maxHeight

          val targetHeight = if (animatedReveal <= 0.50f) {
            val t = (animatedReveal / 0.50f).coerceIn(0f, 1f)
            minStageHeight + (midStageHeight - minStageHeight) * t
          } else {
            val t = ((animatedReveal - 0.50f) / 0.50f).coerceIn(0f, 1f)
            midStageHeight + (maxStageHeight - midStageHeight) * t
          }

          val animatedCardHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(durationMillis = 70, easing = LinearEasing),
            label = "radarCardHeight",
          )

          // Marco sutil del espacio de exploración para distinguir visualmente la zona del mapa
          Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxSize(),
          ) {}

          UnifiedRadarMapLayer(
            uiState = uiState,
            animatedReveal = animatedReveal,
            onDragStarted = onDragStarted,
            onDragDelta = onDragDelta,
            onDragEnded = onDragEnded,
            onDragCancelled = onDragCancelled,
            onCycleScaleFromButton = onCycleScaleFromButton,
            onRestoreToggleButton = onRestoreToggleButton,
            modifier = Modifier
              .fillMaxWidth()
              .height(animatedCardHeight),
          )
        }

        // =====================================================================
        // PANEL DEBUG DISCRETO (Desplegable sin estorbar la exploración)
        // =====================================================================
        androidx.compose.animation.AnimatedVisibility(
          visible = uiState.debugPanelExpanded,
          enter = fadeIn(tween(180)) + expandVertically(tween(220)),
          exit = fadeOut(tween(150)) + shrinkVertically(tween(180)),
        ) {
          DiscreetDebugDrawer(
            uiState = uiState,
            missionsInRadiusCount = missionsInRadiusCount,
            onForceScale = onForceScale,
            onSimulateStep = onSimulateStep,
            onSpawnNearMission = onSpawnNearMission,
            onSpawnHardMission = onSpawnHardMission,
            onShuffleMissions = onShuffleMissions,
            onReset = onReset,
            onCloseDebug = onToggleDebugPanel,
          )
        }

        // =====================================================================
        // CAPA 1B: INTERFAZ PRINCIPAL (Barra inferior de estado y navegación base)
        // =====================================================================
        MainAppBottomBar(
          uiState = uiState,
          missionsInRadiusCount = missionsInRadiusCount,
          animatedReveal = animatedReveal,
          onToggleDebugPanel = onToggleDebugPanel,
        )
      }
    }
  }
}

@Composable
private fun MainAppHeaderLayer(
  uiState: MapScaleUiState,
  missionsInRadiusCount: Int,
  onToggleDebugPanel: () -> Unit,
  onClose: (() -> Unit)?,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 2.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("main_interface_header"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.weight(1f),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = "INTERFAZ PRINCIPAL",
              style = MaterialTheme.typography.labelSmall,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
          Text(
            text = "Veyra • Explorador",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "${uiState.activeScale.label} (${uiState.effectiveViewRadiusMeters} m) • $missionsInRadiusCount/${uiState.missions.size} puntos en rango",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.testTag("map_stat_scale"),
        )
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Surface(
          onClick = onToggleDebugPanel,
          shape = CircleShape,
          color = if (uiState.debugPanelExpanded) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
          border = BorderStroke(
            1.dp,
            if (uiState.debugPanelExpanded) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            },
          ),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("map_debug_toggle_chip"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.BugReport,
              contentDescription = "Abrir o cerrar panel Debug",
              tint = if (uiState.debugPanelExpanded) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.size(16.dp),
            )
            Text(
              text = "Debug",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        if (onClose != null) {
          IconButton(
            onClick = onClose,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("map_lab_close_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Volver al Radar principal",
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MainAppBottomBar(
  uiState: MapScaleUiState,
  missionsInRadiusCount: Int,
  animatedReveal: Float,
  onToggleDebugPanel: () -> Unit,
) {
  val selectedMission = remember(uiState.missions, uiState.selectedMissionId) {
    uiState.missions.firstOrNull { it.id == uiState.selectedMissionId } ?: uiState.missions.firstOrNull()
  }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 3.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("main_interface_bottom_bar"),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.secondaryContainer,
          modifier = Modifier.size(36.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (animatedReveal > 0.72f) Icons.Rounded.Explore else Icons.Rounded.Radar,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(20.dp),
            )
          }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
          Text(
            text = if (animatedReveal > 0.72f) {
              "Modo Mapa Amplio • $missionsInRadiusCount misiones visibles"
            } else {
              "Navegación Base • ${selectedMission?.title ?: "Sin objetivo"}"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = uiState.lastEventLog,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("map_telemetry_log"),
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      Surface(
        onClick = onToggleDebugPanel,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Text(
          text = "Pos (${uiState.playerPosition.xMeters.toInt()}, ${uiState.playerPosition.yMeters.toInt()})",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
private fun UnifiedRadarMapLayer(
  uiState: MapScaleUiState,
  animatedReveal: Float,
  onDragStarted: () -> Unit,
  onDragDelta: (Float, Float) -> Unit,
  onDragEnded: () -> Unit,
  onDragCancelled: () -> Unit,
  onCycleScaleFromButton: () -> Unit,
  onRestoreToggleButton: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val animatedZoom by animateFloatAsState(
    targetValue = uiState.effectiveZoomFactor,
    animationSpec = if (uiState.isSlidingBetween50And75) {
      tween(durationMillis = 45, easing = LinearEasing)
    } else {
      spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    },
    label = "mapZoomAnim",
  )

  val animatedRadiusMeters by animateFloatAsState(
    targetValue = uiState.effectiveViewRadiusMeters.toFloat(),
    animationSpec = if (uiState.isSlidingBetween50And75) {
      tween(durationMillis = 45, easing = LinearEasing)
    } else {
      spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    },
    label = "mapRadiusAnim",
  )

  val pulseAnim = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    pulseAnim.animateTo(
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
      ),
    )
  }

  val scale100Flash = remember { Animatable(0f) }
  LaunchedEffect(uiState.activeScale) {
    if (uiState.activeScale == MapViewScale.SCALE_100) {
      scale100Flash.snapTo(1f)
      scale100Flash.animateTo(0f, tween(durationMillis = 650))
    }
  }

  val borderColor by animateColorAsState(
    targetValue = when {
      animatedReveal > 0.72f -> MaterialTheme.colorScheme.primary
      animatedReveal > 0.25f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
      else -> MaterialTheme.colorScheme.outlineVariant
    },
    animationSpec = tween(durationMillis = 220),
    label = "mapBorderColor",
  )

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val surfaceMapBg = MaterialTheme.colorScheme.surfaceContainerLowest
  val fogVeilColor = MaterialTheme.colorScheme.surfaceContainerLow
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val labelTextColor = MaterialTheme.colorScheme.onSurface

  val markerLabelPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = surfaceMapBg),
    elevation = CardDefaults.cardElevation(defaultElevation = (4 + (8 * animatedReveal)).dp),
    border = BorderStroke(width = (1.2f + (1.0f * animatedReveal)).dp, color = borderColor),
    shape = RoundedCornerShape(26.dp),
    modifier = modifier.testTag("map_layer_card"),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(26.dp))
        .background(surfaceMapBg)
        .testTag("map_lab_canvas_container")
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = { onDragStarted() },
            onDragEnd = { onDragEnded() },
            onDragCancel = { onDragCancelled() },
            onDrag = { change, dragAmount ->
              change.consume()
              onDragDelta(dragAmount.x, dragAmount.y)
            },
          )
        },
    ) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .testTag("map_lab_canvas"),
      ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val halfMin = size.minDimension / 2f
        val metersToPx = (halfMin / 740f) * animatedZoom
        val radiusPx = animatedRadiusMeters * metersToPx

        // Horizonte de revelado progresivo:
        // En 50 (0f) revela justo un poco más del radio inmediato (235m),
        // en 75 (0.5f) revela entorno urbano medio (~680m),
        // en 100 (1f) abre el mapa completo (>1600m).
        val revealMetersHorizon = if (animatedReveal <= 0.50f) {
          val t = (animatedReveal / 0.50f).coerceIn(0f, 1f)
          235f + (680f - 235f) * t
        } else {
          val t = ((animatedReveal - 0.50f) / 0.50f).coerceIn(0f, 1f)
          680f + (1650f - 680f) * t
        }
        val revealPx = revealMetersHorizon * metersToPx

        // 1. Terreno progresivo offline-first: río curvo, avenidas y cuadrícula por bloques
        val stepMeters = if (animatedZoom >= 1.6f) 60f else if (animatedZoom >= 0.9f) 140f else 300f
        val visibleWorldHalfW = (size.width / 2f) / metersToPx.coerceAtLeast(0.05f)
        val visibleWorldHalfH = (size.height / 2f) / metersToPx.coerceAtLeast(0.05f)

        val minGridX = floor((uiState.playerPosition.xMeters - visibleWorldHalfW) / stepMeters).toInt()
        val maxGridX = ceil((uiState.playerPosition.xMeters + visibleWorldHalfW) / stepMeters).toInt()
        val minGridY = floor((uiState.playerPosition.yMeters - visibleWorldHalfH) / stepMeters).toInt()
        val maxGridY = ceil((uiState.playerPosition.yMeters + visibleWorldHalfH) / stepMeters).toInt()

        // Bloques urbanos con opacidad progresiva según cercanía al horizonte revelado
        for (gx in minGridX until maxGridX) {
          for (gy in minGridY until maxGridY) {
            if ((gx + gy * 3) % 4 == 0) {
              val worldCenterX = gx * stepMeters + stepMeters * 0.5f
              val worldCenterY = gy * stepMeters + stepMeters * 0.5f
              val distToBlock = hypot(
                worldCenterX - uiState.playerPosition.xMeters,
                worldCenterY - uiState.playerPosition.yMeters,
              )
              val terrainAlpha = smoothRevealFactor(distToBlock, revealMetersHorizon)
              if (terrainAlpha > 0.02f) {
                val bx = center.x + ((gx * stepMeters + stepMeters * 0.14f) - uiState.playerPosition.xMeters) * metersToPx
                val by = center.y + ((gy * stepMeters + stepMeters * 0.14f) - uiState.playerPosition.yMeters) * metersToPx
                val bw = stepMeters * 0.72f * metersToPx
                drawRoundRect(
                  color = blockColor.copy(alpha = 0.45f * terrainAlpha),
                  topLeft = Offset(bx, by),
                  size = Size(bw, bw),
                  cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
              }
            }
          }
        }

        for (gx in minGridX..maxGridX) {
          val worldX = gx * stepMeters
          val screenX = center.x + (worldX - uiState.playerPosition.xMeters) * metersToPx
          val isMajorAvenue = gx % 3 == 0
          drawLine(
            color = gridColor.copy(alpha = if (isMajorAvenue) 0.34f else 0.16f),
            start = Offset(screenX, 0f),
            end = Offset(screenX, size.height),
            strokeWidth = if (isMajorAvenue) 1.6.dp.toPx() else 0.9.dp.toPx(),
          )
        }
        for (gy in minGridY..maxGridY) {
          val worldY = gy * stepMeters
          val screenY = center.y + (worldY - uiState.playerPosition.yMeters) * metersToPx
          val isMajorAvenue = gy % 3 == 0
          drawLine(
            color = gridColor.copy(alpha = if (isMajorAvenue) 0.34f else 0.16f),
            start = Offset(0f, screenY),
            end = Offset(size.width, screenY),
            strokeWidth = if (isMajorAvenue) 1.6.dp.toPx() else 0.9.dp.toPx(),
          )
        }

        // Río / corredor natural que se descubre conforme amplías hacia 75 y 100
        if (animatedReveal > 0.12f) {
          val riverAlpha = ((animatedReveal - 0.12f) / 0.55f).coerceIn(0f, 1f) * 0.28f
          val riverPath = Path().apply {
            val startX = center.x + (-1100f - uiState.playerPosition.xMeters) * metersToPx
            val startY = center.y + (290f - uiState.playerPosition.yMeters) * metersToPx
            val ctrlX = center.x + (40f - uiState.playerPosition.xMeters) * metersToPx
            val ctrlY = center.y + (120f - uiState.playerPosition.yMeters) * metersToPx
            val endX = center.x + (1150f - uiState.playerPosition.xMeters) * metersToPx
            val endY = center.y + (-340f - uiState.playerPosition.yMeters) * metersToPx
            moveTo(startX, startY)
            quadraticTo(ctrlX, ctrlY, endX, endY)
          }
          drawPath(
            path = riverPath,
            color = Color(0xFF0EA5E9).copy(alpha = riverAlpha),
            style = Stroke(width = (14f * animatedZoom).coerceIn(5f, 22f), cap = StrokeCap.Round),
          )
        }

        // 2. Zonas y puntos de referencia del entorno (Parques, Plazas, Distritos, Miradores)
        uiState.landmarks.forEach { landmark ->
          val dx = landmark.position.xMeters - uiState.playerPosition.xMeters
          val dy = landmark.position.yMeters - uiState.playerPosition.yMeters
          val dist = hypot(dx, dy)
          val revealFactor = smoothRevealFactor(dist, revealMetersHorizon)
          if (revealFactor > 0.04f) {
            val lx = center.x + dx * metersToPx
            val ly = center.y + dy * metersToPx
            val zoneRadiusPx = (landmark.radiusMeters * metersToPx).coerceAtLeast(10.dp.toPx())

            drawCircle(
              color = landmark.type.accentColor.copy(alpha = 0.14f * revealFactor),
              radius = zoneRadiusPx,
              center = Offset(lx, ly),
            )
            drawCircle(
              color = landmark.type.accentColor.copy(alpha = 0.42f * revealFactor),
              radius = zoneRadiusPx,
              center = Offset(lx, ly),
              style = Stroke(width = 1.2.dp.toPx()),
            )

            if (revealFactor > 0.45f) {
              markerLabelPaint.color = landmark.type.accentColor.copy(alpha = 0.85f * revealFactor).toArgb()
              markerLabelPaint.textSize = 9.sp.toPx()
              drawContext.canvas.nativeCanvas.drawText(
                landmark.name,
                lx,
                ly + 3.dp.toPx(),
                markerLabelPaint,
              )
            }
          }
        }

        // 3. Radio de Vista dinámico + pulso suave + flash de Vista 100
        drawCircle(
          color = primaryColor.copy(alpha = 0.08f),
          radius = radiusPx,
          center = center,
        )
        drawCircle(
          color = primaryColor.copy(alpha = (1f - pulseAnim.value) * 0.34f),
          radius = radiusPx * (0.28f + 0.72f * pulseAnim.value),
          center = center,
          style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
          color = primaryColor.copy(alpha = 0.85f),
          radius = radiusPx,
          center = center,
          style = Stroke(
            width = 2.2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
          ),
        )

        if (scale100Flash.value > 0.01f) {
          drawCircle(
            color = secondaryColor.copy(alpha = scale100Flash.value * 0.30f),
            radius = radiusPx * (1.0f + (1f - scale100Flash.value) * 0.16f),
            center = center,
            style = Stroke(width = 6.dp.toPx()),
          )
        }

        // 4. Puntos/Misiones con revelado progresivo según la apertura del radar (50 -> 75 -> 100)
        uiState.missions.forEach { mission ->
          val dx = mission.position.xMeters - uiState.playerPosition.xMeters
          val dy = mission.position.yMeters - uiState.playerPosition.yMeters
          val distMeters = hypot(dx, dy)
          val pointReveal = smoothRevealFactor(distMeters, revealMetersHorizon)

          if (pointReveal > 0.04f) {
            val mx = center.x + dx * metersToPx
            val my = center.y + dy * metersToPx
            val inViewRange = distMeters <= animatedRadiusMeters

            if (mx in -48f..(size.width + 48f) && my in -48f..(size.height + 48f)) {
              if (mission.isActive) {
                drawLine(
                  color = mission.difficulty.markerColor.copy(alpha = 0.45f * pointReveal),
                  start = center,
                  end = Offset(mx, my),
                  strokeWidth = 1.6.dp.toPx(),
                  pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                )
              }

              val baseRadius = when {
                mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY -> 11.dp.toPx()
                mission.isActive -> 9.5.dp.toPx()
                else -> 8.dp.toPx()
              }
              val scaledRadius = baseRadius * (0.65f + 0.35f * pointReveal)
              val finalAlpha = pointReveal * if (inViewRange) 1f else 0.72f

              if (mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY) {
                drawCircle(
                  color = mission.difficulty.markerColor.copy(alpha = 0.24f * finalAlpha),
                  radius = scaledRadius * 1.85f,
                  center = Offset(mx, my),
                )
              }

              drawCircle(
                color = mission.difficulty.markerColor.copy(alpha = finalAlpha),
                radius = scaledRadius,
                center = Offset(mx, my),
              )
              drawCircle(
                color = Color.White.copy(alpha = finalAlpha),
                radius = scaledRadius * 0.42f,
                center = Offset(mx, my),
              )

              val showLabel = pointReveal > 0.55f &&
                (animatedZoom >= 1.45f || mission.isActive || mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY)
              if (showLabel) {
                markerLabelPaint.color = labelTextColor.copy(alpha = 0.92f * finalAlpha).toArgb()
                markerLabelPaint.textSize = 10.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                  "${mission.title} (${distMeters.toInt()}m)",
                  mx,
                  my - scaledRadius - 6.dp.toPx(),
                  markerLabelPaint,
                )
              }
            }
          }
        }

        // 5. Velo periférico de apertura progresiva (en 50 cierra el perímetro; al subir a 75 y 100 se abre el mapa)
        val fogOuterAlpha = (1f - animatedReveal).coerceIn(0f, 1f) * 0.86f
        if (fogOuterAlpha > 0.02f) {
          val maxCanvasRadius = hypot(size.width, size.height) * 0.58f
          val innerClearStop = (revealPx * 0.72f / maxCanvasRadius).coerceIn(0.15f, 0.85f)
          val outerEdgeStop = (revealPx * 1.18f / maxCanvasRadius).coerceIn(innerClearStop + 0.06f, 1.0f)

          drawRect(
            brush = Brush.radialGradient(
              colorStops = arrayOf(
                0.0f to Color.Transparent,
                innerClearStop to Color.Transparent,
                outerEdgeStop to fogVeilColor.copy(alpha = fogOuterAlpha),
                1.0f to fogVeilColor.copy(alpha = fogOuterAlpha),
              ),
              center = center,
              radius = maxCanvasRadius,
            ),
          )
        }

        // 6. Jugador centrado con cono direccional
        rotate(degrees = uiState.playerHeadingDeg, pivot = center) {
          val cone = Path().apply {
            moveTo(center.x, center.y - 24.dp.toPx())
            lineTo(center.x - 10.dp.toPx(), center.y - 4.dp.toPx())
            lineTo(center.x + 10.dp.toPx(), center.y - 4.dp.toPx())
            close()
          }
          drawPath(path = cone, color = primaryColor.copy(alpha = 0.50f))
        }
        drawCircle(color = Color.White, radius = 11.dp.toPx(), center = center)
        drawCircle(
          color = primaryColor,
          radius = 7.5.dp.toPx(),
          center = center,
          style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // Etiqueta superior izquierda que identifica claramente la capa de Mapa/Radar
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.65f)),
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp)
          .testTag("map_layer_badge"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(primaryColor),
          )
          Text(
            text = "MAPA / RADAR • ${(animatedReveal * 100).toInt()}% revelado",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // Indicador superior al jalar hacia abajo buscando activar Vista 100
      androidx.compose.animation.AnimatedVisibility(
        visible = uiState.pullDown100Progress > 0.05f && uiState.activeScale != MapViewScale.SCALE_100,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 42.dp, start = 20.dp, end = 20.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
          border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
          modifier = Modifier.testTag("map_pull_down_100_indicator"),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          ) {
            Text(
              text = "Jala hacia abajo para abrir Vista 100 (${(uiState.pullDown100Progress * 100).toInt()}%)",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = primaryColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
              progress = { uiState.pullDown100Progress },
              modifier = Modifier
                .width(160.dp)
                .height(4.dp)
                .clip(CircleShape),
            )
          }
        }
      }

      // Etiqueta inferior izquierda con la escala activa en tiempo real
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(12.dp)
          .testTag("map_active_scale_badge"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.MyLocation,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = if (uiState.isSlidingBetween50And75) {
              val liveLevel = (50 + (25 * uiState.blend50To75)).toInt()
              "Escala $liveLevel • ${uiState.effectiveViewRadiusMeters} m"
            } else {
              "${uiState.activeScale.label} • ${uiState.effectiveViewRadiusMeters} m"
            },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // Botón dinámico de alternar vistas: se oculta/minimiza al deslizar entre 50 y 75,
      // y reaparece automáticamente al activar la Vista 100 (jalando hacia abajo).
      androidx.compose.animation.AnimatedVisibility(
        visible = uiState.isToggleButtonVisible,
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.82f, animationSpec = tween(220)),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.78f, animationSpec = tween(180)),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(12.dp),
      ) {
        FilledTonalButton(
          onClick = onCycleScaleFromButton,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("map_scale_toggle_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Layers,
            contentDescription = "Alternar vista 50, 75 o 100",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Vista ${uiState.activeScale.level} -> ${uiState.activeScale.nextScale().level}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }

      // Indicador discreto cuando el botón cedió protagonismo al gesto 50 <-> 75
      androidx.compose.animation.AnimatedVisibility(
        visible = !uiState.isToggleButtonVisible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(150)),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(10.dp),
      ) {
        Surface(
          onClick = onRestoreToggleButton,
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
          modifier = Modifier.testTag("map_gesture_mode_pill"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Swipe,
              contentDescription = null,
              tint = primaryColor,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "Gesto 50↔75 • Jala: 100",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscreetDebugDrawer(
  uiState: MapScaleUiState,
  missionsInRadiusCount: Int,
  onForceScale: (MapViewScale) -> Unit,
  onSimulateStep: (Float, Float) -> Unit,
  onSpawnNearMission: () -> Unit,
  onSpawnHardMission: () -> Unit,
  onShuffleMissions: () -> Unit,
  onReset: () -> Unit,
  onCloseDebug: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 6.dp)
      .heightIn(max = 260.dp)
      .testTag("map_debug_panel"),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(12.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Panel Debug Discreto • En rango: $missionsInRadiusCount/${uiState.missions.size} • Botón: ${uiState.buttonVisibilityMode.label}",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.weight(1f),
        )
        IconButton(
          onClick = onCloseDebug,
          modifier = Modifier.size(28.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cerrar debug",
            modifier = Modifier.size(16.dp),
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalButton(
          onClick = { onForceScale(MapViewScale.SCALE_50) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_force_50"),
        ) { Text("50", style = MaterialTheme.typography.labelMedium) }

        FilledTonalButton(
          onClick = { onForceScale(MapViewScale.SCALE_75) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_force_75"),
        ) { Text("75", style = MaterialTheme.typography.labelMedium) }

        FilledTonalButton(
          onClick = { onForceScale(MapViewScale.SCALE_100) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_force_100"),
        ) { Text("100", style = MaterialTheme.typography.labelMedium) }

        FilledTonalButton(
          onClick = { onSimulateStep(-45f, 0f) },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_move_left"),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Oeste", modifier = Modifier.size(16.dp)) }

        FilledTonalButton(
          onClick = { onSimulateStep(0f, -45f) },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_move_up"),
        ) { Icon(Icons.Rounded.ArrowUpward, contentDescription = "Norte", modifier = Modifier.size(16.dp)) }

        FilledTonalButton(
          onClick = { onSimulateStep(0f, 45f) },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_move_down"),
        ) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Sur", modifier = Modifier.size(16.dp)) }

        FilledTonalButton(
          onClick = { onSimulateStep(45f, 0f) },
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_move_right"),
        ) { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Este", modifier = Modifier.size(16.dp)) }
      }

      HorizontalDivider()

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalButton(
          onClick = onSpawnNearMission,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_spawn_near"),
        ) {
          Icon(Icons.Rounded.AddLocationAlt, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Cercana", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = onSpawnHardMission,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_spawn_hard"),
        ) {
          Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("+ Difícil", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = onShuffleMissions,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_shuffle"),
        ) {
          Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Mover", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = onReset,
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.testTag("map_debug_reset"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reset", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

/**
 * Curva suave [0..1] para revelar terreno, zonas y misiones progresivamente conforme
 * aumenta el horizonte del mapa entre las escalas 50 -> 75 -> 100.
 */
private fun smoothRevealFactor(distanceMeters: Float, revealHorizonMeters: Float): Float {
  val fadeStart = revealHorizonMeters * 0.72f
  val fadeEnd = revealHorizonMeters * 1.12f
  if (distanceMeters <= fadeStart) return 1f
  if (distanceMeters >= fadeEnd) return 0f
  val t = (distanceMeters - fadeStart) / (fadeEnd - fadeStart).coerceAtLeast(1f)
  return 1f - (t * t * (3f - 2f * t))
}
