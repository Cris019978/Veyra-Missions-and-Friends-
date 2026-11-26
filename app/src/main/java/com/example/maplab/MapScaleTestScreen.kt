package com.example.maplab

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.MyLocation
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
import androidx.compose.ui.geometry.Offset
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
 * Pantalla completa autocontenida en Jetpack Compose para probar el Mapa de 3 vistas (50 / 75 / 100)
 * con gestos táctiles, Canvas offline-first y panel Debug en memoria.
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

@OptIn(ExperimentalLayoutApi::class)
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
  val missionsInRadiusCount = remember(uiState.missions, uiState.playerPosition, uiState.effectiveViewRadiusMeters) {
    val radius = uiState.effectiveViewRadiusMeters.toFloat()
    uiState.missions.count { m ->
      hypot(m.position.xMeters - uiState.playerPosition.xMeters, m.position.yMeters - uiState.playerPosition.yMeters) <= radius
    }
  }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .testTag("map_lab_screen"),
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
          .testTag("map_lab_lazy_list"),
      ) {
        item(key = "header") {
          MapLabHeader(
            uiState = uiState,
            missionsInRadiusCount = missionsInRadiusCount,
            onClose = onClose,
          )
        }

        item(key = "map_canvas_card") {
          OfflineGestureMapCard(
            uiState = uiState,
            onDragStarted = onDragStarted,
            onDragDelta = onDragDelta,
            onDragEnded = onDragEnded,
            onDragCancelled = onDragCancelled,
            onCycleScaleFromButton = onCycleScaleFromButton,
            onRestoreToggleButton = onRestoreToggleButton,
          )
        }

        item(key = "gesture_guide_strip") {
          GestureGuideStrip(uiState = uiState)
        }

        item(key = "debug_panel") {
          MapScaleDebugPanel(
            uiState = uiState,
            missionsInRadiusCount = missionsInRadiusCount,
            onToggleExpand = onToggleDebugPanel,
            onForceScale = onForceScale,
            onSimulateStep = onSimulateStep,
            onSpawnNearMission = onSpawnNearMission,
            onSpawnHardMission = onSpawnHardMission,
            onShuffleMissions = onShuffleMissions,
            onReset = onReset,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapLabHeader(
  uiState: MapScaleUiState,
  missionsInRadiusCount: Int,
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
          text = "Mapa Táctil 50 / 75 / 100",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(
          text = "Canvas offline-first • Gestos fluidos y botón dinámico",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            contentDescription = "Cerrar prueba de mapa",
          )
        }
      }
    }

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      MapMetricChip(
        label = "Escala",
        value = uiState.activeScale.level.toString(),
        tag = "map_stat_scale",
      )
      MapMetricChip(
        label = "Radio de Vista",
        value = "${uiState.effectiveViewRadiusMeters} m",
        tag = "map_stat_radius",
      )
      MapMetricChip(
        label = "En rango",
        value = "$missionsInRadiusCount/${uiState.missions.size}",
        tag = "map_stat_in_range",
      )
    }
  }
}

@Composable
private fun MapMetricChip(
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
private fun OfflineGestureMapCard(
  uiState: MapScaleUiState,
  onDragStarted: () -> Unit,
  onDragDelta: (Float, Float) -> Unit,
  onDragEnded: () -> Unit,
  onDragCancelled: () -> Unit,
  onCycleScaleFromButton: () -> Unit,
  onRestoreToggleButton: () -> Unit,
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

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val surfaceMapBg = MaterialTheme.colorScheme.surfaceContainerLowest
  val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
  val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.42f)
  val labelTextColor = MaterialTheme.colorScheme.onSurface

  val markerLabelPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
    border = BorderStroke(
      width = if (uiState.activeScale == MapViewScale.SCALE_100) 2.dp else 1.dp,
      color = if (uiState.activeScale == MapViewScale.SCALE_100) {
        primaryColor.copy(alpha = 0.75f)
      } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
      },
    ),
    shape = RoundedCornerShape(24.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(350.dp)
        .clip(RoundedCornerShape(24.dp))
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

        // 1. Cuadrícula urbana offline-first anclada al mundo en metros (se desplaza con el jugador)
        val stepMeters = if (animatedZoom >= 1.6f) 60f else if (animatedZoom >= 0.9f) 140f else 300f
        val visibleWorldHalfW = (size.width / 2f) / metersToPx.coerceAtLeast(0.05f)
        val visibleWorldHalfH = (size.height / 2f) / metersToPx.coerceAtLeast(0.05f)

        val minGridX = floor((uiState.playerPosition.xMeters - visibleWorldHalfW) / stepMeters).toInt()
        val maxGridX = ceil((uiState.playerPosition.xMeters + visibleWorldHalfW) / stepMeters).toInt()
        val minGridY = floor((uiState.playerPosition.yMeters - visibleWorldHalfH) / stepMeters).toInt()
        val maxGridY = ceil((uiState.playerPosition.yMeters + visibleWorldHalfH) / stepMeters).toInt()

        for (gx in minGridX..maxGridX) {
          val worldX = gx * stepMeters
          val screenX = center.x + (worldX - uiState.playerPosition.xMeters) * metersToPx
          drawLine(
            color = gridColor,
            start = Offset(screenX, 0f),
            end = Offset(screenX, size.height),
            strokeWidth = 1.dp.toPx(),
          )
        }
        for (gy in minGridY..maxGridY) {
          val worldY = gy * stepMeters
          val screenY = center.y + (worldY - uiState.playerPosition.yMeters) * metersToPx
          drawLine(
            color = gridColor,
            start = Offset(0f, screenY),
            end = Offset(size.width, screenY),
            strokeWidth = 1.dp.toPx(),
          )
        }

        // Bloques de referencia urbana para dar percepción inmediata de zoom (50 vs 75 vs 100)
        for (gx in minGridX until maxGridX) {
          for (gy in minGridY until maxGridY) {
            if ((gx + gy * 3) % 4 == 0) {
              val bx = center.x + ((gx * stepMeters + stepMeters * 0.14f) - uiState.playerPosition.xMeters) * metersToPx
              val by = center.y + ((gy * stepMeters + stepMeters * 0.14f) - uiState.playerPosition.yMeters) * metersToPx
              val bw = stepMeters * 0.72f * metersToPx
              drawRect(
                color = blockColor,
                topLeft = Offset(bx, by),
                size = androidx.compose.ui.geometry.Size(bw, bw),
              )
            }
          }
        }

        // 2. Radio de Vista dinámico + pulso suave + flash de Vista 100
        val radiusPx = animatedRadiusMeters * metersToPx
        drawCircle(
          color = primaryColor.copy(alpha = 0.09f),
          radius = radiusPx,
          center = center,
        )
        drawCircle(
          color = primaryColor.copy(alpha = (1f - pulseAnim.value) * 0.35f),
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
            color = secondaryColor.copy(alpha = scale100Flash.value * 0.32f),
            radius = radiusPx * (1.0f + (1f - scale100Flash.value) * 0.18f),
            center = center,
            style = Stroke(width = 6.dp.toPx()),
          )
        }

        // 3. Misiones locales con diferenciación por dificultad y estado activo
        uiState.missions.forEach { mission ->
          val dx = mission.position.xMeters - uiState.playerPosition.xMeters
          val dy = mission.position.yMeters - uiState.playerPosition.yMeters
          val distMeters = hypot(dx, dy)
          val mx = center.x + dx * metersToPx
          val my = center.y + dy * metersToPx
          val inViewRange = distMeters <= animatedRadiusMeters

          if (mx in -40f..(size.width + 40f) && my in -40f..(size.height + 40f)) {
            if (mission.isActive) {
              drawLine(
                color = mission.difficulty.markerColor.copy(alpha = 0.45f),
                start = center,
                end = Offset(mx, my),
                strokeWidth = 1.6.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
              )
            }

            val markerRadius = when {
              mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY -> 11.dp.toPx()
              mission.isActive -> 9.5.dp.toPx()
              else -> 8.dp.toPx()
            }
            val alpha = if (inViewRange) 1f else 0.45f

            if (mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY) {
              drawCircle(
                color = mission.difficulty.markerColor.copy(alpha = 0.24f * alpha),
                radius = markerRadius * 1.85f,
                center = Offset(mx, my),
              )
            }

            drawCircle(
              color = mission.difficulty.markerColor.copy(alpha = alpha),
              radius = markerRadius,
              center = Offset(mx, my),
            )
            drawCircle(
              color = Color.White.copy(alpha = alpha),
              radius = markerRadius * 0.42f,
              center = Offset(mx, my),
            )

            // Mostrar etiquetas cuando hay espacio (Vista 50 siempre, Vista 75/100 activas o difíciles)
            val showLabel = animatedZoom >= 1.55f ||
              mission.isActive ||
              mission.difficulty == MapMissionDifficulty.HIGH_DIFFICULTY
            if (showLabel) {
              markerLabelPaint.color = labelTextColor.copy(alpha = if (inViewRange) 0.92f else 0.6f).toArgb()
              markerLabelPaint.textSize = 10.sp.toPx()
              val badgeText = "${mission.title} (${distMeters.toInt()}m)"
              drawContext.canvas.nativeCanvas.drawText(
                badgeText,
                mx,
                my - markerRadius - 6.dp.toPx(),
                markerLabelPaint,
              )
            }
          }
        }

        // 4. Jugador centrado con cono direccional
        rotate(degrees = uiState.playerHeadingDeg, pivot = center) {
          val cone = Path().apply {
            moveTo(center.x, center.y - 24.dp.toPx())
            lineTo(center.x - 10.dp.toPx(), center.y - 4.dp.toPx())
            lineTo(center.x + 10.dp.toPx(), center.y - 4.dp.toPx())
            close()
          }
          drawPath(path = cone, color = primaryColor.copy(alpha = 0.48f))
        }
        drawCircle(
          color = Color.White,
          radius = 11.dp.toPx(),
          center = center,
        )
        drawCircle(
          color = primaryColor,
          radius = 7.5.dp.toPx(),
          center = center,
          style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // Indicador superior cuando jalas hacia abajo buscando activar Vista 100
      androidx.compose.animation.AnimatedVisibility(
        visible = uiState.pullDown100Progress > 0.05f && uiState.activeScale != MapViewScale.SCALE_100,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = 10.dp, start = 20.dp, end = 20.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
          border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.6f)),
          modifier = Modifier.testTag("map_pull_down_100_indicator"),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          ) {
            Text(
              text = "Jala hacia abajo para Vista 100 (${(uiState.pullDown100Progress * 100).toInt()}%)",
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
            text = "Cambiar (${uiState.activeScale.level} -> ${uiState.activeScale.nextScale().level})",
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
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
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
              text = "Gesto 50↔75 • Jala abajo: 100",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GestureGuideStrip(uiState: MapScaleUiState) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(14.dp),
    ) {
      Text(
        text = uiState.activeScale.subtitle,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "1) Desliza lateralmente con el dedo entre Vista 50 y 75 (oculta el botón). 2) Jala hacia abajo para activar Vista 100 y hacer reaparecer el botón.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = "Estado botón: ${uiState.buttonVisibilityMode.label} • ${uiState.lastEventLog}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("map_telemetry_log"),
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapScaleDebugPanel(
  uiState: MapScaleUiState,
  missionsInRadiusCount: Int,
  onToggleExpand: () -> Unit,
  onForceScale: (MapViewScale) -> Unit,
  onSimulateStep: (Float, Float) -> Unit,
  onSpawnNearMission: () -> Unit,
  onSpawnHardMission: () -> Unit,
  onShuffleMissions: () -> Unit,
  onReset: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("map_debug_panel"),
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
              text = "Panel Debug Mapa (En Memoria)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Posición: (${uiState.playerPosition.xMeters.toInt()}m, ${uiState.playerPosition.yMeters.toInt()}m) • En rango: $missionsInRadiusCount",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        IconButton(
          onClick = onToggleExpand,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
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
          onClick = { onForceScale(MapViewScale.SCALE_50) },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("map_debug_force_50"),
        ) {
          Text("Forzar 50")
        }
        FilledTonalButton(
          onClick = { onForceScale(MapViewScale.SCALE_75) },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("map_debug_force_75"),
        ) {
          Text("Forzar 75")
        }
        FilledTonalButton(
          onClick = { onForceScale(MapViewScale.SCALE_100) },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("map_debug_force_100"),
        ) {
          Text("Forzar 100")
        }
        OutlinedButton(
          onClick = onReset,
          colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
          ),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("map_debug_reset"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Reset")
        }
      }

      AnimatedVisibility(visible = uiState.debugPanelExpanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          HorizontalDivider()
          Text(
            text = "Simular Movimiento del Jugador (45 m)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FilledTonalButton(
              onClick = { onSimulateStep(-45f, 0f) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_move_left"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Mover Oeste", modifier = Modifier.size(18.dp))
            }
            FilledTonalButton(
              onClick = { onSimulateStep(0f, -45f) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_move_up"),
            ) {
              Icon(Icons.Rounded.ArrowUpward, contentDescription = "Mover Norte", modifier = Modifier.size(18.dp))
            }
            FilledTonalButton(
              onClick = { onSimulateStep(0f, 45f) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_move_down"),
            ) {
              Icon(Icons.Rounded.ArrowDownward, contentDescription = "Mover Sur", modifier = Modifier.size(18.dp))
            }
            FilledTonalButton(
              onClick = { onSimulateStep(45f, 0f) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_move_right"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Mover Este", modifier = Modifier.size(18.dp))
            }
          }

          HorizontalDivider()
          Text(
            text = "Generar / Mover Misiones de Prueba",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilledTonalButton(
              onClick = onSpawnNearMission,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_spawn_near"),
            ) {
              Icon(Icons.Rounded.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("+ Misión Cercana")
            }
            FilledTonalButton(
              onClick = onSpawnHardMission,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_spawn_hard"),
            ) {
              Icon(Icons.Rounded.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("+ Alta Dificultad")
            }
            OutlinedButton(
              onClick = onShuffleMissions,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("map_debug_shuffle"),
            ) {
              Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Reubicar")
            }
          }

          HorizontalDivider()
          Text(
            text = "Misiones en Escena (${uiState.missions.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            uiState.missions.take(6).forEach { mission ->
              val dist = hypot(
                mission.position.xMeters - uiState.playerPosition.xMeters,
                mission.position.yMeters - uiState.playerPosition.yMeters,
              )
              val inRange = dist <= uiState.effectiveViewRadiusMeters
              Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
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
                      .background(mission.difficulty.markerColor),
                  )
                  Text(
                    text = mission.title,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
                Text(
                  text = "${dist.toInt()}m • ${if (inRange) "EN RANGO" else "LEJOS"}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (inRange) FontWeight.Bold else FontWeight.Normal,
                  color = if (inRange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    }
  }
}
