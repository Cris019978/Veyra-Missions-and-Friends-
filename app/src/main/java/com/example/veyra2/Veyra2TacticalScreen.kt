package com.example.veyra2

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.hypot

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findHostActivity()
  else -> null
}

/**
 * Pantalla/Overlay integrada de "Mejora Veyra 2":
 * - Al entrar en modo Horizontal, fuerza orientación landscape conservando estado en el ViewModel.
 * - Arriba muestra exactamente el botón de salida `"Preparing an Air Strike"` para regresar al menú principal.
 * - Integra retícula central, joystick virtual, pan + pinch-to-zoom, dibujado diferido hasta abrir el mapa,
 *   reciclaje dinámico >= 2 km con libro mayor de recompensas inmutable, tasa adaptativa Hz y panel Debug discreto.
 */
@Composable
fun Veyra2TacticalScreen(
  viewModel: Veyra2TacticalViewModel,
  onExitToMainMenu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = remember(context) { context.findHostActivity() }
  val isRobolectric = remember { Build.FINGERPRINT.contains("robolectric", ignoreCase = true) }

  // Cambio a orientación horizontal al activar LANDSCAPE_AIMING, restaurando al salir
  DisposableEffect(activity, uiState.mapMode.isLandscapeAiming) {
    if (activity != null && !isRobolectric) {
      activity.requestedOrientation = if (uiState.mapMode.isLandscapeAiming) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
      } else {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
      }
    }
    onDispose { }
  }

  DisposableEffect(activity) {
    onDispose {
      if (activity != null && !isRobolectric) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        AdaptiveRefreshRateController.resetFrameRateHint(activity)
      }
    }
  }

  LaunchedEffect(activity, uiState.refreshTier) {
    AdaptiveRefreshRateController.applyFrameRateHint(activity, uiState.refreshTier)
  }

  BackHandler {
    onExitToMainMenu()
  }

  Veyra2TacticalScreenContent(
    uiState = uiState,
    onExitToMainMenu = onExitToMainMenu,
    onSelectMapMode = viewModel::setMapMode,
    onCyclePortraitScale = viewModel::cyclePortraitScale,
    onScaleDragStarted = viewModel::onScaleDragStarted,
    onScaleDragDelta = viewModel::onScaleDragDelta,
    onScaleDragEnded = viewModel::onScaleDragEnded,
    onGestureBoostChanged = viewModel::setGestureBoostActive,
    onPanMeters = viewModel::panCameraByMeters,
    onZoomMultiplier = viewModel::applyAimZoomMultiplier,
    onMarkTarget = viewModel::markTargetAtReticle,
    onAddRoutePoint = viewModel::addRouteWaypointAtReticle,
    onClearAimAndRoute = viewModel::clearAimedMarksAndRoute,
    onRecenterCamera = viewModel::recenterCameraOnPlayer,
    onInspectMission = viewModel::inspectMission,
    onSignalMission = viewModel::signalMission,
    onMovePlayer = viewModel::movePlayerByMeters,
    onSpawnDebugMission = { dist, touched, claimed, diff ->
      viewModel.spawnDebugMission(dist, touched, claimed, diff)
    },
    onManualRecyclePass = viewModel::runManualDistanceRecyclePass,
    onToggleDebugPanel = viewModel::toggleDebugPanel,
    onResetPrototype = viewModel::resetPrototypeSession,
    modifier = modifier,
  )
}

@Composable
fun Veyra2TacticalScreenContent(
  uiState: Veyra2TacticalUiState,
  onExitToMainMenu: () -> Unit,
  onSelectMapMode: (Veyra2MapMode) -> Unit,
  onCyclePortraitScale: () -> Unit,
  onScaleDragStarted: () -> Unit,
  onScaleDragDelta: (Float, Float) -> Unit,
  onScaleDragEnded: () -> Unit,
  onGestureBoostChanged: (Boolean) -> Unit,
  onPanMeters: (Float, Float) -> Unit,
  onZoomMultiplier: (Float) -> Unit,
  onMarkTarget: () -> Unit,
  onAddRoutePoint: () -> Unit,
  onClearAimAndRoute: () -> Unit,
  onRecenterCamera: () -> Unit,
  onInspectMission: (String) -> Unit,
  onSignalMission: (String) -> Unit,
  onMovePlayer: (Float, Float) -> Unit,
  onSpawnDebugMission: (Float, Boolean, Boolean, Veyra2MissionDifficulty) -> Unit,
  onManualRecyclePass: () -> Unit,
  onToggleDebugPanel: () -> Unit,
  onResetPrototype: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var inspectedInfoMissionId by remember { mutableStateOf<String?>(null) }
  val inspectedMission = inspectedInfoMissionId?.let { id ->
    uiState.missionsInMemory.firstOrNull { it.id == id }
  }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .testTag("veyra2_tactical_screen"),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // 1. Canvas Táctico principal (en Landscape ocupa toda la superficie con UI flotante encima)
      Veyra2TacticalCanvas(
        uiState = uiState,
        onScaleDragStarted = onScaleDragStarted,
        onScaleDragDelta = onScaleDragDelta,
        onScaleDragEnded = onScaleDragEnded,
        onGestureBoostChanged = onGestureBoostChanged,
        onPanMeters = onPanMeters,
        onZoomMultiplier = onZoomMultiplier,
        modifier = Modifier.fillMaxSize(),
      )

      // 2. Barra superior: Botón exacto "Preparing an Air Strike" + telemetría Hz/Escala + Debug
      TopTacticalBar(
        uiState = uiState,
        onExitToMainMenu = onExitToMainMenu,
        onToggleDebugPanel = onToggleDebugPanel,
        modifier = Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
      )

      // 3. Si el mapa está CERRADO: mostramos la información textual en memoria (0 pines en Canvas)
      if (!uiState.mapMode.isMapOpen) {
        ClosedMapTextualMissionsOverlay(
          uiState = uiState,
          onOpenScale = onSelectMapMode,
          onInspectMission = { id ->
            onInspectMission(id)
            inspectedInfoMissionId = id
          },
          onSignalMission = onSignalMission,
          modifier = Modifier
            .align(Alignment.Center)
            .padding(top = 64.dp, bottom = 78.dp, start = 16.dp, end = 16.dp),
        )
      }

      // 4. Controles de Puntería Horizontal (Joystick + Acciones de Mira + HUD bajo retícula)
      if (uiState.mapMode.isLandscapeAiming) {
        // Joystick abajo izquierda
        VirtualAimJoystick(
          onStickInput = { nx, ny ->
            val speedFactor = (6.8f / uiState.aimZoomFactor.coerceIn(0.45f, 3.5f)).coerceIn(2.2f, 14f)
            onPanMeters(nx * speedFactor, ny * speedFactor)
          },
          onStickActiveChanged = onGestureBoostChanged,
          modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 16.dp, bottom = 16.dp),
        )

        // Tarjeta de objetivo bajo retícula o telemetría de ruta abajo centro (solo señalar y ver info)
        ReticleTargetInfoStrip(
          uiState = uiState,
          onInspectMission = { id ->
            onInspectMission(id)
            inspectedInfoMissionId = id
          },
          onSignalMission = onSignalMission,
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 148.dp, vertical = 14.dp),
        )

        // Panel lateral derecho de acciones tácticas
        LandscapeAimActionsRail(
          uiState = uiState,
          onMarkTarget = onMarkTarget,
          onAddRoutePoint = onAddRoutePoint,
          onRecenter = onRecenterCamera,
          onClearAll = onClearAimAndRoute,
          modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(end = 12.dp),
        )
      } else {
        // En vistas 50 / 75 / 100 / Cerrado: barra inferior de escala + botón de alternar (minimizado durante arrastre 50<->75)
        PortraitModeBottomControls(
          uiState = uiState,
          onCycleScale = onCyclePortraitScale,
          onEnterHorizontal = { onSelectMapMode(Veyra2MapMode.LANDSCAPE_AIMING) },
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        )
      }

      // 4b. Tarjeta discreta de información de la misión seleccionada (solo lectura + señalar)
      if (inspectedMission != null) {
        val distMeters = hypot(
          inspectedMission.position.xMeters - uiState.playerPosition.xMeters,
          inspectedMission.position.yMeters - uiState.playerPosition.yMeters,
        ).toInt()
        MissionInfoInspectCard(
          mission = inspectedMission,
          distanceMeters = distMeters,
          onSignalMission = { onSignalMission(inspectedMission.id) },
          onClose = { inspectedInfoMissionId = null },
          modifier = Modifier
            .align(Alignment.TopStart)
            .padding(top = 64.dp, start = 14.dp, end = 14.dp)
            .widthIn(max = 360.dp),
        )
      }

      // 5. Panel Debug discreto en memoria (lateral/flotante sin estorbar cuando está colapsado)
      if (uiState.debugPanelExpanded) {
        Veyra2DebugOverlayPanel(
          uiState = uiState,
          onClose = onToggleDebugPanel,
          onSelectMapMode = onSelectMapMode,
          onMovePlayer = onMovePlayer,
          onSpawnDebugMission = onSpawnDebugMission,
          onManualRecyclePass = onManualRecyclePass,
          onResetPrototype = onResetPrototype,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 64.dp, end = 12.dp, bottom = 12.dp)
            .widthIn(max = 360.dp),
        )
      }
    }
  }
}

@Composable
private fun TopTacticalBar(
  uiState: Veyra2TacticalUiState,
  onExitToMainMenu: () -> Unit,
  onToggleDebugPanel: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
    modifier = modifier,
  ) {
    // Botón exacto solicitado para salir al menú principal
    Surface(
      onClick = onExitToMainMenu,
      shape = CircleShape,
      color = MaterialTheme.colorScheme.errorContainer,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("veyra2_exit_preparing_air_strike_button"),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
          contentDescription = "Salir al menú principal",
          tint = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.size(18.dp),
        )
        Text(
          text = "Preparing an Air Strike",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onErrorContainer,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    // Indicadores compactos: escala / pines dibujados / XP guardada / tasa adaptativa Hz / Debug
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Speed,
            contentDescription = "Tasa de refresco adaptativa",
            tint = when (uiState.refreshTier) {
              AdaptiveRefreshTier.GESTURE_BOOST_120HZ -> Color(0xFFFB923C)
              AdaptiveRefreshTier.ACTIVE_MOVEMENT_60HZ -> MaterialTheme.colorScheme.primary
              AdaptiveRefreshTier.IDLE_LOW_30HZ -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = "${uiState.refreshTier.shortLabel} • ${uiState.drawnMarkersCount}/${uiState.missionsInMemory.size} pines • ${uiState.rewardsLedger.totalXp} XP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("veyra2_top_telemetry_text"),
          )
        }
      }

      Surface(
        onClick = onToggleDebugPanel,
        shape = CircleShape,
        color = if (uiState.debugPanelExpanded) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("veyra2_debug_toggle_button"),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = "Abrir o cerrar panel Debug Veyra 2",
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "Debug",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun LandscapeAimActionsRail(
  uiState: Veyra2TacticalUiState,
  onMarkTarget: () -> Unit,
  onAddRoutePoint: () -> Unit,
  onRecenter: () -> Unit,
  onClearAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    modifier = modifier,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(10.dp),
    ) {
      FilledTonalButton(
        onClick = onMarkTarget,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("veyra2_mark_target_button"),
      ) {
        Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Marcar (${uiState.markedTargets.size})", style = MaterialTheme.typography.labelMedium)
      }

      FilledTonalButton(
        onClick = onAddRoutePoint,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("veyra2_add_route_point_button"),
      ) {
        Icon(Icons.Rounded.Route, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Ruta + (${uiState.routePoints.size})", style = MaterialTheme.typography.labelMedium)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
          onClick = onRecenter,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("veyra2_recenter_button"),
        ) {
          Icon(Icons.Rounded.MyLocation, contentDescription = "Centrar mira", modifier = Modifier.size(16.dp))
        }
        OutlinedButton(
          onClick = onClearAll,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("veyra2_clear_route_button"),
        ) {
          Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpiar ruta y marcas", modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}

@Composable
private fun ReticleTargetInfoStrip(
  uiState: Veyra2TacticalUiState,
  onInspectMission: (String) -> Unit,
  onSignalMission: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hovered = uiState.missionUnderReticle
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
    border = BorderStroke(
      1.dp,
      if (hovered != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    ),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      if (hovered != null) {
        val distMeters = hypot(
          hovered.position.xMeters - uiState.playerPosition.xMeters,
          hovered.position.yMeters - uiState.playerPosition.yMeters,
        ).toInt()
        val statusLabel = when {
          hovered.isClaimed -> "Completada"
          hovered.isPinnedTarget -> "Señalada"
          else -> "Visible"
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
          Text(
            text = "En mira: ${hovered.title}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "$distMeters m • +${hovered.xpReward} XP • ${hovered.difficulty.label} • $statusLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        OutlinedButton(
          onClick = { onInspectMission(hovered.id) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("veyra2_inspect_reticle_mission_button"),
        ) {
          Text("Ver info", style = MaterialTheme.typography.labelSmall)
        }
        Button(
          onClick = { onSignalMission(hovered.id) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("veyra2_signal_reticle_mission_button"),
        ) {
          Text(if (hovered.isPinnedTarget) "Señalada" else "Señalar", style = MaterialTheme.typography.labelSmall)
        }
      } else {
        val relX = (uiState.cameraCenter.xMeters - uiState.playerPosition.xMeters).toInt()
        val relY = (uiState.cameraCenter.yMeters - uiState.playerPosition.yMeters).toInt()
        Text(
          text = "Mira (${relX}m, ${relY}m) • Zoom ${String.format("%.2fx", uiState.aimZoomFactor)} • ${uiState.lastEventLog}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun MissionInfoInspectCard(
  mission: PhysicalMapMission,
  distanceMeters: Int,
  onSignalMission: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f)),
    border = BorderStroke(1.dp, mission.difficulty.markerColor.copy(alpha = 0.7f)),
    shape = RoundedCornerShape(20.dp),
    modifier = modifier.testTag("veyra2_mission_info_card"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = mission.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = "${mission.difficulty.label} • $distanceMeters m • +${mission.xpReward} XP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
          )
        }
        IconButton(
          onClick = onClose,
          modifier = Modifier.testTag("veyra2_close_mission_info_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Cerrar información de misión",
          )
        }
      }
      Text(
        text = mission.summary.ifBlank { "Misión táctica visible para explorar y señalar desde el mapa." },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text = when {
              mission.isClaimed -> "Estado: Completada"
              mission.isPinnedTarget -> "Estado: Señalada en radar"
              else -> "Estado: Solo visualización"
            },
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
          onClick = onSignalMission,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("veyra2_info_card_signal_button"),
        ) {
          Icon(Icons.Rounded.GpsFixed, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(Modifier.width(6.dp))
          Text(if (mission.isPinnedTarget) "Señalada" else "Señalar", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
private fun ClosedMapTextualMissionsOverlay(
  uiState: Veyra2TacticalUiState,
  onOpenScale: (Veyra2MapMode) -> Unit,
  onInspectMission: (String) -> Unit,
  onSignalMission: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    shape = RoundedCornerShape(22.dp),
    modifier = modifier
      .fillMaxWidth()
      .widthIn(max = 560.dp)
      .testTag("veyra2_closed_map_list"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text(
            text = "Mapa cerrado • Marcadores diferidos (${uiState.drawnMarkersCount} en Canvas)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
          )
        }
        FilledTonalButton(
          onClick = { onOpenScale(Veyra2MapMode.OPEN_75) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text("Abrir 75", style = MaterialTheme.typography.labelSmall)
        }
      }
      Text(
        text = "Las misiones se conservan visibles en memoria para señalar o ver su información, y solo se dibujan al abrir el mapa.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      LazyColumn(
        modifier = Modifier.height(210.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(uiState.missionsInMemory, key = { it.id }) { m ->
          val dist = hypot(
            m.position.xMeters - uiState.playerPosition.xMeters,
            m.position.yMeters - uiState.playerPosition.yMeters,
          ).toInt()
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                  text = "$dist m • +${m.xpReward} XP • ${if (m.isClaimed) "Completada" else if (m.isPinnedTarget) "Señalada" else "Visible"}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                  onClick = { onInspectMission(m.id) },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                  Text("Ver info", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                  onClick = { onSignalMission(m.id) },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                  Text(if (m.isPinnedTarget) "Señalada" else "Señalar", style = MaterialTheme.typography.labelSmall)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PortraitModeBottomControls(
  uiState: Veyra2TacticalUiState,
  onCycleScale: () -> Unit,
  onEnterHorizontal: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    modifier = modifier,
  ) {
    if (uiState.isToggleButtonVisible) {
      FilledTonalButton(
        onClick = onCycleScale,
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("veyra2_portrait_cycle_scale_button"),
      ) {
        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Alternar (${uiState.mapMode.label})")
      }
    }
    Button(
      onClick = onEnterHorizontal,
      modifier = Modifier
        .minimumInteractiveComponentSize()
        .testTag("veyra2_enter_horizontal_button"),
    ) {
      Icon(Icons.Rounded.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(6.dp))
      Text("Mira Horizontal")
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Veyra2DebugOverlayPanel(
  uiState: Veyra2TacticalUiState,
  onClose: () -> Unit,
  onSelectMapMode: (Veyra2MapMode) -> Unit,
  onMovePlayer: (Float, Float) -> Unit,
  onSpawnDebugMission: (Float, Boolean, Boolean, Veyra2MissionDifficulty) -> Unit,
  onManualRecyclePass: () -> Unit,
  onResetPrototype: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.96f)),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    shape = RoundedCornerShape(20.dp),
    modifier = modifier.testTag("veyra2_debug_panel"),
  ) {
    Column(
      modifier = Modifier
        .verticalScroll(rememberScrollState())
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text(
          text = "Panel Debug • Mejora Veyra 2",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
        )
        Icon(
          imageVector = Icons.Rounded.Close,
          contentDescription = "Cerrar panel Debug",
          modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClose)
            .padding(4.dp),
        )
      }

      // Contadores en vivo
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
          verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
          Text(
            text = "Memoria: ${uiState.missionsInMemory.size} • Dibujadas: ${uiState.drawnMarkersCount} • Caminado: ${uiState.totalWalkedMeters.toInt()} m",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = "Recicladas (>=2km): ${uiState.rewardsLedger.totalRecycledCount} (sin tocar ${uiState.rewardsLedger.recycledUntouchedCount} / tocadas ${uiState.rewardsLedger.recycledTouchedOrClaimedCount})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Recompensas guardadas: ${uiState.rewardsLedger.totalXp} XP • +${uiState.rewardsLedger.bonusLevels} Nv • ${uiState.rewardsLedger.unlockedTitles.size} títulos • ${uiState.rewardsLedger.claimedMissionIds.size} IDs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Refresco activo: ${uiState.refreshTier.shortLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }

      Text("Vistas / Modo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Veyra2MapMode.entries.forEach { mode ->
          val selected = uiState.mapMode == mode
          Surface(
            onClick = { onSelectMapMode(mode) },
            shape = CircleShape,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.testTag("veyra2_dbg_mode_${mode.name.lowercase()}"),
          ) {
            Text(
              text = mode.label,
              style = MaterialTheme.typography.labelSmall,
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }

      HorizontalDivider()
      Text("Simular caminata (genera y recicla >= 2 km)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalButton(
          onClick = { onMovePlayer(250f, 80f) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("veyra2_dbg_walk_250m"),
        ) {
          Icon(Icons.Rounded.DirectionsWalk, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("+260 m", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = { onMovePlayer(850f, -200f) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("veyra2_dbg_walk_850m"),
        ) {
          Text("+870 m", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = { onMovePlayer(2200f, 0f) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("veyra2_dbg_walk_2200m"),
        ) {
          Text("+2.2 km (Cruzar 2km)", style = MaterialTheme.typography.labelSmall)
        }
      }

      Text("Misiones de prueba (distancia / estado)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        OutlinedButton(
          onClick = { onSpawnDebugMission(135f, false, false, Veyra2MissionDifficulty.NORMAL) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Rounded.AddLocationAlt, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("Cerca 135m", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = { onSpawnDebugMission(1850f, true, false, Veyra2MissionDifficulty.TACTICAL) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text("Tocada 1.85km", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = { onSpawnDebugMission(280f, true, true, Veyra2MissionDifficulty.HIGH_DIFFICULTY) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("Reclamada +XP", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = { onSpawnDebugMission(2300f, false, false, Veyra2MissionDifficulty.TACTICAL) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
          Text("Crear >2km", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = onManualRecyclePass,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("veyra2_dbg_recycle_now"),
        ) {
          Text("Reciclar >=2km", style = MaterialTheme.typography.labelSmall)
        }
      }

      HorizontalDivider()
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
          onClick = { onResetPrototype(true) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.weight(1f),
        ) {
          Text("Reset mapa (XP intacta)", style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
          onClick = { onResetPrototype(false) },
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.weight(1f).testTag("veyra2_dbg_full_reset"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(Modifier.width(4.dp))
          Text("Reset total", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
