package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyPlayerItem
import com.example.data.model.PlayerProfile
import com.example.util.GeoUtils
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ExpandedRadarMapDialog(
  myProfile: PlayerProfile?,
  nearbyPlayers: List<NearbyPlayerItem>,
  missions: List<MissionWithDistance>,
  searchRadiusMeters: Long,
  hasLocationPermission: Boolean,
  activeAcceptedMissionId: String? = null,
  onDismiss: () -> Unit,
  onMissionClick: (MissionWithDistance) -> Unit,
  onPlayerClick: (NearbyPlayerItem) -> Unit,
  onRespawnRandomMissions: () -> Unit = {},
) {
  val context = LocalContext.current
  var panOffset by remember { mutableStateOf(Offset.Zero) }
  var zoomScale by remember { mutableFloatStateOf(1.15f) }

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val surfaceBg = MaterialTheme.colorScheme.surfaceContainerLowest
  val gridColor = MaterialTheme.colorScheme.outlineVariant

  val activeMission = missions.firstOrNull { it.mission.id == activeAcceptedMissionId }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = surfaceBg,
      modifier = Modifier
        .fillMaxSize()
        .testTag("expanded_radar_map_dialog"),
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .safeDrawingPadding(),
      ) {
        // Pannable & Zoomable Map Viewport
        BoxWithConstraints(
          modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
              detectTransformGestures { _, pan, zoom, _ ->
                zoomScale = (zoomScale * zoom).coerceIn(0.60f, 3.4f)
                panOffset = Offset(
                  x = (panOffset.x + pan.x).coerceIn(-1100f, 1100f),
                  y = (panOffset.y + pan.y).coerceIn(-1400f, 1400f),
                )
              }
            },
          contentAlignment = Alignment.Center,
        ) {
          val density = LocalDensity.current
          val baseRadiusPx = with(density) { (minOf(maxWidth, maxHeight).toPx() * 0.42f) } * zoomScale
          val effectiveSpanMeters = searchRadiusMeters.toFloat().coerceAtLeast(300f)
          val pxPerMeter = baseRadiusPx / effectiveSpanMeters
          val proximityCirclePx = (GeoUtils.PROXIMITY_RADIUS_METERS * pxPerMeter).coerceIn(28f, 160f)

          Canvas(modifier = Modifier.fillMaxSize()) {
            val mapCenter = Offset(size.width / 2f + panOffset.x, size.height / 2f + panOffset.y)

            // Google-Maps-inspired street blocks & avenues backdrop
            val stepPx = 88f * zoomScale.coerceIn(0.75f, 2.3f)
            val startX = (mapCenter.x % stepPx) - stepPx
            val startY = (mapCenter.y % stepPx) - stepPx
            var bx = startX
            var colIdx = 0
            while (bx < size.width + stepPx) {
              var by = startY
              var rowIdx = 0
              while (by < size.height + stepPx) {
                val blockHash = ((colIdx * 31 + rowIdx * 17) and 7)
                if (blockHash == 1 || blockHash == 5) {
                  // Subtle green park patch
                  drawRoundRect(
                    color = Color(0xFF1B8E6E).copy(alpha = 0.08f),
                    topLeft = Offset(bx + 6f, by + 6f),
                    size = Size(stepPx - 12f, stepPx - 12f),
                    cornerRadius = CornerRadius(14f, 14f),
                  )
                } else {
                  drawRoundRect(
                    color = gridColor.copy(alpha = 0.09f),
                    topLeft = Offset(bx + 5f, by + 5f),
                    size = Size(stepPx - 10f, stepPx - 10f),
                    cornerRadius = CornerRadius(10f, 10f),
                  )
                }
                by += stepPx
                rowIdx++
              }
              bx += stepPx
              colIdx++
            }

            // Road gridlines
            var x = startX
            while (x < size.width + stepPx) {
              drawLine(
                color = gridColor.copy(alpha = 0.34f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.2f,
              )
              x += stepPx
            }
            var y = startY
            while (y < size.height + stepPx) {
              drawLine(
                color = gridColor.copy(alpha = 0.34f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.2f,
              )
              y += stepPx
            }

            // Concentric distance rings around player's real GPS position
            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
            listOf(0.35f, 0.7f, 1.0f).forEach { frac ->
              drawCircle(
                color = primaryColor.copy(alpha = 0.28f),
                radius = baseRadiusPx * frac,
                center = mapCenter,
                style = Stroke(width = 1.6f, pathEffect = if (frac < 1f) dash else null),
              )
            }

            // Proximity unlock zone circles & active route guide line
            missions.forEach { item ->
              val angleRad = Math.toRadians((item.bearingDegrees - 90f).toDouble())
              val distPx = (item.distanceMeters * pxPerMeter).coerceAtLeast(24f)
              val mx = mapCenter.x + (distPx * cos(angleRad)).toFloat()
              val my = mapCenter.y + (distPx * sin(angleRad)).toFloat()
              val isAccepted = item.mission.id == activeAcceptedMissionId
              val zoneColor = if (isAccepted || item.isWithinProximity) primaryColor else tertiaryColor

              if (isAccepted) {
                drawLine(
                  color = primaryColor.copy(alpha = 0.85f),
                  start = mapCenter,
                  end = Offset(mx, my),
                  strokeWidth = 5f,
                  cap = StrokeCap.Round,
                  pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f),
                )
              }

              drawCircle(
                color = zoneColor.copy(alpha = if (isAccepted) 0.22f else 0.12f),
                radius = proximityCirclePx,
                center = Offset(mx, my),
              )
              drawCircle(
                color = zoneColor.copy(alpha = if (isAccepted) 0.85f else 0.45f),
                radius = proximityCirclePx,
                center = Offset(mx, my),
                style = Stroke(width = if (isAccepted) 2.4f else 1.5f, pathEffect = dash),
              )
            }
          }

          // Center Player GPS Dot
          val centerDx = panOffset.x.roundToInt()
          val centerDy = panOffset.y.roundToInt()
          Box(
            modifier = Modifier
              .align(Alignment.Center)
              .offset { IntOffset(centerDx, centerDy) },
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Surface(
                shape = CircleShape,
                color = primaryColor,
                shadowElevation = 6.dp,
                modifier = Modifier
                  .size(40.dp)
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
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
              ) {
                Text(
                  text = "Tú",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
          }

          // Plotted Missions on the pannable map with distinct custom icons
          missions.forEach { item ->
            val angleRad = Math.toRadians((item.bearingDegrees - 90f).toDouble())
            val distPx = (item.distanceMeters * pxPerMeter).coerceAtLeast(24f)
            val dx = (panOffset.x + distPx * cos(angleRad)).roundToInt()
            val dy = (panOffset.y + distPx * sin(angleRad)).roundToInt()
            val isAccepted = item.mission.id == activeAcceptedMissionId

            val pinColor = if (item.isCompletedByMe) {
              secondaryColor.copy(alpha = 0.72f)
            } else {
              MissionIcons.accentColorForMission(item.mission, primaryColor, tertiaryColor)
            }
            val pinIcon = MissionIcons.iconForMission(item.mission)

            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dx, dy) }
                .clickable { onMissionClick(item) }
                .semantics { contentDescription = "Ver misión ${item.mission.title}" },
            ) {
              Box(contentAlignment = Alignment.TopEnd) {
                Surface(
                  shape = CircleShape,
                  color = pinColor,
                  shadowElevation = if (isAccepted) 8.dp else 5.dp,
                  modifier = Modifier
                    .size(if (isAccepted) 40.dp else 34.dp)
                    .border(if (isAccepted) 3.dp else 2.dp, Color.White, CircleShape),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = pinIcon,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(if (isAccepted) 21.dp else 18.dp),
                    )
                  }
                }
                if (!item.isWithinProximity && !item.isCompletedByMe) {
                  Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(16.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Lock,
                      contentDescription = "Requiere proximidad",
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.padding(2.dp),
                    )
                  }
                }
              }
              Surface(
                color = if (isAccepted) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f)
                } else {
                  MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                },
                shape = RoundedCornerShape(8.dp),
              ) {
                Text(
                  text = "${item.mission.title.take(14)} • ${GeoUtils.formatDistance(item.distanceMeters)}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (isAccepted) FontWeight.Bold else FontWeight.Normal,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }

          // Nearby Online Players on the pannable map
          nearbyPlayers.forEachIndexed { idx, playerItem ->
            val bearing = if (playerItem.distanceMeters > 0f) playerItem.bearingDegrees else (idx * 55f) + 25f
            val distPx = if (playerItem.distanceMeters > 0f) {
              (playerItem.distanceMeters * pxPerMeter).coerceAtLeast(36f)
            } else {
              120f + (idx * 24f)
            }
            val angleRad = Math.toRadians((bearing - 90f).toDouble())
            val dx = (panOffset.x + distPx * cos(angleRad)).roundToInt()
            val dy = (panOffset.y + distPx * sin(angleRad)).roundToInt()

            val playerDotColor = runCatching {
              Color(android.graphics.Color.parseColor(playerItem.profile.avatarColorHex))
            }.getOrElse { primaryColor }

            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(dx, dy) }
                .clickable { onPlayerClick(playerItem) },
            ) {
              Surface(
                shape = CircleShape,
                color = playerDotColor,
                shadowElevation = 5.dp,
                modifier = Modifier
                  .size(32.dp)
                  .border(2.dp, Color.White, CircleShape),
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
                    modifier = Modifier.size(17.dp),
                  )
                }
              }
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(6.dp),
              ) {
                Text(
                  text = playerItem.profile.callsign.take(10),
                  style = MaterialTheme.typography.labelSmall,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
              }
            }
          }
        }

        // Top Floating Header Bar
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
          shape = MaterialTheme.shapes.large,
          tonalElevation = 6.dp,
          shadowElevation = 6.dp,
          modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .padding(14.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Mapa interactivo (${missions.size} misiones)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Puntos aleatorios • Toca una misión para aceptar, esperar, rechazar o abrir en Google Maps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_expanded_map_button"),
            ) {
              Icon(Icons.Rounded.Close, contentDescription = "Cerrar mapa expandido")
            }
          }
        }

        // Bottom Controls: Google Maps / Shuffle + Zoom +/- and Re-center
        Row(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledIconButton(
            onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.60f) },
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Alejar")
          }
          FilledIconButton(
            onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3.4f) },
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Acercar")
          }
          FilledIconButton(
            onClick = {
              panOffset = Offset.Zero
              zoomScale = 1.15f
            },
          ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = "Centrar en mi ubicación")
          }
        }

        // Bottom Left Actions: Shuffle Random Points + Google Maps Launch for Active Mission
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (activeMission != null) {
            FilledTonalButton(
              onClick = {
                GeoUtils.openInGoogleMaps(
                  context = context,
                  latitude = activeMission.mission.latitude,
                  longitude = activeMission.mission.longitude,
                  title = activeMission.mission.title,
                )
              },
              shape = CircleShape,
            ) {
              Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Google Maps", style = MaterialTheme.typography.labelMedium)
            }
          }
          FilledTonalButton(
            onClick = onRespawnRandomMissions,
            shape = CircleShape,
            modifier = Modifier.testTag("expanded_map_shuffle_button"),
          ) {
            Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Nuevos puntos", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
}
