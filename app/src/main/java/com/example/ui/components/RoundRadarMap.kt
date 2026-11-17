package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyPlayerItem
import com.example.data.model.PlayerProfile
import com.example.util.GeoUtils
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
  onPlayerClick: (NearbyPlayerItem) -> Unit,
  onMissionClick: (MissionWithDistance) -> Unit,
  onCycleRadius: () -> Unit,
  onExpandMap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val surfaceContainer = MaterialTheme.colorScheme.surfaceContainerHigh
  val outlineVariant = MaterialTheme.colorScheme.outlineVariant
  val onSurface = MaterialTheme.colorScheme.onSurface

  val isLowTier = animationTier == "low"
  val isHighTier = animationTier != "low" && animationTier != "medium"

  var showExpandHint by remember { mutableStateOf(false) }

  // Essential animation: radar sweep always runs when broadcasting (even on low-end tier).
  val essentialTransition = rememberInfiniteTransition(label = "veyraEssentialSweep")
  val sweepAngle by essentialTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = if (isLowTier) 3400 else 2800, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "sweepAngle",
  )

  // Medium + High tier lightweight animations
  val mediumTransition = if (!isLowTier) rememberInfiniteTransition(label = "veyraMediumWave") else null
  val sonarWave1 = if (mediumTransition != null) {
    mediumTransition.animateFloat(
      initialValue = 0.08f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "sonarWave1",
    ).value
  } else {
    0f
  }
  val pulseBreath = if (mediumTransition != null) {
    mediumTransition.animateFloat(
      initialValue = 0.92f,
      targetValue = 1.10f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1450, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "pulseBreath",
    ).value
  } else {
    1.0f
  }

  // High tier extra fluid animations
  val highTransition = if (isHighTier) rememberInfiniteTransition(label = "veyraHighExtras") else null
  val outerOrbitAngle = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 360f,
      targetValue = 0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 16000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "outerOrbitAngle",
    ).value
  } else {
    0f
  }
  val sonarWave2 = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 0.08f,
      targetValue = 1.0f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Restart,
        initialStartOffset = StartOffset(1350),
      ),
      label = "sonarWave2",
    ).value
  } else {
    0f
  }
  val liveDotAlpha = if (highTransition != null) {
    highTransition.animateFloat(
      initialValue = 0.38f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "liveDotAlpha",
    ).value
  } else {
    1f
  }

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Top-center round animated radar map
    BoxWithConstraints(
      modifier = Modifier
        .size(296.dp)
        .testTag("round_radar_map"),
      contentAlignment = Alignment.Center,
    ) {
      val density = LocalDensity.current
      val radarDiameterPx = with(density) { maxWidth.toPx() }
      val radarRadiusPx = radarDiameterPx / 2f

      Surface(
        shape = CircleShape,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        color = surfaceContainer,
        modifier = Modifier
          .fillMaxSize()
          .border(
            width = 2.5.dp,
            brush = Brush.sweepGradient(
              listOf(
                primaryColor.copy(alpha = 0.90f),
                tertiaryColor.copy(alpha = 0.65f),
                secondaryColor.copy(alpha = 0.45f),
                primaryColor.copy(alpha = 0.90f),
              )
            ),
            shape = CircleShape,
          ),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              onClick = { showExpandHint = !showExpandHint },
            ),
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f

            // Background depth gradient
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  primaryColor.copy(alpha = if (radarStyle == "neon") 0.24f else 0.13f),
                  secondaryColor.copy(alpha = 0.08f),
                  surfaceContainer,
                ),
                center = center,
                radius = maxR,
              ),
              radius = maxR,
              center = center,
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
                color = primaryColor.copy(alpha = if (radarStyle == "neon") 0.36f else 0.22f),
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
              start = Offset(center.x, 14f),
              end = Offset(center.x, size.height - 14f),
              strokeWidth = 1.2f,
            )
            drawLine(
              color = outlineVariant.copy(alpha = 0.42f),
              start = Offset(14f, center.y),
              end = Offset(size.width - 14f, center.y),
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
              // Sonar ripples (Medium: 1 wave, High: 2 waves, Low: none)
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

              // Essential rotating radar sweep (kept across all 3 tiers)
              rotate(degrees = sweepAngle, pivot = center) {
                if (!isLowTier) {
                  drawArc(
                    brush = Brush.sweepGradient(
                      0.0f to primaryColor.copy(alpha = if (radarStyle == "neon") 0.46f else 0.28f),
                      0.18f to primaryColor.copy(alpha = 0.08f),
                      0.24f to Color.Transparent,
                      1.0f to Color.Transparent,
                      center = center,
                    ),
                    startAngle = -80f,
                    sweepAngle = 82f,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = size,
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
            color = onSurface.copy(alpha = 0.6f),
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 8.dp),
          )
          Text(
            text = "O",
            style = MaterialTheme.typography.labelSmall,
            color = onSurface.copy(alpha = 0.6f),
            modifier = Modifier
              .align(Alignment.CenterStart)
              .padding(start = 10.dp),
          )
          Text(
            text = "E",
            style = MaterialTheme.typography.labelSmall,
            color = onSurface.copy(alpha = 0.6f),
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .padding(end = 10.dp),
          )

          // Mission pins plotted on the radar (tapping opens mission info + proximity check)
          missions.take(8).forEach { item ->
            val normalizedDist = (item.distanceMeters / searchRadiusMeters.toFloat()).coerceIn(0.22f, 0.82f)
            val angleRad = Math.toRadians((item.bearingDegrees - 90f).toDouble())
            val rPx = (radarRadiusPx * 0.80f) * normalizedDist
            val dx = (rPx * cos(angleRad)).roundToInt()
            val dy = (rPx * sin(angleRad)).roundToInt()

            val isSwept = isHighTier && isBroadcasting && isAngleNearSweep(item.bearingDegrees, sweepAngle)
            val pinScaleValue = if (isHighTier) {
              val animScale by animateFloatAsState(
                targetValue = if (isSwept && !item.isCompletedByMe) 1.24f else if (!item.isCompletedByMe) 1.0f else 0.88f,
                animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
                label = "missionPinScale",
              )
              animScale
            } else {
              if (item.isCompletedByMe) 0.90f else 1.0f
            }

            val pinColor = when {
              item.isCompletedByMe -> secondaryColor.copy(alpha = 0.60f)
              item.mission.category == "eco" -> Color(0xFF1B8E6E)
              item.mission.category == "social" -> tertiaryColor
              item.mission.category == "fitness" -> Color(0xFFE67E22)
              item.mission.category == "creative" -> Color(0xFF8E44AD)
              item.mission.category == "challenge" -> Color(0xFFD84315)
              else -> primaryColor
            }
            val pinIcon = when (item.mission.category) {
              "eco" -> Icons.Rounded.LocalFlorist
              "social" -> Icons.Rounded.VolunteerActivism
              "challenge" -> Icons.Rounded.CameraAlt
              "fitness" -> Icons.AutoMirrored.Rounded.DirectionsRun
              "creative" -> Icons.Rounded.Brush
              else -> Icons.Rounded.Explore
            }

            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dx, dy) }
                .size(48.dp)
                .graphicsLayer {
                  scaleX = pinScaleValue
                  scaleY = pinScaleValue
                }
                .clickable { onMissionClick(item) }
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

          // Nearby online players plotted on the radar
          nearbyPlayers.take(8).forEachIndexed { idx, playerItem ->
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
            val rPx = (radarRadiusPx * 0.80f) * normalizedDist
            val dx = (rPx * cos(angleRad)).roundToInt()
            val dy = (rPx * sin(angleRad)).roundToInt()

            val isSwept = isHighTier && isBroadcasting && isAngleNearSweep(bearing, sweepAngle)
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

            val playerDotColor = runCatching {
              Color(android.graphics.Color.parseColor(playerItem.profile.avatarColorHex))
            }.getOrElse { primaryColor }

            Box(
              modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dx, dy) }
                .size(48.dp)
                .graphicsLayer {
                  scaleX = dotScaleValue
                  scaleY = dotScaleValue
                }
                .clickable { onPlayerClick(playerItem) }
                .testTag("radar_player_dot_${playerItem.profile.userId}"),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                  if (playerItem.profile.seekingHelp || isSwept) {
                    Box(
                      modifier = Modifier
                        .size((34f * pulseBreath).dp)
                        .clip(CircleShape)
                        .background(
                          (if (playerItem.profile.seekingHelp) tertiaryColor else playerDotColor)
                            .copy(alpha = 0.28f)
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

          // Center User Avatar Marker
          Surface(
            shape = CircleShape,
            color = primaryColor,
            shadowElevation = 6.dp,
            modifier = Modifier
              .align(Alignment.Center)
              .size(42.dp)
              .graphicsLayer {
                val s = if (!isLowTier && isBroadcasting) 0.96f + (pulseBreath - 0.92f) * 0.28f else 1f
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

          // Floating "Expandir mapa" action revealed when user taps the radar
          if (isLowTier) {
            if (showExpandHint) {
              ExpandMapActionPill(
                onClick = {
                  showExpandHint = false
                  onExpandMap()
                },
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .padding(bottom = 26.dp),
              )
            }
          } else {
            androidx.compose.animation.AnimatedVisibility(
              visible = showExpandHint,
              enter = fadeIn(),
              exit = fadeOut(),
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            ) {
              ExpandMapActionPill(
                onClick = {
                  showExpandHint = false
                  onExpandMap()
                },
              )
            }
          }
        }
      }

      // Dedicated quick expand button on top-right corner of the radar
      FilledTonalIconButton(
        onClick = {
          showExpandHint = false
          onExpandMap()
        },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(4.dp)
          .testTag("radar_expand_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.OpenInFull,
          contentDescription = "Expandir mapa del radar",
        )
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
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
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
        color = if (isBroadcasting) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceVariant
        },
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(
                if (isBroadcasting) {
                  primaryColor.copy(alpha = liveDotAlpha)
                } else {
                  MaterialTheme.colorScheme.outline
                }
              ),
          )
          Text(
            text = if (isBroadcasting) {
              "${nearbyPlayers.size} cercanos en vivo"
            } else {
              "Radar oculto"
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
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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

