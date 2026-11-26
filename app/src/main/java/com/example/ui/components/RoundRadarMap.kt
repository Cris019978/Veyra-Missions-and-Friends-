package com.example.ui.components

import android.os.Build
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
  var lastAppliedOsmSignature by remember { mutableStateOf<Triple<Double, Double, Double>?>(null) }
  var osmMapView by remember { mutableStateOf<MapView?>(null) }
  var isOsmAttachedAndReady by remember { mutableStateOf(false) }

  val currentScaleState by rememberUpdatedState(radarScale)
  val currentOnScaleChange by rememberUpdatedState(onRadarScaleChange)

  // Animación fluida del tamaño, forma y revelado de OpenStreetMap según escala 50 / 75 / 100
  val targetHeightDp = when (radarScale) {
    RadarScaleLevel.SCALE_50 -> 212.dp
    RadarScaleLevel.SCALE_75 -> 284.dp
    RadarScaleLevel.SCALE_100 -> expandedHeightDp.coerceAtLeast(320.dp)
  }
  val animatedHeightDp by animateDpAsState(
    targetValue = targetHeightDp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
    label = "radarHeightAnim",
  )

  val targetCornerDp = when (radarScale) {
    RadarScaleLevel.SCALE_50 -> 106.dp
    RadarScaleLevel.SCALE_75 -> 142.dp
    RadarScaleLevel.SCALE_100 -> 26.dp
  }
  val animatedCornerDp by animateDpAsState(
    targetValue = targetCornerDp,
    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
    label = "radarCornerAnim",
  )

  val animatedOsmAlpha by animateFloatAsState(
    targetValue = radarScale.osmRevealAlpha,
    animationSpec = tween(durationMillis = 280),
    label = "osmRevealAlphaAnim",
  )
  val animatedVeilOuterAlpha by animateFloatAsState(
    targetValue = radarScale.veilOuterAlpha,
    animationSpec = tween(durationMillis = 280),
    label = "veilOuterAlphaAnim",
  )
  val animatedVeilInnerAlpha by animateFloatAsState(
    targetValue = radarScale.veilInnerAlpha,
    animationSpec = tween(durationMillis = 280),
    label = "veilInnerAlphaAnim",
  )
  val lupaButtonAlpha by animateFloatAsState(
    targetValue = if (isDragging50To75 && radarScale != RadarScaleLevel.SCALE_100) 0.25f else 1.0f,
    animationSpec = tween(durationMillis = 160),
    label = "lupaButtonAlphaAnim",
  )
  val borderColor by animateColorAsState(
    targetValue = if (radarScale == RadarScaleLevel.SCALE_100) {
      primaryColor
    } else {
      primaryColor.copy(alpha = 0.72f)
    },
    animationSpec = tween(durationMillis = 220),
    label = "radarBorderColor",
  )

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

  // Keep animation drivers as State<Float> so only DrawScope / graphicsLayer / derivedStateOf
  // read them, preventing 60fps recompositions of all radar mission pins and player dots.
  val essentialTransition = if (!pauseAnimations) rememberInfiniteTransition(label = "veyraEssentialSweep") else null
  val sweepAngleState: State<Float> = if (essentialTransition != null) {
    essentialTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = if (isLowTier) 3400 else 2800, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "sweepAngle",
    )
  } else {
    remember { mutableFloatStateOf(45f) }
  }

  val mediumTransition = if (!pauseAnimations && !isLowTier) rememberInfiniteTransition(label = "veyraMediumWave") else null
  val sonarWave1State: State<Float> = if (mediumTransition != null) {
    mediumTransition.animateFloat(
      initialValue = 0.08f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "sonarWave1",
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }
  val pulseBreathState: State<Float> = if (mediumTransition != null) {
    mediumTransition.animateFloat(
      initialValue = 0.92f,
      targetValue = 1.10f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1450, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "pulseBreath",
    )
  } else {
    remember { mutableFloatStateOf(1.0f) }
  }

  val highTransition = if (!pauseAnimations && isHighTier) rememberInfiniteTransition(label = "veyraHighExtras") else null
  val outerOrbitAngleState: State<Float> = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 360f,
      targetValue = 0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 16000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "outerOrbitAngle",
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }
  val sonarWave2State: State<Float> = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 0.08f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
        initialStartOffset = StartOffset(1350),
      ),
      label = "sonarWave2",
    )
  } else {
    remember { mutableFloatStateOf(0f) }
  }
  val liveDotAlphaState: State<Float> = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 0.38f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "liveDotAlpha",
    )
  } else {
    remember { mutableFloatStateOf(1f) }
  }

  val plottedMissions = remember(missions) { missions.take(10) }
  val plottedPlayers = remember(nearbyPlayers) { nearbyPlayers.take(8) }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Contenedor exterior adaptable para animar suavemente ancho de círculo (50/75) -> panorama amplio (100)
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
      contentAlignment = Alignment.Center,
    ) {
      val maxContainerWidthDp = maxWidth
      val targetWidthDp = when (radarScale) {
        RadarScaleLevel.SCALE_50 -> 212.dp.coerceAtMost(maxContainerWidthDp)
        RadarScaleLevel.SCALE_75 -> 284.dp.coerceAtMost(maxContainerWidthDp)
        RadarScaleLevel.SCALE_100 -> maxContainerWidthDp
      }
      val animatedWidthDp by animateDpAsState(
        targetValue = targetWidthDp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "radarWidthAnim",
      )

      val radarShape = RoundedCornerShape(animatedCornerDp)

      BoxWithConstraints(
        modifier = Modifier
          .width(animatedWidthDp)
          .height(animatedHeightDp)
          .testTag("round_radar_map"),
        contentAlignment = Alignment.Center,
      ) {
        val density = LocalDensity.current
        val minDimPx = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) }
        val radarRadiusPx = minDimPx / 2f

        Surface(
          shape = radarShape,
          tonalElevation = if (radarScale == RadarScaleLevel.SCALE_100) 10.dp else 8.dp,
          shadowElevation = if (radarScale == RadarScaleLevel.SCALE_100) 14.dp else 12.dp,
          color = surfaceContainer,
          modifier = Modifier
            .fillMaxSize()
            .border(
              width = if (radarScale == RadarScaleLevel.SCALE_100) 2.2.dp else 2.5.dp,
              brush = Brush.sweepGradient(
                listOf(
                  borderColor,
                  tertiaryColor.copy(alpha = 0.65f),
                  secondaryColor.copy(alpha = 0.45f),
                  borderColor,
                ),
              ),
              shape = radarShape,
            ),
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .clip(radarShape)
              .pointerInput(Unit) {
                var dragDx = 0f
                var dragDy = 0f
                var handledDownward = false
                detectDragGestures(
                  onDragStart = {
                    dragDx = 0f
                    dragDy = 0f
                    handledDownward = false
                  },
                  onDragEnd = {
                    if (!handledDownward && abs(dragDx) >= 56f && currentScaleState != RadarScaleLevel.SCALE_100) {
                      val next = if (currentScaleState == RadarScaleLevel.SCALE_50) {
                        RadarScaleLevel.SCALE_75
                      } else {
                        RadarScaleLevel.SCALE_50
                      }
                      currentOnScaleChange(next)
                    }
                    isDragging50To75 = false
                  },
                  onDragCancel = {
                    isDragging50To75 = false
                  },
                  onDrag = { change, amount ->
                    change.consume()
                    dragDx += amount.x
                    dragDy += amount.y
                    if (abs(dragDy) > abs(dragDx) * 1.15f) {
                      if (dragDy >= 110f && !handledDownward && currentScaleState != RadarScaleLevel.SCALE_100) {
                        handledDownward = true
                        isDragging50To75 = false
                        currentOnScaleChange(RadarScaleLevel.SCALE_100)
                      } else if (dragDy <= -85f && !handledDownward && currentScaleState == RadarScaleLevel.SCALE_100) {
                        handledDownward = true
                        currentOnScaleChange(RadarScaleLevel.SCALE_75)
                      }
                    } else if (abs(dragDx) >= 16f && currentScaleState != RadarScaleLevel.SCALE_100) {
                      isDragging50To75 = true
                    }
                  },
                )
              }
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { showExpandHint = !showExpandHint },
              ),
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
                  if (pauseAnimations) return@AndroidView
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
                      map.controller.animateTo(GeoPoint(userLat, userLon), targetZoom, 380L)
                    }
                  }
                },
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer { alpha = animatedOsmAlpha },
              )
            }

            // Capa estética del radar: velo progresivo sobre OSM + anillos + barrido
            Canvas(modifier = Modifier.fillMaxSize()) {
              val center = Offset(size.width / 2f, size.height / 2f)
              val maxR = min(size.width, size.height) / 2f
              val sweepAngle = sweepAngleState.value
              val sonarWave1 = sonarWave1State.value
              val sonarWave2 = sonarWave2State.value
              val pulseBreath = pulseBreathState.value
              val outerOrbitAngle = outerOrbitAngleState.value

              // Velo radial progresivo: en 50 encierra el radar; en 75 y 100 deja ver más OpenStreetMap
              drawRect(
                brush = Brush.radialGradient(
                  colors = listOf(
                    surfaceContainer.copy(alpha = animatedVeilInnerAlpha),
                    primaryColor.copy(alpha = if (radarStyle == "neon") 0.14f else 0.08f),
                    surfaceContainer.copy(alpha = animatedVeilOuterAlpha),
                  ),
                  center = center,
                  radius = maxR * 1.15f,
                ),
              )

              // Counter-rotating outer orbital ring (High tier only)
              if (isHighTier) {
                rotate(degrees = outerOrbitAngle, pivot = center) {
                  drawCircle(
                    color = tertiaryColor.copy(alpha = if (radarStyle == "neon") 0.48f else 0.28f),
                    radius = maxR - 6f,
                    center = center,
                    style = Stroke(
                      width = 2.5f,
                      cap = StrokeCap.Round,
                      pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f, 6f, 14f), 0f),
                    ),
                  )
                }
              }

              // Concentric range rings
              val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
              val ringCount = 3
              for (i in 1..ringCount) {
                val r = maxR * (i / 3.25f)
                drawCircle(
                  color = primaryColor.copy(alpha = if (radarStyle == "neon") 0.36f else 0.24f),
                  radius = r,
                  center = center,
                  style = Stroke(
                    width = if (i == ringCount) 2f else 1.4f,
                    pathEffect = if (radarStyle == "classic" && i < ringCount) dashEffect else null,
                  ),
                )
              }

              // Crosshair lines
              drawLine(
                color = outlineVariant.copy(alpha = 0.42f),
                start = Offset(center.x, center.y - maxR + 14f),
                end = Offset(center.x, center.y + maxR - 14f),
                strokeWidth = 1.2f,
              )
              drawLine(
                color = outlineVariant.copy(alpha = 0.42f),
                start = Offset(center.x - maxR + 14f, center.y),
                end = Offset(center.x + maxR - 14f, center.y),
                strokeWidth = 1.2f,
              )

              // Compass ticks
              if (radarStyle == "compass" || radarStyle == "neon") {
                val step = if (radarStyle == "compass") 15 else 30
                for (deg in 0 until 360 step step) {
                  val rad = Math.toRadians(deg.toDouble())
                  val outer = maxR - 10f
                  val inner = if (deg % 45 == 0) maxR - 22f else maxR - 15f
                  drawLine(
                    color = primaryColor.copy(alpha = if (deg % 45 == 0) 0.72f else 0.32f),
                    start = Offset(
                      center.x + (inner * cos(rad)).toFloat(),
                      center.y + (inner * sin(rad)).toFloat(),
                    ),
                    end = Offset(
                      center.x + (outer * cos(rad)).toFloat(),
                      center.y + (outer * sin(rad)).toFloat(),
                    ),
                    strokeWidth = if (deg % 45 == 0) 2.2f else 1.2f,
                  )
                }
              }

              if (isBroadcasting) {
                if (!isLowTier && sonarWave1 > 0f) {
                  val wave1R = maxR * sonarWave1
                  val wave1Alpha = (1f - sonarWave1).coerceIn(0f, 1f) * 0.46f
                  drawCircle(
                    color = primaryColor.copy(alpha = wave1Alpha),
                    radius = wave1R,
                    center = center,
                    style = Stroke(width = 3.0f),
                  )
                }
                if (isHighTier && sonarWave2 > 0f) {
                  val wave2R = maxR * sonarWave2
                  val wave2Alpha = (1f - sonarWave2).coerceIn(0f, 1f) * 0.36f
                  drawCircle(
                    color = tertiaryColor.copy(alpha = wave2Alpha),
                    radius = wave2R,
                    center = center,
                    style = Stroke(width = 2.2f),
                  )
                }

                rotate(degrees = sweepAngle, pivot = center) {
                  if (!isLowTier) {
                    drawArc(
                      brush = Brush.sweepGradient(
                        0.0f to primaryColor.copy(alpha = if (radarStyle == "neon") 0.42f else 0.24f),
                        0.18f to primaryColor.copy(alpha = 0.08f),
                        0.24f to Color.Transparent,
                        1.0f to Color.Transparent,
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

              // Center beacon aura
              drawCircle(
                color = primaryColor.copy(alpha = 0.22f),
                radius = 27f * pulseBreath,
                center = center,
              )
            }

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

            // Mission pins plotted on the radar
            plottedMissions.forEach { item ->
              androidx.compose.runtime.key(item.mission.id) {
                val (dx, dy) = remember(item.distanceMeters, item.bearingDegrees, searchRadiusMeters, radarRadiusPx) {
                  val normalizedDist = (item.distanceMeters / searchRadiusMeters.toFloat()).coerceIn(0.18f, 0.84f)
                  val angleRad = Math.toRadians((item.bearingDegrees - 90f).toDouble())
                  val rPx = (radarRadiusPx * 0.80f) * normalizedDist
                  (rPx * cos(angleRad)).roundToInt() to (rPx * sin(angleRad)).roundToInt()
                }

                val isAccepted = item.mission.id == activeAcceptedMissionId
                val isSwept by remember(item.bearingDegrees, isHighTier, isBroadcasting) {
                  derivedStateOf {
                    isHighTier && isBroadcasting && isAngleNearSweep(item.bearingDegrees, sweepAngleState.value)
                  }
                }
                val pinScaleValue = if (isHighTier) {
                  val animScale by animateFloatAsState(
                    targetValue = when {
                      isAccepted -> 1.22f
                      isSwept && !item.isCompletedByMe -> 1.22f
                      !item.isCompletedByMe -> 1.0f
                      else -> 0.88f
                    },
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
                    label = "missionPinScale",
                  )
                  animScale
                } else {
                  if (isAccepted) 1.12f else if (item.isCompletedByMe) 0.90f else 1.0f
                }

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

                Box(
                  modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx, dy) }
                    .size(48.dp)
                    .graphicsLayer {
                      scaleX = pinScaleValue
                      scaleY = pinScaleValue
                    }
                    .clickable {
                      HapticFeedbackHelper.performRadarPinTap(context)
                      onMissionClick(currentItem)
                    }
                    .testTag("radar_mission_pin_${item.mission.id}")
                    .semantics { contentDescription = "Misión ${item.mission.title}" },
                  contentAlignment = Alignment.Center,
                ) {
                  if (!item.isCompletedByMe && (isSwept || item.isWithinProximity)) {
                    Box(
                      modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(pinColor.copy(alpha = if (item.isWithinProximity) 0.34f else 0.24f)),
                    )
                  }
                  Box(contentAlignment = Alignment.TopEnd) {
                    Surface(
                      shape = CircleShape,
                      color = pinColor,
                      shadowElevation = 4.dp,
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
            plottedPlayers.forEachIndexed { idx, playerItem ->
              androidx.compose.runtime.key(playerItem.profile.userId) {
                val (dx, dy, bearing) = remember(playerItem.distanceMeters, playerItem.bearingDegrees, idx, searchRadiusMeters, radarRadiusPx) {
                  val normalizedDist = if (playerItem.distanceMeters > 0f) {
                    (playerItem.distanceMeters / searchRadiusMeters.toFloat()).coerceIn(0.20f, 0.84f)
                  } else {
                    0.38f + (idx * 0.08f).coerceAtMost(0.42f)
                  }
                  val b = if (playerItem.distanceMeters > 0f) {
                    playerItem.bearingDegrees
                  } else {
                    (idx * 55f) + 25f
                  }
                  val angleRad = Math.toRadians((b - 90f).toDouble())
                  val rPx = (radarRadiusPx * 0.80f) * normalizedDist
                  Triple((rPx * cos(angleRad)).roundToInt(), (rPx * sin(angleRad)).roundToInt(), b)
                }

                val isSwept by remember(bearing, isHighTier, isBroadcasting) {
                  derivedStateOf {
                    isHighTier && isBroadcasting && isAngleNearSweep(bearing, sweepAngleState.value)
                  }
                }
                val dotScaleValue = if (isHighTier) {
                  val animScale by animateFloatAsState(
                    targetValue = if (isSwept || playerItem.profile.seekingHelp) 1.15f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.58f, stiffness = 400f),
                    label = "playerDotScale",
                  )
                  animScale
                } else {
                  1.0f
                }

                val playerDotColor = remember(playerItem.profile.avatarColorHex, primaryColor) {
                  runCatching {
                    Color(android.graphics.Color.parseColor(playerItem.profile.avatarColorHex))
                  }.getOrElse { primaryColor }
                }
                val currentPlayerItem by rememberUpdatedState(playerItem)

                Box(
                  modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(dx, dy) }
                    .size(48.dp)
                    .graphicsLayer {
                      scaleX = dotScaleValue
                      scaleY = dotScaleValue
                    }
                    .clickable {
                      HapticFeedbackHelper.performRadarPinTap(context)
                      onPlayerClick(currentPlayerItem)
                    }
                    .testTag("radar_player_dot_${playerItem.profile.userId}"),
                  contentAlignment = Alignment.Center,
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                      if (playerItem.profile.seekingHelp || isSwept) {
                        Box(
                          modifier = Modifier
                            .size(34.dp)
                            .graphicsLayer {
                              val b = pulseBreathState.value
                              scaleX = b
                              scaleY = b
                            }
                            .clip(CircleShape)
                            .background(
                              (if (playerItem.profile.seekingHelp) tertiaryColor else playerDotColor)
                                .copy(alpha = 0.28f),
                            ),
                        )
                      }
                      Surface(
                        shape = CircleShape,
                        color = playerDotColor,
                        shadowElevation = 4.dp,
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
              shadowElevation = 6.dp,
              modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp)
                .graphicsLayer {
                  val s = if (!isLowTier && isBroadcasting) 0.96f + (pulseBreathState.value - 0.92f) * 0.28f else 1f
                  scaleX = s
                  scaleY = s
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
        }
      }

        // Barra flotante al tocar el radar: "Expandir mapa" + segundo botón de Lupa (50/75/100) totalmente separados
        // Ubicada en el contenedor exterior para no recortarse cuando el radar está en vista 50 (212.dp)
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
            enter = fadeIn(),
            exit = fadeOut(),
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
              .graphicsLayer { alpha = lupaButtonAlpha }
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
              .graphicsLayer {
                alpha = if (isBroadcasting) liveDotAlphaState.value else 1f
              }
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
      shadowElevation = 8.dp,
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
    shadowElevation = 8.dp,
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

private fun isAngleNearSweep(bearingDeg: Float, sweepDeg: Float): Boolean {
  val canvasBearing = (bearingDeg - 90f + 360f) % 360f
  val diff = abs(((sweepDeg - canvasBearing + 540f) % 360f) - 180f)
  return diff < 34f
}
