package com.example.ui.components

import android.os.Build
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyPlayerItem
import com.example.data.model.PlayerProfile
import com.example.data.remote.OsmSafePlaceFinder
import com.example.util.GeoUtils
import com.example.util.HapticFeedbackHelper
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Niveles de apertura estética del radar integrado con OpenStreetMap:
 * - [SCALE_50]: radar encogido/cerrado (deja máximo espacio táctil a las misiones).
 * - [SCALE_75]: escala intermedia equilibrada con vista parcial de OSM.
 * - [SCALE_100]: radar expandido revelando un panorama amplio de OpenStreetMap.
 */
enum class RadarScaleLevel(
  val level: Int,
  val osmZoomOffset: Double,
  val osmRevealAlpha: Float,
  val veilOuterAlpha: Float,
  val veilInnerAlpha: Float,
) {
  SCALE_50(
    level = 50,
    osmZoomOffset = 1.15,
    osmRevealAlpha = 0.36f,
    veilOuterAlpha = 0.78f,
    veilInnerAlpha = 0.42f,
  ),
  SCALE_75(
    level = 75,
    osmZoomOffset = 0.0,
    osmRevealAlpha = 0.65f,
    veilOuterAlpha = 0.48f,
    veilInnerAlpha = 0.22f,
  ),
  SCALE_100(
    level = 100,
    osmZoomOffset = -1.25,
    osmRevealAlpha = 0.92f,
    veilOuterAlpha = 0.14f,
    veilInnerAlpha = 0.06f,
  );

  fun nextScale(): RadarScaleLevel = when (this) {
    SCALE_50 -> SCALE_75
    SCALE_75 -> SCALE_100
    SCALE_100 -> SCALE_50
  }
}

private fun RadarScaleLevel.toProgress(): Float = when (this) {
  RadarScaleLevel.SCALE_50 -> 0f
  RadarScaleLevel.SCALE_75 -> 0.5f
  RadarScaleLevel.SCALE_100 -> 1f
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
  return start + (end - start) * fraction
}

private fun interpolateRevealTriple(progress: Float): Triple<Float, Float, Float> {
  val p = progress.coerceIn(0f, 1f)
  return if (p <= 0.5f) {
    val t = p / 0.5f
    Triple(
      lerpFloat(RadarScaleLevel.SCALE_50.osmRevealAlpha, RadarScaleLevel.SCALE_75.osmRevealAlpha, t),
      lerpFloat(RadarScaleLevel.SCALE_50.veilOuterAlpha, RadarScaleLevel.SCALE_75.veilOuterAlpha, t),
      lerpFloat(RadarScaleLevel.SCALE_50.veilInnerAlpha, RadarScaleLevel.SCALE_75.veilInnerAlpha, t),
    )
  } else {
    val t = (p - 0.5f) / 0.5f
    Triple(
      lerpFloat(RadarScaleLevel.SCALE_75.osmRevealAlpha, RadarScaleLevel.SCALE_100.osmRevealAlpha, t),
      lerpFloat(RadarScaleLevel.SCALE_75.veilOuterAlpha, RadarScaleLevel.SCALE_100.veilOuterAlpha, t),
      lerpFloat(RadarScaleLevel.SCALE_75.veilInnerAlpha, RadarScaleLevel.SCALE_100.veilInnerAlpha, t),
    )
  }
}

@Immutable
private data class PlottedMissionSpec(
  val item: MissionWithDistance,
  val unitX: Float,
  val unitY: Float,
)

@Immutable
private data class PlottedPlayerSpec(
  val item: NearbyPlayerItem,
  val unitX: Float,
  val unitY: Float,
  val dotColor: Color,
)

@Composable
fun RoundRadarMap(
  myProfile: PlayerProfile?,
  nearbyPlayers: List<NearbyPlayerItem>,
  missions: List<MissionWithDistance>,
  searchRadiusMeters: Long,
  radarStyle: String,
  animationTier: String = "high",
  isBroadcasting: Boolean,
  hasLocationPermission: Boolean,
  activeAcceptedMissionId: String? = null,
  pauseAnimations: Boolean = false,
  userLat: Double = myProfile?.latitude?.takeIf { it != 0.0 } ?: 19.4326,
  userLon: Double = myProfile?.longitude?.takeIf { it != 0.0 } ?: -99.1332,
  radarScale: RadarScaleLevel = RadarScaleLevel.SCALE_75,
  expandedHeightDp: Dp = 420.dp,
  onRadarScaleChange: (RadarScaleLevel) -> Unit = {},
  onPlayerClick: (NearbyPlayerItem) -> Unit,
  onMissionClick: (MissionWithDistance) -> Unit,
  onCycleRadius: () -> Unit,
  onExpandMap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val isRobolectric = remember { Build.FINGERPRINT.contains("robolectric", ignoreCase = true) }

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
  val outlineVariant = MaterialTheme.colorScheme.outlineVariant
  val onSurface = MaterialTheme.colorScheme.onSurface

  val isLowTier = animationTier == "low"
  val isHighTier = animationTier != "low" && animationTier != "medium"

  var showExpandHint by remember { mutableStateOf(false) }
  var isDragging50To75 by remember { mutableStateOf(false) }
  var isDraggingGesture by remember { mutableStateOf(false) }
  var lastAppliedOsmSignature by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
  var osmMapView by remember { mutableStateOf<MapView?>(null) }
  var isOsmAttachedAndReady by remember { mutableStateOf(false) }

  val currentScaleState by rememberUpdatedState(radarScale)
  val currentOnScaleChange by rememberUpdatedState(onRadarScaleChange)

  // Un solo driver de progreso [0f=50, 0.5f=75, 1f=100]:
  // durante el arrastre sigue directo 1:1 al dedo (snapTo), y al soltar desacelera limpio sin animaciones compitiendo.
  val scaleProgressAnim = remember { Animatable(radarScale.toProgress()) }
  LaunchedEffect(radarScale, isDraggingGesture, animationTier) {
    if (!isDraggingGesture) {
      val target = radarScale.toProgress()
      when {
        isLowTier -> scaleProgressAnim.snapTo(target)
        isHighTier -> scaleProgressAnim.animateTo(
          targetValue = target,
          animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
        )
        else -> scaleProgressAnim.animateTo(
          targetValue = target,
          animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        )
      }
    }
  }

  DisposableEffect(lifecycleOwner, osmMapView, pauseAnimations) {
    val map = osmMapView
    if (map == null) {
      onDispose { }
    } else {
      if (pauseAnimations) {
        runCatching { map.onPause() }
      } else {
        runCatching { map.onResume() }
      }
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME -> if (!pauseAnimations) runCatching { map.onResume() }
          Lifecycle.Event.ON_PAUSE -> runCatching { map.onPause() }
          else -> Unit
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        runCatching { map.onPause() }
      }
    }
  }

  // Gama Baja y Media solo mantienen el barrido esencial; Gama Alta añade 1 onda sonar y respiro sutil.
  val essentialTransition = if (!pauseAnimations && isBroadcasting) {
    rememberInfiniteTransition(label = "veyraEssentialSweep")
  } else {
    null
  }
  val sweepAngleState: State<Float> = if (essentialTransition != null) {
    essentialTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = if (isLowTier) 3800 else 3200, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "sweepAngle",
    )
  } else {
    remember { mutableFloatStateOf(45f) }
  }

  val highTransition = if (!pauseAnimations && isHighTier) {
    rememberInfiniteTransition(label = "veyraHighExtras")
  } else {
    null
  }
  val sonarWave1State: State<Float> = if (highTransition != null && isBroadcasting) {
    highTransition.animateFloat(
      initialValue = 0.08f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "sonarWave1",
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }
  val pulseBreathState: State<Float> = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 0.96f,
      targetValue = 1.05f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "pulseBreath",
    )
  } else {
    remember { mutableFloatStateOf(1.0f) }
  }

  // Precalcular vectores unitarios fuera de layout/draw: cambiar de 50 <-> 75 <-> 100 no recalcula ni recompone pines.
  val plottedMissions = remember(missions, searchRadiusMeters) {
    missions.take(10).map { item ->
      val normalizedDist = (item.distanceMeters / searchRadiusMeters.toFloat()).coerceIn(0.18f, 0.84f)
      val angleRad = Math.toRadians((item.bearingDegrees - 90f).toDouble())
      val factor = 0.80f * normalizedDist
      PlottedMissionSpec(
        item = item,
        unitX = (factor * cos(angleRad)).toFloat(),
        unitY = (factor * sin(angleRad)).toFloat(),
      )
    }
  }
  val plottedPlayers = remember(nearbyPlayers, searchRadiusMeters, primaryColor) {
    nearbyPlayers.take(8).mapIndexed { idx, playerItem ->
      val normalizedDist = if (playerItem.distanceMeters > 0f) {
        (playerItem.distanceMeters / searchRadiusMeters.toFloat()).coerceIn(0.20f, 0.84f)
      } else {
        0.38f + (idx * 0.08f).coerceAtMost(0.42f)
      }
      val bearing = if (playerItem.distanceMeters > 0f) {
        playerItem.bearingDegrees
      } else {
        (idx * 55f) + 25f
      }
      val angleRad = Math.toRadians((bearing - 90f).toDouble())
      val factor = 0.80f * normalizedDist
      val dotColor = runCatching {
        Color(android.graphics.Color.parseColor(playerItem.profile.avatarColorHex))
      }.getOrElse { primaryColor }
      PlottedPlayerSpec(
        item = playerItem,
        unitX = (factor * cos(angleRad)).toFloat(),
        unitY = (factor * sin(angleRad)).toFloat(),
        dotColor = dotColor,
      )
    }
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      val maxContainerWidthPx = with(density) { maxWidth.toPx() }
      val size50Px = with(density) { 212.dp.toPx() }.coerceAtMost(maxContainerWidthPx)
      val size75Px = with(density) { 284.dp.toPx() }.coerceAtMost(maxContainerWidthPx)
      val height100Px = with(density) { expandedHeightDp.coerceAtLeast(320.dp).toPx() }
      val corner50Px = size50Px / 2f
      val corner75Px = size75Px / 2f
      val corner100Px = with(density) { 26.dp.toPx() }

      val currentBoxSizePx: () -> Pair<Float, Float> = remember(
        scaleProgressAnim,
        size50Px,
        size75Px,
        maxContainerWidthPx,
        height100Px,
      ) {
        {
          val p = scaleProgressAnim.value.coerceIn(0f, 1f)
          if (p <= 0.5f) {
            val t = p / 0.5f
            val w = lerpFloat(size50Px, size75Px, t)
            w to w
          } else {
            val t = (p - 0.5f) / 0.5f
            val w = lerpFloat(size75Px, maxContainerWidthPx, t)
            val h = lerpFloat(size75Px, height100Px, t)
            w to h
          }
        }
      }
      val currentRadarRadiusPx: () -> Float = remember(currentBoxSizePx) {
        {
          val (w, h) = currentBoxSizePx()
          min(w, h) / 2f
        }
      }
      val currentCornerPx: () -> Float = remember(scaleProgressAnim, corner50Px, corner75Px, corner100Px) {
        {
          val p = scaleProgressAnim.value.coerceIn(0f, 1f)
          if (p <= 0.5f) {
            lerpFloat(corner50Px, corner75Px, p / 0.5f)
          } else {
            lerpFloat(corner75Px, corner100Px, (p - 0.5f) / 0.5f)
          }
        }
      }

      val borderStrokeColor = if (radarScale == RadarScaleLevel.SCALE_100) {
        primaryColor
      } else {
        primaryColor.copy(alpha = 0.72f)
      }
      val highBorderBrush = remember(borderStrokeColor, tertiaryColor, secondaryColor) {
        Brush.sweepGradient(
          listOf(
            borderStrokeColor,
            tertiaryColor.copy(alpha = 0.65f),
            secondaryColor.copy(alpha = 0.45f),
            borderStrokeColor,
          ),
        )
      }
      Box(
        modifier = Modifier
          .layout { measurable, constraints ->
            val (wPx, hPx) = currentBoxSizePx()
            val wInt = wPx.roundToInt().coerceIn(0, constraints.maxWidth)
            val hInt = hPx.roundToInt().coerceIn(0, constraints.maxHeight)
            val placeable = measurable.measure(
              constraints.copy(minWidth = wInt, maxWidth = wInt, minHeight = hInt, maxHeight = hInt),
            )
            layout(wInt, hInt) {
              placeable.placeRelative(0, 0)
            }
          }
          .graphicsLayer {
            val cPx = currentCornerPx()
            shape = RoundedCornerShape(cPx)
            clip = true
            shadowElevation = 0f
          }
          .background(surfaceContainer)
          .testTag("round_radar_map")
          .pointerInput(Unit) {
            var dragDx = 0f
            var dragDy = 0f
            var handledVertical = false
            var startScale = RadarScaleLevel.SCALE_75
            detectDragGestures(
              onDragStart = {
                dragDx = 0f
                dragDy = 0f
                handledVertical = false
                startScale = currentScaleState
                isDraggingGesture = true
              },
              onDragEnd = {
                if (!handledVertical && startScale != RadarScaleLevel.SCALE_100) {
                  val curProgress = scaleProgressAnim.value
                  val target = when {
                    abs(dragDx) >= 56f -> if (startScale == RadarScaleLevel.SCALE_50) {
                      RadarScaleLevel.SCALE_75
                    } else {
                      RadarScaleLevel.SCALE_50
                    }
                    curProgress < 0.25f -> RadarScaleLevel.SCALE_50
                    else -> RadarScaleLevel.SCALE_75
                  }
                  if (target != currentScaleState) {
                    currentOnScaleChange(target)
                  }
                }
                isDragging50To75 = false
                isDraggingGesture = false
              },
              onDragCancel = {
                isDragging50To75 = false
                isDraggingGesture = false
              },
              onDrag = { change, amount ->
                change.consume()
                dragDx += amount.x
                dragDy += amount.y
                if (abs(dragDy) > abs(dragDx) * 1.15f) {
                  if (startScale != RadarScaleLevel.SCALE_100 && dragDy > 0f) {
                    val baseP = startScale.toProgress()
                    val previewP = lerpFloat(baseP, 1f, (dragDy / 120f).coerceIn(0f, 1f))
                    scope.launch { scaleProgressAnim.snapTo(previewP) }
                    if (dragDy >= 105f && !handledVertical) {
                      handledVertical = true
                      isDragging50To75 = false
                      currentOnScaleChange(RadarScaleLevel.SCALE_100)
                    }
                  } else if (startScale == RadarScaleLevel.SCALE_100 && dragDy < 0f) {
                    val previewP = lerpFloat(1f, 0.5f, (abs(dragDy) / 100f).coerceIn(0f, 1f))
                    scope.launch { scaleProgressAnim.snapTo(previewP) }
                    if (dragDy <= -80f && !handledVertical) {
                      handledVertical = true
                      currentOnScaleChange(RadarScaleLevel.SCALE_75)
                    }
                  }
                } else if (startScale != RadarScaleLevel.SCALE_100 && abs(dragDx) >= 8f) {
                  if (abs(dragDx) >= 16f) {
                    isDragging50To75 = true
                  }
                  val travelFraction = (abs(dragDx) / 150f).coerceIn(0f, 1f)
                  val liveP = if (startScale == RadarScaleLevel.SCALE_50) {
                    lerpFloat(0f, 0.5f, travelFraction)
                  } else {
                    lerpFloat(0.5f, 0f, travelFraction)
                  }
                  scope.launch { scaleProgressAnim.snapTo(liveP) }
                }
              },
            )
          }
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { showExpandHint = !showExpandHint },
          ),
        contentAlignment = Alignment.Center,
      ) {
        // Capa estética de OpenStreetMap integrada dentro del mismo radar:
        // conforme expande 50 -> 75 -> 100 revela más terreno real alrededor del jugador.
        if (!isRobolectric) {
          val baseZoom = when {
            searchRadiusMeters <= 600L -> 16.4
            searchRadiusMeters <= 2500L -> 15.2
            searchRadiusMeters <= 6000L -> 13.9
            else -> 12.6
          }
          val targetZoom = (baseZoom + radarScale.osmZoomOffset).coerceIn(10.5, 18.5)

          AndroidView(
            factory = { ctx ->
              OsmSafePlaceFinder.configureOsmdroid(ctx)
              MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                isEnabled = false
                isClickable = false
                isFocusable = false
                setOnTouchListener { _, _ -> false }
                minZoomLevel = 4.0
                maxZoomLevel = 19.5
                controller.setZoom(targetZoom)
                controller.setCenter(GeoPoint(userLat, userLon))
                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                  override fun onViewAttachedToWindow(v: View) {
                    v.post {
                      val mv = v as? MapView ?: return@post
                      if (mv.isAttachedToWindow && isOsmMapRepositoryReady(mv)) {
                        isOsmAttachedAndReady = true
                      }
                    }
                  }

                  override fun onViewDetachedFromWindow(v: View) {
                    isOsmAttachedAndReady = false
                    lastAppliedOsmSignature = null
                  }
                })
                osmMapView = this
              }
            },
            onRelease = { map ->
              isOsmAttachedAndReady = false
              lastAppliedOsmSignature = null
              if (osmMapView === map) {
                osmMapView = null
              }
              runCatching {
                map.overlays.clear()
                map.onPause()
                map.onDetach()
              }
            },
            update = { map ->
              if (pauseAnimations || isDraggingGesture) return@AndroidView
              if (!isOsmAttachedAndReady || !isOsmMapRepositoryReady(map)) {
                map.post {
                  if (map.isAttachedToWindow && isOsmMapRepositoryReady(map) && !isOsmAttachedAndReady) {
                    isOsmAttachedAndReady = true
                  }
                }
                return@AndroidView
              }
              val signature = Triple(userLat, userLon, targetZoom)
              if (lastAppliedOsmSignature != signature) {
                lastAppliedOsmSignature = signature
                runCatching {
                  if (isLowTier) {
                    map.controller.setZoom(targetZoom)
                    map.controller.setCenter(GeoPoint(userLat, userLon))
                  } else {
                    map.controller.animateTo(
                      GeoPoint(userLat, userLon),
                      targetZoom,
                      if (isHighTier) 280L else 150L,
                    )
                  }
                }
              }
            },
            modifier = Modifier
              .fillMaxSize()
              .graphicsLayer {
                alpha = interpolateRevealTriple(scaleProgressAnim.value).first
              },
          )
        }

        // Precalcular recursos del Canvas fuera de DrawScope
        val rangeDashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) }
        val compassUnitTicks = remember(radarStyle, isLowTier) {
          if (!isLowTier && (radarStyle == "compass" || radarStyle == "neon")) {
            val step = if (radarStyle == "compass") 15 else 30
            (0 until 360 step step).map { deg ->
              val rad = Math.toRadians(deg.toDouble())
              Triple(cos(rad).toFloat(), sin(rad).toFloat(), deg % 45 == 0)
            }
          } else {
            emptyList()
          }
        }
        val sweepStops = remember(primaryColor, radarStyle) {
          arrayOf(
            0.0f to primaryColor.copy(alpha = if (radarStyle == "neon") 0.42f else 0.24f),
            0.18f to primaryColor.copy(alpha = 0.08f),
            0.24f to Color.Transparent,
            1.0f to Color.Transparent,
          )
        }
        val veilColorsBuffer = remember {
          ArrayList<Color>(3).apply {
            add(Color.Transparent)
            add(Color.Transparent)
            add(Color.Transparent)
          }
        }
        val veilMidColor = remember(primaryColor, radarStyle) {
          primaryColor.copy(alpha = if (radarStyle == "neon") 0.14f else 0.08f)
        }

        // Capa estética del radar + borde en un solo Canvas ligero
        Canvas(modifier = Modifier.fillMaxSize()) {
          val center = Offset(size.width / 2f, size.height / 2f)
          val maxR = min(size.width, size.height) / 2f
          val sweepAngle = sweepAngleState.value
          val sonarWave1 = sonarWave1State.value
          val pulseBreath = pulseBreathState.value
          val (_, veilOuterA, veilInnerA) = interpolateRevealTriple(scaleProgressAnim.value)

          veilColorsBuffer[0] = surfaceContainer.copy(alpha = veilInnerA)
          veilColorsBuffer[1] = veilMidColor
          veilColorsBuffer[2] = surfaceContainer.copy(alpha = veilOuterA)

          drawRect(
            brush = Brush.radialGradient(
              colors = veilColorsBuffer,
              center = center,
              radius = maxR * 1.15f,
            ),
          )

          val ringCount = 3
          val ringColor = primaryColor.copy(alpha = if (radarStyle == "neon") 0.36f else 0.24f)
          for (i in 1..ringCount) {
            val r = maxR * (i / 3.25f)
            drawCircle(
              color = ringColor,
              radius = r,
              center = center,
              style = Stroke(
                width = if (i == ringCount) 2f else 1.4f,
                pathEffect = if (radarStyle == "classic" && i < ringCount) rangeDashEffect else null,
              ),
            )
          }

          val crosshairColor = outlineVariant.copy(alpha = 0.42f)
          drawLine(
            color = crosshairColor,
            start = Offset(center.x, center.y - maxR + 14f),
            end = Offset(center.x, center.y + maxR - 14f),
            strokeWidth = 1.2f,
          )
          drawLine(
            color = crosshairColor,
            start = Offset(center.x - maxR + 14f, center.y),
            end = Offset(center.x + maxR - 14f, center.y),
            strokeWidth = 1.2f,
          )

          if (compassUnitTicks.isNotEmpty()) {
            val outer = maxR - 10f
            val majorInner = maxR - 22f
            val minorInner = maxR - 15f
            val majorColor = primaryColor.copy(alpha = 0.72f)
            val minorColor = primaryColor.copy(alpha = 0.32f)
            compassUnitTicks.forEach { (cosVal, sinVal, isMajor) ->
              val inner = if (isMajor) majorInner else minorInner
              drawLine(
                color = if (isMajor) majorColor else minorColor,
                start = Offset(center.x + inner * cosVal, center.y + inner * sinVal),
                end = Offset(center.x + outer * cosVal, center.y + outer * sinVal),
                strokeWidth = if (isMajor) 2.2f else 1.2f,
              )
            }
          }

          if (isBroadcasting) {
            if (isHighTier && sonarWave1 > 0f) {
              val wave1R = maxR * sonarWave1
              val wave1Alpha = (1f - sonarWave1).coerceIn(0f, 1f) * 0.36f
              drawCircle(
                color = primaryColor.copy(alpha = wave1Alpha),
                radius = wave1R,
                center = center,
                style = Stroke(width = 2.5f),
              )
            }

            rotate(degrees = sweepAngle, pivot = center) {
              if (!isLowTier) {
                drawArc(
                  brush = Brush.sweepGradient(
                    colorStops = sweepStops,
                    center = center,
                  ),
                  startAngle = -80f,
                  sweepAngle = 82f,
                  useCenter = true,
                  topLeft = Offset(center.x - maxR, center.y - maxR),
                  size = androidx.compose.ui.geometry.Size(maxR * 2f, maxR * 2f),
                )
              }
              drawLine(
                color = primaryColor.copy(alpha = if (isLowTier) 0.72f else 0.92f),
                start = center,
                end = Offset(center.x + maxR - 8f, center.y),
                strokeWidth = if (isLowTier) 2.5f else 3f,
                cap = StrokeCap.Round,
              )
            }
          }

          drawCircle(
            color = primaryColor.copy(alpha = 0.22f),
            radius = 27f * pulseBreath,
            center = center,
          )
        }

        // Borde ligero separado (sin SweepGradient shader en Media/Baja)
        Box(
          modifier = Modifier
            .fillMaxSize()
            .let { baseMod ->
              val cPx = currentCornerPx()
              val shape = RoundedCornerShape(cPx)
              val borderWidth = if (radarScale == RadarScaleLevel.SCALE_100) 2.2.dp else 2.dp
              if (isHighTier) {
                baseMod.border(width = borderWidth, brush = highBorderBrush, shape = shape)
              } else {
                baseMod.border(width = 1.5.dp, color = borderStrokeColor, shape = shape)
              }
            },
        )

        // Cardinal markers N / E / S / O
        Text(
          text = "N",
          style = MaterialTheme.typography.labelSmall,
          fontWeight = FontWeight.Bold,
          color = primaryColor,
          modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 8.dp),
        )
        Text(
          text = "S",
          style = MaterialTheme.typography.labelSmall,
          color = onSurface.copy(alpha = 0.68f),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp),
        )
        Text(
          text = "O",
          style = MaterialTheme.typography.labelSmall,
          color = onSurface.copy(alpha = 0.68f),
          modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = 10.dp),
        )
        Text(
          text = "E",
          style = MaterialTheme.typography.labelSmall,
          color = onSurface.copy(alpha = 0.68f),
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 10.dp),
        )

        // Mission pins: offset dinámico sin recomponer ni lanzar 10 springs por cada barrido
        plottedMissions.forEach { spec ->
          val item = spec.item
          key(item.mission.id) {
            val isAccepted = item.mission.id == activeAcceptedMissionId
            val pinColor = remember(item.isCompletedByMe, item.mission.category, primaryColor, secondaryColor, tertiaryColor) {
              if (item.isCompletedByMe) {
                secondaryColor.copy(alpha = 0.60f)
              } else {
                MissionIcons.accentColorForMission(item.mission, primaryColor, tertiaryColor)
              }
            }
            val pinIcon = remember(item.mission.iconKey, item.mission.missionType, item.mission.category) {
              MissionIcons.iconForMission(item.mission)
            }
            val currentItem by rememberUpdatedState(item)
            val pinScale = when {
              isAccepted -> 1.15f
              item.isCompletedByMe -> 0.88f
              else -> 1.0f
            }

            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .offset {
                  val rPx = currentRadarRadiusPx()
                  IntOffset((spec.unitX * rPx).roundToInt(), (spec.unitY * rPx).roundToInt())
                }
                .size(48.dp)
                .graphicsLayer {
                  scaleX = pinScale
                  scaleY = pinScale
                }
                .clickable {
                  HapticFeedbackHelper.performRadarPinTap(context)
                  onMissionClick(currentItem)
                }
                .testTag("radar_mission_pin_${item.mission.id}")
                .semantics { contentDescription = "Misión ${item.mission.title}" },
              contentAlignment = Alignment.Center,
            ) {
              if (!item.isCompletedByMe && item.isWithinProximity) {
                Box(
                  modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(pinColor.copy(alpha = 0.34f)),
                )
              }
              Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                  shape = CircleShape,
                  color = pinColor,
                  shadowElevation = 0.dp,
                  modifier = Modifier
                    .size(26.dp)
                    .border(
                      width = if (item.isWithinProximity) 2.dp else 1.4.dp,
                      color = Color.White.copy(alpha = if (item.isWithinProximity) 1f else 0.78f),
                      shape = CircleShape,
                    ),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = pinIcon,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(14.dp),
                    )
                  }
                }
                if (!item.isWithinProximity && !item.isCompletedByMe) {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(12.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Lock,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(2.dp),
                    )
                  }
                }
              }
            }
          }
        }

        // Nearby online players plotted on the radar
        plottedPlayers.forEach { spec ->
          val playerItem = spec.item
          key(playerItem.profile.userId) {
            val currentPlayerItem by rememberUpdatedState(playerItem)
            val playerDotColor = spec.dotColor

            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .offset {
                  val rPx = currentRadarRadiusPx()
                  IntOffset((spec.unitX * rPx).roundToInt(), (spec.unitY * rPx).roundToInt())
                }
                .size(48.dp)
                .clickable {
                  HapticFeedbackHelper.performRadarPinTap(context)
                  onPlayerClick(currentPlayerItem)
                }
                .testTag("radar_player_dot_${playerItem.profile.userId}"),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                  if (playerItem.profile.seekingHelp) {
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .let { mod ->
                          if (isHighTier) {
                            mod.graphicsLayer {
                              val b = pulseBreathState.value
                              scaleX = b
                              scaleY = b
                            }
                          } else {
                            mod
                          }
                        }
                        .clip(CircleShape)
                        .background(tertiaryColor.copy(alpha = 0.28f)),
                    )
                  }
                  Surface(
                    shape = CircleShape,
                    color = playerDotColor,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                      .size(28.dp)
                      .border(
                        width = 2.dp,
                        color = if (playerItem.profile.seekingHelp) tertiaryColor else Color.White,
                        shape = CircleShape,
                      ),
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(
                        imageVector = if (playerItem.profile.seekingHelp) {
                          Icons.Rounded.VolunteerActivism
                        } else {
                          Icons.Rounded.Person
                        },
                        contentDescription = playerItem.profile.callsign,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                      )
                    }
                  }
                }
                Surface(
                  color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                  shape = RoundedCornerShape(6.dp),
                ) {
                  Text(
                    text = playerItem.profile.callsign.take(8),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                  )
                }
              }
            }
          }
        }

        // Center User Avatar Marker
        Surface(
          shape = CircleShape,
          color = primaryColor,
          shadowElevation = 0.dp,
          modifier = Modifier
            .align(Alignment.Center)
            .size(42.dp)
            .let { mod ->
              if (isHighTier && isBroadcasting) {
                mod.graphicsLayer {
                  val s = pulseBreathState.value
                  scaleX = s
                  scaleY = s
                }
              } else {
                mod
              }
            }
            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = if (hasLocationPermission) Icons.Rounded.MyLocation else Icons.Rounded.Explore,
              contentDescription = myProfile?.callsign ?: "Tú",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }

      // Barra flotante al tocar el radar: "Expandir mapa" + segundo botón de Lupa (50/75/100) totalmente separados
      if (isLowTier) {
        if (showExpandHint) {
          RadarFloatingActionPair(
            radarScale = radarScale,
            onExpandMap = {
              showExpandHint = false
              onExpandMap()
            },
            onCycleScale = {
              onRadarScaleChange(radarScale.nextScale())
            },
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 16.dp),
          )
        }
      } else {
        androidx.compose.animation.AnimatedVisibility(
          visible = showExpandHint,
          enter = fadeIn(tween(150)),
          exit = fadeOut(tween(120)),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp),
        ) {
          RadarFloatingActionPair(
            radarScale = radarScale,
            onExpandMap = {
              showExpandHint = false
              onExpandMap()
            },
            onCycleScale = {
              onRadarScaleChange(radarScale.nextScale())
            },
          )
        }
      }

      // Botones de esquina superior derecha separados sin superponerse:
      // [Lupa 50/75/100]  +  [Expandir mapa]
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp),
      ) {
        FilledTonalIconButton(
          onClick = {
            isDragging50To75 = false
            onRadarScaleChange(radarScale.nextScale())
          },
          modifier = Modifier
            .graphicsLayer {
              alpha = if (isDragging50To75 && radarScale != RadarScaleLevel.SCALE_100) 0.25f else 1f
            }
            .minimumInteractiveComponentSize()
            .testTag("radar_scale_lupa_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.Search,
              contentDescription = "Encoger o expandir vista del radar (${radarScale.level})",
            )
            Surface(
              shape = CircleShape,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp),
            ) {
              Text(
                text = radarScale.level.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 3.dp),
              )
            }
          }
        }

        FilledTonalIconButton(
          onClick = {
            showExpandHint = false
            onExpandMap()
          },
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("radar_expand_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.OpenInFull,
            contentDescription = "Expandir mapa del radar",
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Live Radar Telemetry Strip below circle
    Row(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(50))
          .clickable { onCycleRadius() }
          .testTag("radar_radius_chip"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "Radio: ${GeoUtils.formatDistance(searchRadiusMeters.toFloat())}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .clip(RoundedCornerShape(50))
          .clickable { onRadarScaleChange(radarScale.nextScale()) }
          .testTag("radar_scale_status_chip"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = "Escala del mapa",
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Vista ${radarScale.level}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
      }

      Surface(
        shape = RoundedCornerShape(50),
        color = if (isBroadcasting) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceVariant
        },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(
                if (isBroadcasting) primaryColor else MaterialTheme.colorScheme.outline,
              ),
          )
          Text(
            text = if (isBroadcasting) {
              "${nearbyPlayers.size} en vivo"
            } else {
              "Oculto"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (isBroadcasting) {
              MaterialTheme.colorScheme.onPrimaryContainer
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
          )
        }
      }
    }
  }
}

@Composable
private fun RadarFloatingActionPair(
  radarScale: RadarScaleLevel,
  onExpandMap: () -> Unit,
  onCycleScale: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
  ) {
    ExpandMapActionPill(onClick = onExpandMap)

    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.secondaryContainer,
      shadowElevation = 0.dp,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .clip(CircleShape)
        .clickable(onClick = onCycleScale)
        .testTag("radar_scale_lupa_pill_button"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Search,
          contentDescription = "Cambiar escala 50/75/100",
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = radarScale.level.toString(),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
    }
  }
}

@Composable
private fun ExpandMapActionPill(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primary,
    shadowElevation = 0.dp,
    modifier = modifier
      .minimumInteractiveComponentSize()
      .clip(CircleShape)
      .clickable(onClick = onClick)
      .testTag("radar_expand_pill"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.OpenInFull,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = "Expandir mapa",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
      )
    }
  }
}
