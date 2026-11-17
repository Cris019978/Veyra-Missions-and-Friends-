package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyPlayerItem
import com.example.data.model.PlayerProfile
import com.example.data.remote.OsmSafePlaceFinder
import com.example.util.GeoUtils
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
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
  userLat: Double = myProfile?.latitude?.takeIf { it != 0.0 } ?: 19.4326,
  userLon: Double = myProfile?.longitude?.takeIf { it != 0.0 } ?: -99.1332,
  safePublicAnchorsCount: Int = 0,
  activeAcceptedMissionId: String? = null,
  onDismiss: () -> Unit,
  onMissionClick: (MissionWithDistance) -> Unit,
  onPlayerClick: (NearbyPlayerItem) -> Unit,
  onRespawnRandomMissions: () -> Unit = {},
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val isRobolectric = remember { Build.FINGERPRINT.contains("robolectric", ignoreCase = true) }
  val canUseLiveOsmMap = !isRobolectric
  var useOsmMap by remember(canUseLiveOsmMap) { mutableStateOf(canUseLiveOsmMap) }

  var panOffset by remember { mutableStateOf(Offset.Zero) }
  var zoomScale by remember { mutableFloatStateOf(1.15f) }

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val tertiaryColor = MaterialTheme.colorScheme.tertiary
  val surfaceBg = MaterialTheme.colorScheme.surfaceContainerLowest
  val gridColor = MaterialTheme.colorScheme.outlineVariant

  val activeMission = missions.firstOrNull { it.mission.id == activeAcceptedMissionId }
  val playerGeoPoint = remember(userLat, userLon) { GeoPoint(userLat, userLon) }

  val osmMapView = remember(canUseLiveOsmMap) {
    if (!canUseLiveOsmMap) {
      null
    } else {
      OsmSafePlaceFinder.configureOsmdroid(context)
      MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        isTilesScaledToDpi = true
        minZoomLevel = 4.0
        maxZoomLevel = 19.5
        controller.setZoom(15.4)
        controller.setCenter(GeoPoint(userLat, userLon))
      }
    }
  }

  DisposableEffect(lifecycleOwner, osmMapView) {
    val map = osmMapView
    if (map == null) {
      onDispose { }
    } else {
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME -> map.onResume()
          Lifecycle.Event.ON_PAUSE -> map.onPause()
          else -> Unit
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        map.onPause()
        map.onDetach()
      }
    }
  }

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
        if (useOsmMap && osmMapView != null) {
          AndroidView(
            factory = { osmMapView },
            update = { map ->
              map.overlays.clear()

              // Outer radar scan radius circle around user's real location
              val scanCircle = Polygon(map).apply {
                points = Polygon.pointsAsCircle(playerGeoPoint, searchRadiusMeters.toDouble())
                fillPaint.color = primaryColor.copy(alpha = 0.06f).toArgb()
                outlinePaint.color = primaryColor.copy(alpha = 0.45f).toArgb()
                outlinePaint.strokeWidth = 3f
                infoWindow = null
              }
              map.overlays.add(scanCircle)

              // Proximity circles + mission markers
              missions.forEach { item ->
                val mPoint = GeoPoint(item.mission.latitude, item.mission.longitude)
                val isAccepted = item.mission.id == activeAcceptedMissionId
                val accentColor = when {
                  isAccepted -> primaryColor
                  item.isCompletedByMe -> secondaryColor.copy(alpha = 0.78f)
                  else -> MissionIcons.accentColorForMission(item.mission, primaryColor, tertiaryColor)
                }

                val zoneColor = if (isAccepted || item.isWithinProximity) primaryColor else tertiaryColor
                val proximityZone = Polygon(map).apply {
                  points = Polygon.pointsAsCircle(mPoint, GeoUtils.PROXIMITY_RADIUS_METERS.toDouble())
                  fillPaint.color = zoneColor.copy(alpha = if (isAccepted) 0.18f else 0.10f).toArgb()
                  outlinePaint.color = zoneColor.copy(alpha = if (isAccepted) 0.80f else 0.48f).toArgb()
                  outlinePaint.strokeWidth = if (isAccepted) 3f else 2f
                  infoWindow = null
                }
                map.overlays.add(proximityZone)

                val marker = Marker(map).apply {
                  position = mPoint
                  title = item.mission.title
                  snippet = "${item.mission.placeLabel} • ${GeoUtils.formatDistance(item.distanceMeters)}"
                  setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                  icon = createMissionPinDrawable(
                    context = context,
                    fillColorArgb = accentColor.toArgb(),
                    isAccepted = isAccepted,
                    isWithinProximity = item.isWithinProximity,
                    isCompleted = item.isCompletedByMe,
                    isPuzzleTimed = item.mission.category == "puzzle" && item.mission.timeLimitSeconds > 0,
                  )
                  setOnMarkerClickListener { _, _ ->
                    onMissionClick(item)
                    true
                  }
                }
                map.overlays.add(marker)
              }

              // Route guide polyline to active accepted mission
              if (activeMission != null) {
                val routeLine = Polyline(map).apply {
                  setPoints(
                    listOf(
                      playerGeoPoint,
                      GeoPoint(activeMission.mission.latitude, activeMission.mission.longitude),
                    ),
                  )
                  outlinePaint.color = primaryColor.toArgb()
                  outlinePaint.strokeWidth = 8f
                  outlinePaint.strokeCap = Paint.Cap.ROUND
                  infoWindow = null
                }
                map.overlays.add(routeLine)
              }

              // Nearby players markers
              nearbyPlayers.forEach { playerItem ->
                if (playerItem.profile.latitude != 0.0 || playerItem.profile.longitude != 0.0) {
                  val pPoint = GeoPoint(playerItem.profile.latitude, playerItem.profile.longitude)
                  val pColor = runCatching {
                    android.graphics.Color.parseColor(playerItem.profile.avatarColorHex)
                  }.getOrElse { primaryColor.toArgb() }
                  val pMarker = Marker(map).apply {
                    position = pPoint
                    title = playerItem.profile.callsign
                    snippet = playerItem.profile.statusMessage
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = createPlayerPinDrawable(context, pColor)
                    setOnMarkerClickListener { _, _ ->
                      onPlayerClick(playerItem)
                      true
                    }
                  }
                  map.overlays.add(pMarker)
                }
              }

              // User live GPS marker drawn last so it always stays crisp on top
              val userMarker = Marker(map).apply {
                position = playerGeoPoint
                title = myProfile?.callsign ?: "Tú"
                snippet = if (hasLocationPermission) "Tu ubicación GPS actual" else "Ubicación de radar"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = createUserLocationPinDrawable(context, primaryColor.toArgb())
              }
              map.overlays.add(userMarker)
              map.invalidate()
            },
            modifier = Modifier
              .fillMaxSize()
              .testTag("osm_map_view"),
          )
        } else {
          // Interactive Vector Radar Map Viewport (toggleable layer / Robolectric fallback)
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

              val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
              listOf(0.35f, 0.7f, 1.0f).forEach { frac ->
                drawCircle(
                  color = primaryColor.copy(alpha = 0.28f),
                  radius = baseRadiusPx * frac,
                  center = mapCenter,
                  style = Stroke(width = 1.6f, pathEffect = if (frac < 1f) dash else null),
                )
              }

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

            // Plotted Missions on vector view
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

            // Nearby Online Players
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
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.VerifiedUser,
                  contentDescription = null,
                  tint = Color(0xFF1B8E6E),
                  modifier = Modifier.size(16.dp),
                )
                Text(
                  text = "OpenStreetMap (${missions.size} misiones)",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text = if (safePublicAnchorsCount > 0) {
                  "OpenStreetMap: $safePublicAnchorsCount zonas públicas seguras (sin lugares privados o peligrosos)"
                } else {
                  "Mapa público OSM con puntos aleatorios seguros"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            if (canUseLiveOsmMap) {
              IconButton(
                onClick = { useOsmMap = !useOsmMap },
              ) {
                Icon(
                  imageVector = Icons.Rounded.Layers,
                  contentDescription = if (useOsmMap) "Cambiar a vista radar" else "Ver OpenStreetMap",
                )
              }
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.testTag("close_expanded_map_button"),
            ) {
              Icon(Icons.Rounded.Close, contentDescription = "Cerrar mapa expandido")
            }
          }
        }

        // Bottom Controls: Zoom +/- and Re-center
        Row(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledIconButton(
            onClick = {
              if (useOsmMap && osmMapView != null) {
                osmMapView.controller.zoomOut()
              } else {
                zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.60f)
              }
            },
          ) {
            Icon(Icons.Rounded.Remove, contentDescription = "Alejar")
          }
          FilledIconButton(
            onClick = {
              if (useOsmMap && osmMapView != null) {
                osmMapView.controller.zoomIn()
              } else {
                zoomScale = (zoomScale + 0.25f).coerceAtMost(3.4f)
              }
            },
          ) {
            Icon(Icons.Rounded.Add, contentDescription = "Acercar")
          }
          FilledIconButton(
            onClick = {
              if (useOsmMap && osmMapView != null) {
                osmMapView.controller.animateTo(playerGeoPoint, 15.6, 450L)
              } else {
                panOffset = Offset.Zero
                zoomScale = 1.15f
              }
            },
          ) {
            Icon(Icons.Rounded.MyLocation, contentDescription = "Centrar en mi ubicación")
          }
        }

        // Bottom Left Actions: Shuffle Random Points + External Map Route for Active Mission
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          if (activeMission != null) {
            FilledTonalButton(
              onClick = {
                GeoUtils.openInExternalMap(
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
              Text("Ver ruta", style = MaterialTheme.typography.labelMedium)
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

private fun createUserLocationPinDrawable(context: Context, primaryColorArgb: Int): BitmapDrawable {
  val density = context.resources.displayMetrics.density
  val sizePx = (44f * density).roundToInt().coerceAtLeast(44)
  val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bitmap)
  val cx = sizePx / 2f
  val cy = sizePx / 2f

  val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = primaryColorArgb
    alpha = 72
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.48f, haloPaint)

  val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.30f, borderPaint)

  val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = primaryColorArgb
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.22f, corePaint)

  return BitmapDrawable(context.resources, bitmap)
}

private fun createMissionPinDrawable(
  context: Context,
  fillColorArgb: Int,
  isAccepted: Boolean,
  isWithinProximity: Boolean,
  isCompleted: Boolean,
  isPuzzleTimed: Boolean,
): BitmapDrawable {
  val density = context.resources.displayMetrics.density
  val sizeDp = if (isAccepted) 42f else 36f
  val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(36)
  val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bitmap)
  val cx = sizePx / 2f
  val cy = sizePx / 2f

  if (isAccepted) {
    val activeHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = fillColorArgb
      alpha = 88
      style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, sizePx * 0.48f, activeHalo)
  }

  val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.38f, borderPaint)

  val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = fillColorArgb
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.31f, fillPaint)

  val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.FILL
  }
  if (isPuzzleTimed) {
    // Small inner clock dot indicator
    canvas.drawCircle(cx, cy, sizePx * 0.11f, glyphPaint)
  } else if (isCompleted) {
    val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      strokeWidth = 2.4f * density
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx - sizePx * 0.11f, cy, cx - sizePx * 0.02f, cy + sizePx * 0.09f, checkPaint)
    canvas.drawLine(cx - sizePx * 0.02f, cy + sizePx * 0.09f, cx + sizePx * 0.12f, cy - sizePx * 0.08f, checkPaint)
  } else {
    canvas.drawCircle(cx, cy, sizePx * 0.09f, glyphPaint)
  }

  if (!isWithinProximity && !isCompleted) {
    val badgeCx = sizePx * 0.78f
    val badgeCy = sizePx * 0.22f
    val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      style = Paint.Style.FILL
    }
    val badgeDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.DKGRAY
      style = Paint.Style.FILL
    }
    canvas.drawCircle(badgeCx, badgeCy, sizePx * 0.13f, badgeBorder)
    canvas.drawCircle(badgeCx, badgeCy, sizePx * 0.08f, badgeDot)
  }

  return BitmapDrawable(context.resources, bitmap)
}

private fun createPlayerPinDrawable(context: Context, playerColorArgb: Int): BitmapDrawable {
  val density = context.resources.displayMetrics.density
  val sizePx = (32f * density).roundToInt().coerceAtLeast(32)
  val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bitmap)
  val cx = sizePx / 2f
  val cy = sizePx / 2f

  val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.42f, borderPaint)

  val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = playerColorArgb
    style = Paint.Style.FILL
  }
  canvas.drawCircle(cx, cy, sizePx * 0.34f, fillPaint)

  return BitmapDrawable(context.resources, bitmap)
}
