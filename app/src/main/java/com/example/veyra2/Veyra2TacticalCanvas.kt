package com.example.veyra2

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

@Immutable
private data class PreparedCanvasMission(
  val mission: PhysicalMapMission,
  val labelText: String,
)

/**
 * Canvas táctico unificado para Veyra 2:
 * - Cero asignaciones de `Paint`, `Path` o listas dentro de `DrawScope`.
 * - En `CLOSED_MINIMAP`: dibuja solo la base del radar/entorno y CERO pines de misiones.
 * - En `OPEN_50 / 75 / 100`: revelado progresivo y gestos 50<->75 y tirón hacia abajo 100.
 * - En `LANDSCAPE_AIMING`: pan táctil + pinch-to-zoom + retícula central fija con círculo de mira.
 */
@Composable
fun Veyra2TacticalCanvas(
  uiState: Veyra2TacticalUiState,
  onScaleDragStarted: () -> Unit,
  onScaleDragDelta: (Float, Float) -> Unit,
  onScaleDragEnded: () -> Unit,
  onGestureBoostChanged: (Boolean) -> Unit,
  onPanMeters: (Float, Float) -> Unit,
  onZoomMultiplier: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val currentMode by rememberUpdatedState(uiState.mapMode)
  val currentZoom by rememberUpdatedState(uiState.aimZoomFactor)

  val revealAnim = remember { Animatable(uiState.mapRevealProgress) }
  LaunchedEffect(uiState.mapRevealProgress, uiState.isGestureDragging) {
    val target = uiState.mapRevealProgress
    if (uiState.isGestureDragging) {
      revealAnim.snapTo(target)
    } else {
      revealAnim.animateTo(
        targetValue = target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f),
      )
    }
  }

  // Pulso suave solo en reposo / movimiento; se congela automáticamente durante gestos 120Hz
  val pulseAnim = remember { Animatable(0f) }
  LaunchedEffect(uiState.refreshTier) {
    val dur = uiState.refreshTier.pulseDurationMs
    if (dur > 0) {
      pulseAnim.animateTo(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
          animation = tween(durationMillis = dur, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Restart,
        ),
      )
    } else {
      pulseAnim.snapTo(0f)
    }
  }

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val mapBgColor = MaterialTheme.colorScheme.surfaceContainerLowest
  val blockColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val fogColor = MaterialTheme.colorScheme.surfaceContainerLow
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface
  val routeColor = remember { Color(0xFFFB923C) } // Naranja táctico Veyra
  val targetColor = remember { Color(0xFFF43F5E) }

  // Precalcular etiquetas fuera de DrawScope
  val preparedDrawnMissions = remember(uiState.drawnMissions, uiState.playerPosition) {
    uiState.drawnMissions.map { m ->
      val dist = hypot(
        m.position.xMeters - uiState.playerPosition.xMeters,
        m.position.yMeters - uiState.playerPosition.yMeters,
      )
      val distLabel = if (dist >= 1000f) {
        String.format("%.1fkm", dist / 1000f)
      } else {
        "${dist.toInt()}m"
      }
      PreparedCanvasMission(
        mission = m,
        labelText = "${m.title} ($distLabel)",
      )
    }
  }

  val labelPaint = remember {
    Paint().apply {
      isAntiAlias = true
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
  }
  val routePath = remember { Path() }
  val playerConePath = remember { Path() }
  val rangeDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }
  val routeDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f) }
  val fogStops = remember { Array(4) { 0f to Color.Transparent } }

  var canvasMinDimPx by remember { mutableStateOf(600f) }

  val currentOnPanMeters by rememberUpdatedState(onPanMeters)
  val currentOnZoomMultiplier by rememberUpdatedState(onZoomMultiplier)
  val currentOnGestureBoost by rememberUpdatedState(onGestureBoostChanged)

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .background(mapBgColor)
      .testTag("veyra2_tactical_canvas")
      .pointerInput(uiState.mapMode) {
        if (currentMode.isLandscapeAiming) {
          val touchSlop = viewConfiguration.touchSlop
          awaitEachGesture {
            awaitFirstDown(requireUnconsumed = true)
            var pastTouchSlop = false
            var zoomAccum = 1f
            var panAccum = Offset.Zero
            var lockedToPinch = false
            currentOnGestureBoost(true)
            do {
              val event = awaitPointerEvent()
              val canceled = event.changes.any { it.isConsumed }
              if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (!pastTouchSlop) {
                  zoomAccum *= zoomChange
                  panAccum += panChange
                  val centroidSize = event.calculateCentroidSize(useCurrent = false)
                  val zoomMotion = kotlin.math.abs(1f - zoomAccum) * centroidSize
                  val panMotion = panAccum.getDistance()
                  if (zoomMotion > touchSlop || panMotion > touchSlop) {
                    pastTouchSlop = true
                    lockedToPinch = zoomMotion > touchSlop && event.changes.count { it.pressed } >= 2
                  }
                }
                if (pastTouchSlop) {
                  val safeZoom = if (zoomChange.isFinite() && zoomChange > 0f) {
                    zoomChange.coerceIn(0.82f, 1.22f)
                  } else {
                    1f
                  }
                  if (lockedToPinch || (safeZoom != 1f && event.changes.count { it.pressed } >= 2)) {
                    currentOnZoomMultiplier(safeZoom)
                  }
                  if (panChange != Offset.Zero && panChange.x.isFinite() && panChange.y.isFinite()) {
                    val metersToPxNow = ((canvasMinDimPx / 2f).coerceAtLeast(120f) / 720f) *
                      currentZoom.coerceIn(0.45f, 3.5f)
                    val metersPerPx = 1f / metersToPxNow.coerceAtLeast(0.01f)
                    currentOnPanMeters(-panChange.x * metersPerPx, -panChange.y * metersPerPx)
                  }
                  event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
              }
            } while (!canceled && event.changes.any { it.pressed })
            currentOnGestureBoost(false)
          }
        } else if (currentMode.isMapOpen) {
          detectDragGestures(
            onDragStart = { onScaleDragStarted() },
            onDragEnd = { onScaleDragEnded() },
            onDragCancel = { onScaleDragEnded() },
            onDrag = { change, amount ->
              change.consume()
              onScaleDragDelta(amount.x, amount.y)
            },
          )
        }
      },
  ) {
    val minDim = size.minDimension.coerceAtLeast(120f)
    canvasMinDimPx = minDim
    val center = Offset(size.width / 2f, size.height / 2f)
    val isLandscapeAim = uiState.mapMode.isLandscapeAiming
    val isClosed = !uiState.mapMode.isMapOpen
    val reveal = revealAnim.value

    // En modo horizontal apunta usando `cameraCenter` y `aimZoomFactor`;
    // en modo retrato centrado en el jugador usando escala progresiva 50/75/100.
    val activeCenterWorld = if (isLandscapeAim) uiState.cameraCenter else uiState.playerPosition
    val effectiveZoom = if (isLandscapeAim) {
      uiState.aimZoomFactor.coerceIn(0.45f, 3.5f)
    } else {
      when {
        isClosed -> 2.1f
        reveal <= 0.50f -> 2.15f + (1.15f - 2.15f) * (reveal / 0.50f)
        else -> 1.15f + (0.48f - 1.15f) * ((reveal - 0.50f) / 0.50f)
      }
    }
    val metersToPx = ((minDim / 2f) / 720f) * effectiveZoom

    val blockCorner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
    val majorStroke = 1.4.dp.toPx()
    val minorStroke = 0.8.dp.toPx()
    val routeStroke = 2.6.dp.toPx()
    val labelSizePx = 10.sp.toPx()

    // 1. Cuadrícula urbana offline-first
    val stepMeters = when {
      effectiveZoom >= 1.8f -> 80f
      effectiveZoom >= 0.9f -> 180f
      else -> 360f
    }
    val halfWorldW = (size.width / 2f) / metersToPx.coerceAtLeast(0.02f)
    val halfWorldH = (size.height / 2f) / metersToPx.coerceAtLeast(0.02f)
    val minGx = floor((activeCenterWorld.xMeters - halfWorldW) / stepMeters).toInt()
    val maxGx = ceil((activeCenterWorld.xMeters + halfWorldW) / stepMeters).toInt()
    val minGy = floor((activeCenterWorld.yMeters - halfWorldH) / stepMeters).toInt()
    val maxGy = ceil((activeCenterWorld.yMeters + halfWorldH) / stepMeters).toInt()

    val blockPx = stepMeters * 0.68f * metersToPx
    val blockSize = Size(blockPx, blockPx)

    for (gx in minGx until maxGx) {
      for (gy in minGy until maxGy) {
        if ((gx * 7 + gy * 11) % 5 == 0) {
          val bx = center.x + ((gx * stepMeters + stepMeters * 0.16f) - activeCenterWorld.xMeters) * metersToPx
          val by = center.y + ((gy * stepMeters + stepMeters * 0.16f) - activeCenterWorld.yMeters) * metersToPx
          drawRoundRect(
            color = blockColor.copy(alpha = if (isClosed) 0.20f else 0.42f),
            topLeft = Offset(bx, by),
            size = blockSize,
            cornerRadius = blockCorner,
          )
        }
      }
    }

    for (gx in minGx..maxGx) {
      val sx = center.x + (gx * stepMeters - activeCenterWorld.xMeters) * metersToPx
      val major = gx % 3 == 0
      drawLine(
        color = gridColor.copy(alpha = if (major) 0.34f else 0.14f),
        start = Offset(sx, 0f),
        end = Offset(sx, size.height),
        strokeWidth = if (major) majorStroke else minorStroke,
      )
    }
    for (gy in minGy..maxGy) {
      val sy = center.y + (gy * stepMeters - activeCenterWorld.yMeters) * metersToPx
      val major = gy % 3 == 0
      drawLine(
        color = gridColor.copy(alpha = if (major) 0.34f else 0.14f),
        start = Offset(0f, sy),
        end = Offset(size.width, sy),
        strokeWidth = if (major) majorStroke else minorStroke,
      )
    }

    // 2. Radio de Vista y Anillo límite de reciclaje (2 km)
    val playerScreenX = center.x + (uiState.playerPosition.xMeters - activeCenterWorld.xMeters) * metersToPx
    val playerScreenY = center.y + (uiState.playerPosition.yMeters - activeCenterWorld.yMeters) * metersToPx
    val playerScreen = Offset(playerScreenX, playerScreenY)
    val viewRadiusPx = uiState.effectiveScaleRadiusMeters * metersToPx

    drawCircle(
      color = primaryColor.copy(alpha = 0.06f),
      radius = viewRadiusPx,
      center = playerScreen,
    )
    drawCircle(
      color = primaryColor.copy(alpha = 0.55f),
      radius = viewRadiusPx,
      center = playerScreen,
      style = Stroke(width = 1.6.dp.toPx(), pathEffect = rangeDashEffect),
    )

    val pulseVal = pulseAnim.value
    if (pulseVal > 0f && !isClosed) {
      drawCircle(
        color = secondaryColor.copy(alpha = (1f - pulseVal) * 0.22f),
        radius = viewRadiusPx * (0.25f + 0.75f * pulseVal),
        center = playerScreen,
        style = Stroke(width = 1.5.dp.toPx()),
      )
    }

    // Anillo punteado de 2 km para visualizar físicamente el umbral de reciclaje
    val recyclePx = uiState.recycleThresholdMeters * metersToPx
    if (recyclePx < size.maxDimension * 2.5f && !isClosed) {
      drawCircle(
        color = routeColor.copy(alpha = 0.35f),
        radius = recyclePx,
        center = playerScreen,
        style = Stroke(width = 1.4.dp.toPx(), pathEffect = rangeDashEffect),
      )
    }

    // 3. Ruta trazada con la mira (solo si el mapa está abierto)
    if (!isClosed && uiState.routePoints.isNotEmpty()) {
      routePath.rewind()
      uiState.routePoints.forEachIndexed { idx, wp ->
        val wx = center.x + (wp.position.xMeters - activeCenterWorld.xMeters) * metersToPx
        val wy = center.y + (wp.position.yMeters - activeCenterWorld.yMeters) * metersToPx
        if (idx == 0) {
          routePath.moveTo(playerScreen.x, playerScreen.y)
        }
        routePath.lineTo(wx, wy)
      }
      drawPath(
        path = routePath,
        color = routeColor.copy(alpha = 0.88f),
        style = Stroke(width = routeStroke, cap = StrokeCap.Round, pathEffect = routeDashEffect),
      )
      uiState.routePoints.forEach { wp ->
        val wx = center.x + (wp.position.xMeters - activeCenterWorld.xMeters) * metersToPx
        val wy = center.y + (wp.position.yMeters - activeCenterWorld.yMeters) * metersToPx
        drawCircle(color = routeColor, radius = 6.dp.toPx(), center = Offset(wx, wy))
        drawCircle(color = Color.White, radius = 2.6.dp.toPx(), center = Offset(wx, wy))
      }
    }

    // 4. Objetivos marcados manualmente con la mira central
    if (!isClosed) {
      uiState.markedTargets.forEach { tgt ->
        val tx = center.x + (tgt.position.xMeters - activeCenterWorld.xMeters) * metersToPx
        val ty = center.y + (tgt.position.yMeters - activeCenterWorld.yMeters) * metersToPx
        if (tx in -60f..(size.width + 60f) && ty in -60f..(size.height + 60f)) {
          val rOuter = 11.dp.toPx()
          drawCircle(
            color = targetColor.copy(alpha = 0.28f),
            radius = rOuter * 1.45f,
            center = Offset(tx, ty),
          )
          drawCircle(
            color = targetColor,
            radius = rOuter,
            center = Offset(tx, ty),
            style = Stroke(width = 2.dp.toPx()),
          )
          drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = Offset(tx, ty))
        }
      }
    }

    // 5. Misiones diferidas: si mapMode == CLOSED_MINIMAP, `preparedDrawnMissions` está vacío (0 pines)
    preparedDrawnMissions.forEach { prep ->
      val m = prep.mission
      val mx = center.x + (m.position.xMeters - activeCenterWorld.xMeters) * metersToPx
      val my = center.y + (m.position.yMeters - activeCenterWorld.yMeters) * metersToPx
      if (mx in -64f..(size.width + 64f) && my in -64f..(size.height + 64f)) {
        val isUnderReticle = m.id == uiState.missionUnderReticleId
        val isSelected = m.id == uiState.selectedMissionId
        val baseRadius = when {
          isUnderReticle -> 12.5.dp.toPx()
          m.difficulty == Veyra2MissionDifficulty.HIGH_DIFFICULTY -> 10.5.dp.toPx()
          else -> 8.5.dp.toPx()
        }
        val markerTint = if (m.isClaimed) {
          secondaryColor.copy(alpha = 0.65f)
        } else {
          m.difficulty.markerColor
        }

        if (isUnderReticle || isSelected) {
          drawCircle(
            color = markerTint.copy(alpha = 0.28f),
            radius = baseRadius * 1.9f,
            center = Offset(mx, my),
          )
        }
        drawCircle(
          color = markerTint,
          radius = baseRadius,
          center = Offset(mx, my),
        )
        drawCircle(
          color = if (m.isClaimed) Color.Black.copy(alpha = 0.55f) else Color.White,
          radius = baseRadius * 0.42f,
          center = Offset(mx, my),
        )

        if (isUnderReticle || isSelected || effectiveZoom >= 0.95f) {
          labelPaint.color = onSurfaceColor.copy(alpha = 0.92f).toArgb()
          labelPaint.textSize = labelSizePx
          drawContext.canvas.nativeCanvas.drawText(
            prep.labelText,
            mx,
            my - baseRadius - 6.dp.toPx(),
            labelPaint,
          )
        }
      }
    }

    // 6. Velo periférico cuando está cerrado o en transición 50 -> 75 -> 100
    if (!isLandscapeAim) {
      val fogAlpha = if (isClosed) 0.80f else (1f - reveal).coerceIn(0f, 1f) * 0.65f
      if (fogAlpha > 0.03f) {
        val maxCanvasR = hypot(size.width, size.height) * 0.56f
        val clearRatio = if (isClosed) 0.22f else (0.34f + 0.52f * reveal).coerceIn(0.25f, 0.88f)
        val fogColorEdge = fogColor.copy(alpha = fogAlpha)
        fogStops[0] = 0f to Color.Transparent
        fogStops[1] = clearRatio to Color.Transparent
        fogStops[2] = (clearRatio + 0.14f).coerceAtMost(1f) to fogColorEdge
        fogStops[3] = 1f to fogColorEdge
        drawRect(
          brush = Brush.radialGradient(
            colorStops = fogStops,
            center = center,
            radius = maxCanvasR,
          ),
        )
      }
    }

    // 7. Jugador (cono de dirección + punto)
    val tipY = playerScreen.y - 20.dp.toPx()
    val baseY = playerScreen.y - 4.dp.toPx()
    val halfW = 8.dp.toPx()
    playerConePath.rewind()
    playerConePath.moveTo(playerScreen.x, tipY)
    playerConePath.lineTo(playerScreen.x - halfW, baseY)
    playerConePath.lineTo(playerScreen.x + halfW, baseY)
    playerConePath.close()

    rotate(degrees = uiState.playerHeadingDeg, pivot = playerScreen) {
      drawPath(path = playerConePath, color = primaryColor.copy(alpha = 0.55f))
    }
    drawCircle(color = Color.White, radius = 9.5.dp.toPx(), center = playerScreen)
    drawCircle(
      color = primaryColor,
      radius = 6.5.dp.toPx(),
      center = playerScreen,
      style = Stroke(width = 3.dp.toPx()),
    )

    // 8. Retícula central principal (cruz + círculo en el centro) en modo horizontal
    if (isLandscapeAim) {
      val hasSnap = uiState.missionUnderReticleId != null
      val reticleColor = if (hasSnap) routeColor else primaryColor
      val reticleCircleR = 22.dp.toPx()
      val innerCircleR = 4.dp.toPx()
      val armOuter = 36.dp.toPx()
      val armGap = 9.dp.toPx()
      val strokeW = if (hasSnap) 2.6.dp.toPx() else 2.0.dp.toPx()

      // Halo sutil para contraste en cualquier fondo
      drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = reticleCircleR,
        center = center,
        style = Stroke(width = strokeW + 2.dp.toPx()),
      )
      drawCircle(
        color = reticleColor,
        radius = reticleCircleR,
        center = center,
        style = Stroke(width = strokeW),
      )
      // Cruz principal (4 brazos alrededor del centro)
      drawLine(
        color = reticleColor,
        start = Offset(center.x - armOuter, center.y),
        end = Offset(center.x - armGap, center.y),
        strokeWidth = strokeW,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = reticleColor,
        start = Offset(center.x + armGap, center.y),
        end = Offset(center.x + armOuter, center.y),
        strokeWidth = strokeW,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = reticleColor,
        start = Offset(center.x, center.y - armOuter),
        end = Offset(center.x, center.y - armGap),
        strokeWidth = strokeW,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = reticleColor,
        start = Offset(center.x, center.y + armGap),
        end = Offset(center.x, center.y + armOuter),
        strokeWidth = strokeW,
        cap = StrokeCap.Round,
      )
      // Punto central de precisión
      drawCircle(color = reticleColor, radius = innerCircleR, center = center)
      drawCircle(color = Color.White, radius = innerCircleR * 0.45f, center = center)
    }
  }
}

/**
 * Joystick virtual analógico para desplazar la vista y apuntar con precisión en modo horizontal.
 * Eleva automáticamente el refresco a 120 Hz mientras el pulgar está activo y regresa al centro al soltar.
 */
@Composable
fun VirtualAimJoystick(
  onStickInput: (normalizedX: Float, normalizedY: Float) -> Unit,
  onStickActiveChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var knobOffset by remember { mutableStateOf(Offset.Zero) }
  var isActive by remember { mutableStateOf(false) }

  val currentOnStickInput by rememberUpdatedState(onStickInput)
  val currentOnActiveChanged by rememberUpdatedState(onStickActiveChanged)

  val primaryColor = MaterialTheme.colorScheme.primary
  val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f)
  val borderColor = if (isActive) primaryColor else MaterialTheme.colorScheme.outlineVariant

  LaunchedEffect(isActive) {
    currentOnActiveChanged(isActive)
    if (!isActive) return@LaunchedEffect
    var lastNanos = 0L
    while (isActive) {
      withFrameNanos { now ->
        if (lastNanos != 0L) {
          val dtSeconds = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
          val speedScale = dtSeconds * 60f
          if (knobOffset != Offset.Zero) {
            val mag = hypot(knobOffset.x, knobOffset.y).coerceIn(0f, 1f)
            val curvedGain = 0.35f + 0.65f * mag
            currentOnStickInput(
              knobOffset.x * curvedGain * speedScale,
              knobOffset.y * curvedGain * speedScale,
            )
          }
        }
        lastNanos = now
      }
    }
  }

  Surface(
    shape = CircleShape,
    color = containerColor,
    border = BorderStroke(1.5.dp, borderColor),
    modifier = modifier
      .size(116.dp)
      .testTag("veyra2_virtual_joystick")
      .pointerInput(Unit) {
        val maxRadius = (size.width / 2f) * 0.68f
        detectDragGestures(
          onDragStart = {
            isActive = true
          },
          onDragEnd = {
            isActive = false
            knobOffset = Offset.Zero
          },
          onDragCancel = {
            isActive = false
            knobOffset = Offset.Zero
          },
          onDrag = { change, dragAmount ->
            change.consume()
            val curPx = Offset(knobOffset.x * maxRadius, knobOffset.y * maxRadius) + dragAmount
            val dist = hypot(curPx.x, curPx.y)
            val clamped = if (dist > maxRadius && dist > 0f) {
              Offset(curPx.x / dist * maxRadius, curPx.y / dist * maxRadius)
            } else {
              curPx
            }
            knobOffset = Offset(
              x = (clamped.x / maxRadius).coerceIn(-1f, 1f),
              y = (clamped.y / maxRadius).coerceIn(-1f, 1f),
            )
          },
        )
      },
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = (size.minDimension / 2f) * 0.68f
        drawCircle(
          color = primaryColor.copy(alpha = 0.22f),
          radius = maxR,
          center = center,
          style = Stroke(width = 1.4.dp.toPx()),
        )
        val knobCenter = Offset(
          center.x + knobOffset.x * maxR,
          center.y + knobOffset.y * maxR,
        )
        drawCircle(
          color = primaryColor.copy(alpha = if (isActive) 0.95f else 0.72f),
          radius = 19.dp.toPx(),
          center = knobCenter,
        )
        drawCircle(
          color = Color.White.copy(alpha = 0.88f),
          radius = 7.dp.toPx(),
          center = knobCenter,
        )
      }
    }
  }
}
