package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Mission
import com.example.data.model.MissionCategory
import com.example.data.model.MissionWithDistance
import com.example.data.model.NearbyPlayerItem
import com.example.ui.components.ConfettiCelebrationOverlay
import com.example.ui.components.ExpandedRadarMapDialog
import com.example.ui.components.MissionDetailSheet
import com.example.ui.components.MissionIcons
import com.example.ui.components.PhotoConfirmSheet
import com.example.ui.components.PlayerActionSheet
import com.example.ui.components.RoundRadarMap
import com.example.ui.components.TilePuzzleGameDialog
import com.example.ui.components.WireConnectGameDialog
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.GeoUtils
import com.example.util.HapticFeedbackHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarHomeScreen(
  uiState: RadarQuestUiState,
  onRequestLocationPermission: () -> Unit,
  onOpenNotifications: () -> Unit,
  onRefresh: () -> Unit,
  onToggleBroadcast: (Boolean) -> Unit,
  onToggleSeekingHelp: () -> Unit,
  onCycleRadius: () -> Unit,
  onHelpPlayer: (NearbyPlayerItem) -> Unit,
  onSendPulse: (NearbyPlayerItem) -> Unit,
  onCompeteDuel: (NearbyPlayerItem, Mission) -> Unit,
  onPlayCoop: (NearbyPlayerItem, Mission) -> Unit,
  onCreateMission: (title: String, description: String, category: String, xpReward: Long, missionType: String, iconKey: String) -> Unit,
  onCreateTimedMission: ((title: String, description: String, category: String, xpReward: Long, missionType: String, iconKey: String, timeLimitSeconds: Int) -> Unit)? = null,
  onConfirmMissionPhoto: (mission: Mission, caption: String, base64Thumb: String, isPublic: Boolean) -> Unit,
  onCompleteMiniGame: (mission: Mission, caption: String, isPublic: Boolean) -> Unit = { _, _, _ -> },
  onAcceptMission: (String) -> Unit = {},
  onWaitMission: (String) -> Unit = {},
  onRejectMission: (String) -> Unit = {},
  onCancelActiveMission: () -> Unit = {},
  onRespawnRandomMissions: () -> Unit = {},
  onDialogVisibilityChanged: (Boolean) -> Unit = {},
  useExternalConfettiHost: Boolean = false,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var selectedCategory by remember { mutableStateOf("all") }
  var selectedPlayer by remember { mutableStateOf<NearbyPlayerItem?>(null) }
  var selectedMission by remember { mutableStateOf<MissionWithDistance?>(null) }
  var showExpandedMap by remember { mutableStateOf(false) }
  var activeMissionPlayTarget by remember { mutableStateOf<Mission?>(null) }
  var showCreateMissionDialog by remember { mutableStateOf(false) }
  var localCelebrationTrigger by remember { mutableLongStateOf(0L) }

  val disableDecorAnimations = uiState.settings.animationTier == "low"

  val isModalOpen = selectedMission != null ||
    selectedPlayer != null ||
    activeMissionPlayTarget != null ||
    showCreateMissionDialog
  val pauseRadarAnimations = isModalOpen || showExpandedMap

  LaunchedEffect(isModalOpen) {
    onDialogVisibilityChanged(isModalOpen)
  }
  DisposableEffect(Unit) {
    onDispose { onDialogVisibilityChanged(false) }
  }

  val favoriteCategories = remember(uiState.settings.favoriteCategories) {
    uiState.settings.favoriteCategories.toSet()
  }

  val categoryCounts = remember(uiState.missions) {
    uiState.missions.groupingBy { it.mission.category }.eachCount()
  }

  val filteredMissions = remember(uiState.missions, selectedCategory, favoriteCategories) {
    when (selectedCategory) {
      "all" -> uiState.missions
      "for_you" -> uiState.missions.filter { it.mission.category in favoriteCategories }
        .ifEmpty { uiState.missions }
      "timed_1min" -> uiState.missions.filter { it.mission.timeLimitSeconds > 0 }
      "puzzle" -> uiState.missions.filter {
        it.mission.category == "puzzle" || it.mission.missionType in setOf("wires", "puzzle", "memory")
      }
      else -> uiState.missions.filter { it.mission.category == selectedCategory }
    }
  }

  val launchMissionFlow: (MissionWithDistance) -> Unit = { item ->
    if (item.mission.missionType in setOf("wires", "puzzle", "memory")) {
      // Puzzle missions solve inline right inside the mission's own menu at the demonstrated point
      if (item.isWithinProximity && uiState.hasLocationPermission) {
        onAcceptMission(item.mission.id)
      }
      selectedMission = item
    } else if (item.isWithinProximity && uiState.hasLocationPermission) {
      onAcceptMission(item.mission.id)
      activeMissionPlayTarget = item.mission
    } else {
      selectedMission = item
    }
  }

  PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier
      .fillMaxSize()
      .testTag("radar_pull_to_refresh"),
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .widthIn(max = 680.dp)
          .fillMaxSize(),
        contentPadding = PaddingValues(
          bottom = if (uiState.settings.gestureNavEnabled) 28.dp else 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // 1. Player HUD Header
        item(key = "hud_header") {
          val level = uiState.myProfile?.level ?: 1L
          val xp = uiState.myProfile?.xp ?: 0L
          val xpInLevel = (xp % 250L) / 250f
          val seekingHelp = uiState.myProfile?.seekingHelp == true

          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = CircleShape,
                ) {
                  Text(
                    text = "NV $level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
                Text(
                  text = uiState.myProfile?.callsign ?: "Explorador",
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                LinearProgressIndicator(
                  progress = { xpInLevel },
                  modifier = Modifier
                    .width(120.dp)
                    .height(7.dp)
                    .clip(CircleShape),
                )
                Text(
                  text = "$xp XP",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }

            Row(
              horizontalArrangement = Arrangement.spacedBy(2.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              FilledTonalButton(
                onClick = onToggleSeekingHelp,
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.testTag("toggle_seek_help_button"),
              ) {
                Icon(
                  imageVector = Icons.Rounded.VolunteerActivism,
                  contentDescription = "Pedir ayuda",
                  tint = if (seekingHelp) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (seekingHelp) "Ayuda ON" else "Ayuda",
                  style = MaterialTheme.typography.labelSmall,
                )
              }

              IconButton(
                onClick = onOpenNotifications,
                modifier = Modifier.testTag("open_notifications_button"),
              ) {
                BadgedBox(
                  badge = {
                    if (uiState.unreadNotificationsCount > 0) {
                      Badge {
                        Text(uiState.unreadNotificationsCount.coerceAtMost(99).toString())
                      }
                    }
                  },
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Notificaciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }

              IconButton(
                onClick = { onToggleBroadcast(!uiState.isBroadcastingRadar) },
                modifier = Modifier.testTag("toggle_radar_broadcast_button"),
              ) {
                Icon(
                  imageVector = if (uiState.isBroadcastingRadar) Icons.Rounded.Sensors else Icons.Rounded.SensorsOff,
                  contentDescription = "Alternar señal de radar",
                  tint = if (uiState.isBroadcastingRadar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
              }
            }
          }
        }

        // 1b. Subtle Local Mode Indicator when Firebase / Google Services are unavailable
        if (uiState.isLocalOfflineMode) {
          item(key = "local_offline_banner") {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = CircleShape,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("local_offline_mode_banner"),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.CloudOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = "Modo exploración local (sin nube) • Mapa OSM y misiones activos",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }

        // 2. Location Permission Prompt if not yet granted
        if (!uiState.hasLocationPermission) {
          item(key = "location_permission_card") {
            Card(
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              ),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.LocationOn,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(28.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = stringResource(id = R.string.grant_location_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                  Text(
                    text = stringResource(id = R.string.grant_location_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                  )
                }
                Button(
                  onClick = onRequestLocationPermission,
                  modifier = Modifier.testTag("grant_location_button"),
                ) {
                  Text("Activar")
                }
              }
            }
          }
        }

        // 2b. Subtle Active Mission Close-Proximity Guidance Banner
        uiState.activeAcceptedMission?.let { activeItem ->
          item(key = "active_guidance_card") {
            val isVeryClose = activeItem.distanceMeters <= GeoUtils.CLOSE_GUIDANCE_RADIUS_METERS
            val arrow = GeoUtils.compassArrowForBearing(activeItem.bearingDegrees)
            val dir = GeoUtils.cardinalDirectionEs(activeItem.bearingDegrees)
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (isVeryClose) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.secondaryContainer
                },
              ),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("active_mission_guidance_card"),
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.NearMe,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                      Text(
                        text = if (isVeryClose) {
                          "¡MUY CERCA! ($arrow $dir • ${GeoUtils.formatDistance(activeItem.distanceMeters)})"
                        } else {
                          "Misión activa • $arrow $dir (${GeoUtils.formatDistance(activeItem.distanceMeters)})"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = activeItem.mission.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                  }
                  IconButton(
                    onClick = onCancelActiveMission,
                    modifier = Modifier.testTag("stop_active_guidance_button"),
                  ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Pausar guía")
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  OutlinedButton(
                    onClick = {
                      GeoUtils.openInExternalMap(
                        context = context,
                        latitude = activeItem.mission.latitude,
                        longitude = activeItem.mission.longitude,
                        title = activeItem.mission.title,
                      )
                    },
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver ruta")
                  }
                  Button(
                    onClick = { launchMissionFlow(activeItem) },
                    enabled = activeItem.isWithinProximity && uiState.hasLocationPermission,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(
                      imageVector = MissionIcons.typeActionIcon(activeItem.mission.missionType),
                      contentDescription = null,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = if (activeItem.isWithinProximity) {
                        when (activeItem.mission.missionType) {
                          "wires" -> "Conectar"
                          "puzzle" -> "Puzzle"
                          "memory" -> "Secuencia"
                          else -> "Foto"
                        }
                      } else {
                        "Camina cerca"
                      },
                    )
                  }
                }
              }
            }
          }
        }

        // 3. Top-Center Animated Round Radar Map
        item(key = "round_radar_map") {
          RoundRadarMap(
            myProfile = uiState.myProfile,
            nearbyPlayers = uiState.nearbyPlayers,
            missions = uiState.missions,
            searchRadiusMeters = uiState.settings.searchRadiusMeters,
            radarStyle = uiState.settings.radarStyle,
            animationTier = uiState.settings.animationTier,
            isBroadcasting = uiState.isBroadcastingRadar,
            hasLocationPermission = uiState.hasLocationPermission,
            activeAcceptedMissionId = uiState.activeAcceptedMissionId,
            pauseAnimations = pauseRadarAnimations,
            onPlayerClick = { selectedPlayer = it },
            onMissionClick = { selectedMission = it },
            onCycleRadius = onCycleRadius,
            onExpandMap = { showExpandedMap = true },
            modifier = Modifier.padding(top = 2.dp),
          )
        }

        // 4. Nearby Players Strip
        item(key = "nearby_players_strip") {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = stringResource(id = R.string.nearby_players_title),
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "${uiState.nearbyPlayers.size} activos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }

            if (uiState.nearbyPlayers.isEmpty()) {
              Card(
                colors = CardDefaults.cardColors(
                  containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Radar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                  )
                  Text(
                    text = "Tu señal está en vivo. Cuando otros amigos abran Veyra dentro de ${
                      GeoUtils.formatDistance(uiState.settings.searchRadiusMeters.toFloat())
                    }, aparecerán aquí para ayudar, competir o jugar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                items(
                  items = uiState.nearbyPlayers,
                  key = { it.profile.userId },
                  contentType = { "nearby_player_card" },
                ) { item ->
                  NearbyPlayerCard(
                    item = item,
                    onClick = { selectedPlayer = item },
                    onQuickHelp = { onHelpPlayer(item) },
                  )
                }
              }
            }
          }
        }

        // 5. Bottom Section: Randomized 28+ Real-Life & Mini-Game Missions
        item(key = "missions_section_header") {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "${uiState.missions.size} misiones aleatorias",
                  style = MaterialTheme.typography.titleLarge,
                )
                Text(
                  text = "Foto, cables y rompecabezas • Acércate a ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(
                  onClick = onRespawnRandomMissions,
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                  modifier = Modifier.testTag("respawn_random_missions_button"),
                ) {
                  Icon(Icons.Rounded.Shuffle, contentDescription = "Reubicar aleatorias", modifier = Modifier.size(16.dp))
                }
                FilledTonalButton(
                  onClick = { showCreateMissionDialog = true },
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                  modifier = Modifier.testTag("open_create_mission_button"),
                ) {
                  Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(stringResource(id = R.string.create_mission), style = MaterialTheme.typography.labelMedium)
                }
              }
            }

            if (uiState.waitingMissionQueue.isNotEmpty()) {
              Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.testTag("waiting_queue_summary_pill"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(15.dp),
                  )
                  Text(
                    text = "${uiState.waitingMissionQueue.size} misión(es) en cola de espera — aparecerán al terminar otra",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                  )
                }
              }
            }
          }
        }

        // Interactive Category & Gameplay Type Pills Bar
        item(key = "mission_category_pills_bar") {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            item(key = "all") {
              MissionCategoryCard(
                key = "all",
                label = "Todas",
                count = uiState.missions.size,
                icon = Icons.Rounded.AutoAwesome,
                accentColor = MaterialTheme.colorScheme.primary,
                isSelected = selectedCategory == "all",
                isPrivateFavorite = false,
                disableAnimations = disableDecorAnimations,
                onClick = { selectedCategory = "all" },
              )
            }

            if (favoriteCategories.isNotEmpty()) {
              item(key = "for_you") {
                val favCount = uiState.missions.count { it.mission.category in favoriteCategories }
                MissionCategoryCard(
                  key = "for_you",
                  label = "Para ti",
                  count = favCount,
                  icon = Icons.Rounded.Star,
                  accentColor = MaterialTheme.colorScheme.tertiary,
                  isSelected = selectedCategory == "for_you",
                  isPrivateFavorite = true,
                  disableAnimations = disableDecorAnimations,
                  onClick = { selectedCategory = "for_you" },
                )
              }
            }

            item(key = "timed_1min") {
              MissionCategoryCard(
                key = "timed_1min",
                label = "Reloj 1:00",
                count = uiState.missions.count { it.mission.timeLimitSeconds > 0 },
                icon = Icons.Rounded.Timer,
                accentColor = MaterialTheme.colorScheme.error,
                isSelected = selectedCategory == "timed_1min",
                isPrivateFavorite = false,
                disableAnimations = disableDecorAnimations,
                onClick = { selectedCategory = "timed_1min" },
              )
            }

            items(MissionCategory.entries, key = { it.key }, contentType = { "category_pill" }) { cat ->
              val pillCount = if (cat == MissionCategory.PUZZLE) {
                uiState.missions.count { it.mission.category == "puzzle" || it.mission.missionType in setOf("wires", "puzzle", "memory") }
              } else {
                categoryCounts[cat.key] ?: 0
              }
              MissionCategoryCard(
                key = cat.key,
                label = cat.label,
                count = pillCount,
                icon = categoryIcon(cat.key),
                accentColor = categoryColor(cat.key, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                isSelected = selectedCategory == cat.key,
                isPrivateFavorite = cat.key in favoriteCategories,
                disableAnimations = disableDecorAnimations,
                onClick = { selectedCategory = cat.key },
              )
            }
          }
        }

        if (filteredMissions.isEmpty()) {
          item(key = "missions_empty_card") {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.Explore,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(32.dp),
                )
                Text(
                  text = "Sin misiones disponibles aquí",
                  style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilledTonalButton(onClick = onRespawnRandomMissions) {
                    Text("Generar aleatorias")
                  }
                  FilledTonalButton(onClick = { showCreateMissionDialog = true }) {
                    Text("Crear misión")
                  }
                }
              }
            }
          }
        } else {
          items(
            items = filteredMissions,
            key = { it.mission.id },
            contentType = { "mission_card" },
          ) { item ->
            val queuePos = uiState.waitingMissionQueue.indexOf(item.mission.id).takeIf { it >= 0 }?.plus(1)
            MissionCard(
              item = item,
              isPrivateMatch = item.mission.category in favoriteCategories,
              isAccepted = item.mission.id == uiState.activeAcceptedMissionId,
              waitQueuePosition = queuePos,
              hasLocationPermission = uiState.hasLocationPermission,
              disableAnimations = disableDecorAnimations,
              onOpenDetails = { selectedMission = item },
              onLaunchMission = { launchMissionFlow(item) },
              onAccept = { onAcceptMission(item.mission.id) },
              onWait = { onWaitMission(item.mission.id) },
              onReject = { onRejectMission(item.mission.id) },
              modifier = Modifier.padding(horizontal = 16.dp),
            )
          }
        }
      }

      if (!useExternalConfettiHost) {
        ConfettiCelebrationOverlay(
          triggerKey = localCelebrationTrigger,
          disableAnimations = disableDecorAnimations,
        )
      }
    }
  }

  if (showExpandedMap) {
    ExpandedRadarMapDialog(
      myProfile = uiState.myProfile,
      nearbyPlayers = uiState.nearbyPlayers,
      missions = uiState.missions,
      searchRadiusMeters = uiState.settings.searchRadiusMeters,
      hasLocationPermission = uiState.hasLocationPermission,
      userLat = uiState.currentLocation?.latitude ?: uiState.myProfile?.latitude?.takeIf { it != 0.0 } ?: 19.4326,
      userLon = uiState.currentLocation?.longitude ?: uiState.myProfile?.longitude?.takeIf { it != 0.0 } ?: -99.1332,
      safePublicAnchorsCount = uiState.safePublicAnchorsCount,
      activeAcceptedMissionId = uiState.activeAcceptedMissionId,
      pauseMapUpdates = isModalOpen,
      onDismiss = { showExpandedMap = false },
      onMissionClick = { selectedMission = it },
      onPlayerClick = { selectedPlayer = it },
      onRespawnRandomMissions = onRespawnRandomMissions,
    )
  }

  selectedMission?.let { item ->
    val queuePos = uiState.waitingMissionQueue.indexOf(item.mission.id).takeIf { it >= 0 }?.plus(1)
    MissionDetailSheet(
      item = item,
      hasLocationPermission = uiState.hasLocationPermission,
      showExpandMapButton = !showExpandedMap,
      isAccepted = item.mission.id == uiState.activeAcceptedMissionId,
      waitQueuePosition = queuePos,
      isSubmitting = uiState.isSubmitting,
      onDismiss = { selectedMission = null },
      onStartMissionAction = {
        selectedMission = null
        launchMissionFlow(item)
      },
      onCompleteMiniGame = { caption, isPublic ->
        onCompleteMiniGame(item.mission, caption, isPublic)
        if (!useExternalConfettiHost) {
          localCelebrationTrigger += 1L
          HapticFeedbackHelper.performMissionVerified(context)
        }
        selectedMission = null
      },
      onAcceptMission = {
        onAcceptMission(item.mission.id)
        selectedMission = null
      },
      onWaitMission = {
        onWaitMission(item.mission.id)
        selectedMission = null
      },
      onRejectMission = {
        onRejectMission(item.mission.id)
        selectedMission = null
      },
      onOpenExpandedMap = {
        selectedMission = null
        showExpandedMap = true
      },
      onRequestLocationPermission = {
        selectedMission = null
        onRequestLocationPermission()
      },
    )
  }

  selectedPlayer?.let { target ->
    PlayerActionSheet(
      playerItem = target,
      featuredMission = uiState.missions.firstOrNull()?.mission,
      onDismiss = { selectedPlayer = null },
      onHelpPlayer = { onHelpPlayer(target) },
      onCompeteDuel = { mission -> onCompeteDuel(target, mission) },
      onPlayCoop = { mission -> onPlayCoop(target, mission) },
      onSendPulse = { onSendPulse(target) },
    )
  }

  activeMissionPlayTarget?.let { mission ->
    when (mission.missionType) {
      "wires" -> WireConnectGameDialog(
        mission = mission,
        isSubmitting = uiState.isSubmitting,
        onDismiss = { activeMissionPlayTarget = null },
        onComplete = { caption, isPublic ->
          onCompleteMiniGame(mission, caption, isPublic)
          if (!useExternalConfettiHost) {
            localCelebrationTrigger += 1L
            HapticFeedbackHelper.performMissionVerified(context)
          }
          activeMissionPlayTarget = null
        },
      )

      "puzzle" -> TilePuzzleGameDialog(
        mission = mission,
        isSubmitting = uiState.isSubmitting,
        onDismiss = { activeMissionPlayTarget = null },
        onComplete = { caption, isPublic ->
          onCompleteMiniGame(mission, caption, isPublic)
          if (!useExternalConfettiHost) {
            localCelebrationTrigger += 1L
            HapticFeedbackHelper.performMissionVerified(context)
          }
          activeMissionPlayTarget = null
        },
      )

      else -> PhotoConfirmSheet(
        mission = mission,
        isSubmitting = uiState.isSubmitting,
        onDismiss = { activeMissionPlayTarget = null },
        onConfirmPhoto = { caption, base64, isPublic ->
          onConfirmMissionPhoto(mission, caption, base64, isPublic)
          if (!useExternalConfettiHost) {
            localCelebrationTrigger += 1L
            HapticFeedbackHelper.performMissionVerified(context)
          }
          activeMissionPlayTarget = null
        },
      )
    }
  }

  if (showCreateMissionDialog) {
    CreateMissionDialog(
      onDismiss = { showCreateMissionDialog = false },
      onCreate = { title, desc, cat, xp, type, icon, timeLimit ->
        if (onCreateTimedMission != null) {
          onCreateTimedMission(title, desc, cat, xp, type, icon, timeLimit)
        } else {
          onCreateMission(title, desc, cat, xp, type, icon)
        }
        showCreateMissionDialog = false
      },
    )
  }
}

@Composable
private fun MissionCategoryCard(
  key: String,
  label: String,
  count: Int,
  icon: ImageVector,
  accentColor: Color,
  isSelected: Boolean,
  isPrivateFavorite: Boolean,
  disableAnimations: Boolean,
  onClick: () -> Unit,
) {
  val bg = if (disableAnimations) {
    if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh
  } else {
    animateColorAsState(
      targetValue = if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh,
      label = "catBg",
    ).value
  }
  val fg = if (disableAnimations) {
    if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
  } else {
    animateColorAsState(
      targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
      label = "catFg",
    ).value
  }

  Surface(
    color = bg,
    shape = RoundedCornerShape(26.dp),
    tonalElevation = if (isSelected) 4.dp else 1.dp,
    modifier = Modifier
      .let { if (disableAnimations) it else it.animateContentSize() }
      .border(
        width = if (!isSelected && isPrivateFavorite) 1.5.dp else 0.dp,
        color = if (!isSelected && isPrivateFavorite) accentColor.copy(alpha = 0.55f) else Color.Transparent,
        shape = RoundedCornerShape(26.dp),
      )
      .clip(RoundedCornerShape(26.dp))
      .clickable(onClick = onClick)
      .testTag("mission_category_chip_$key"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(CircleShape)
          .background(if (isSelected) Color.White.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isSelected) Color.White else accentColor,
          modifier = Modifier.size(17.dp),
        )
      }
      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = fg,
          )
          if (isPrivateFavorite && key != "for_you") {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = "Tu gusto privado",
              tint = if (isSelected) Color.White.copy(alpha = 0.85f) else accentColor,
              modifier = Modifier.size(11.dp),
            )
          }
        }
        Text(
          text = "$count disponibles",
          style = MaterialTheme.typography.labelSmall,
          color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun NearbyPlayerCard(
  item: NearbyPlayerItem,
  onClick: () -> Unit,
  onQuickHelp: () -> Unit,
) {
  val player = item.profile
  val primaryFallback = MaterialTheme.colorScheme.primary
  val dotColor = remember(player.avatarColorHex, primaryFallback) {
    runCatching {
      Color(android.graphics.Color.parseColor(player.avatarColorHex))
    }.getOrElse { primaryFallback }
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier
      .width(188.dp)
      .clickable(onClick = onClick)
      .testTag("nearby_player_card_${player.userId}"),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(dotColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (player.seekingHelp) Icons.Rounded.VolunteerActivism else Icons.Rounded.Person,
            contentDescription = player.callsign,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = player.callsign,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = if (item.distanceMeters > 0f) {
              "${GeoUtils.formatDistance(item.distanceMeters)} • Nv ${player.level}"
            } else {
              "En vivo • Nv ${player.level}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      }

      Text(
        text = player.statusMessage.ifBlank { "Explorando cerca" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalButton(
          onClick = onQuickHelp,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .height(36.dp),
        ) {
          Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Ayudar", style = MaterialTheme.typography.labelSmall)
        }
        FilledTonalButton(
          onClick = onClick,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .height(36.dp),
        ) {
          Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Jugar", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
private fun MissionCard(
  item: MissionWithDistance,
  isPrivateMatch: Boolean,
  isAccepted: Boolean,
  waitQueuePosition: Int?,
  hasLocationPermission: Boolean,
  disableAnimations: Boolean,
  onOpenDetails: () -> Unit,
  onLaunchMission: () -> Unit,
  onAccept: () -> Unit,
  onWait: () -> Unit,
  onReject: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val mission = item.mission
  val cat = MissionCategory.fromKey(mission.category)
  val missionIcon = MissionIcons.iconForMission(mission)
  val accentColor = MissionIcons.accentColorForMission(
    mission,
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
  )
  val unlocked = hasLocationPermission && item.isWithinProximity

  Card(
    colors = CardDefaults.cardColors(
      containerColor = when {
        isAccepted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        item.isCompletedByMe -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
      },
    ),
    shape = MaterialTheme.shapes.large,
    modifier = modifier
      .fillMaxWidth()
      .let {
        if (isAccepted) {
          it.border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large)
        } else {
          it
        }
      }
      .clip(MaterialTheme.shapes.large)
      .clickable(onClick = onOpenDetails)
      .testTag("mission_card_${mission.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = missionIcon,
              contentDescription = mission.title,
              tint = accentColor,
              modifier = Modifier.size(21.dp),
            )
          }
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Surface(color = accentColor, shape = CircleShape) {
                Text(
                  text = MissionIcons.typeLabel(mission.missionType).uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                )
              }
              Text(
                text = cat.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
              )
              if (mission.timeLimitSeconds > 0) {
                Surface(
                  color = MaterialTheme.colorScheme.errorContainer,
                  shape = CircleShape,
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Timer,
                      contentDescription = "Temporizador de 1 minuto",
                      tint = MaterialTheme.colorScheme.onErrorContainer,
                      modifier = Modifier.size(11.dp),
                    )
                    Text(
                      text = "1:00",
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                  }
                }
              }
              if (isPrivateMatch && !item.isCompletedByMe) {
                if (disableAnimations) {
                  ParaTiBadge()
                } else {
                  AnimatedVisibility(visible = true) {
                    ParaTiBadge()
                  }
                }
              }
            }
            Text(
              text = mission.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }

        Surface(
          color = if (item.isCompletedByMe) {
            MaterialTheme.colorScheme.secondaryContainer
          } else {
            MaterialTheme.colorScheme.tertiaryContainer
          },
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = if (item.isCompletedByMe) "Completada" else "+${mission.xpReward} XP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (item.isCompletedByMe) {
              MaterialTheme.colorScheme.onSecondaryContainer
            } else {
              MaterialTheme.colorScheme.onTertiaryContainer
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          )
        }
      }

      Text(
        text = mission.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.VerifiedUser,
          contentDescription = null,
          tint = Color(0xFF1B8E6E),
          modifier = Modifier.size(14.dp),
        )
        Text(
          text = "${mission.placeLabel} • Punto público seguro",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = if (unlocked) Icons.Rounded.Flag else Icons.Rounded.Lock,
            contentDescription = null,
            tint = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
          )
          Text(
            text = if (unlocked) {
              "¡En punto (${GeoUtils.formatDistance(item.distanceMeters)})!"
            } else {
              "A ${GeoUtils.formatDistance(item.distanceMeters)} (${GeoUtils.cardinalDirectionEs(item.bearingDegrees)})"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        if (unlocked) {
          Button(
            onClick = onLaunchMission,
            shape = CircleShape,
            modifier = Modifier
              .height(40.dp)
              .testTag("confirm_mission_photo_button_${mission.id}"),
          ) {
            Icon(
              imageVector = if (item.isCompletedByMe) Icons.Rounded.CheckCircle else MissionIcons.typeActionIcon(mission.missionType),
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = when {
                mission.missionType == "wires" -> "Cables"
                mission.missionType == "puzzle" -> "Puzzle"
                mission.missionType == "memory" -> "Secuencia"
                item.isCompletedByMe -> "Repetir"
                else -> "Foto"
              },
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      // Accept / Wait / Reject action row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        FilledTonalButton(
          onClick = onAccept,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .testTag("accept_mission_button_${mission.id}"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = if (isAccepted) "Activa" else "Aceptar",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
          )
        }

        OutlinedButton(
          onClick = onWait,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .testTag("wait_mission_button_${mission.id}"),
        ) {
          Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = if (waitQueuePosition != null) "Cola #$waitQueuePosition" else "Esperar",
            style = MaterialTheme.typography.labelSmall,
          )
        }

        OutlinedButton(
          onClick = onReject,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier
            .weight(1f)
            .height(38.dp)
            .testTag("reject_mission_button_${mission.id}"),
        ) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = "Rechazar",
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }
    }
  }
}

@Composable
private fun ParaTiBadge() {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer,
    shape = CircleShape,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.Star,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.size(10.dp),
      )
      Text(
        text = "Para ti",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateMissionDialog(
  onDismiss: () -> Unit,
  onCreate: (title: String, description: String, category: String, xpReward: Long, missionType: String, iconKey: String, timeLimitSeconds: Int) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("explore") }
  var missionType by remember { mutableStateOf("photo") }
  var xpReward by remember { mutableLongStateOf(140L) }
  var timedOneMinute by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nueva misión en punto público") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 80) title = it },
          label = { Text("Título de la misión") },
          placeholder = { Text("Ej. Circuito secreto del parque") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { if (it.length <= 280) description = it },
          label = { Text("Instrucciones") },
          placeholder = { Text("Describe qué hacer al llegar...") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
        Text("Tipo de reto", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          listOf(
            "photo" to "Foto",
            "wires" to "Cables",
            "puzzle" to "Puzzle 3×3",
            "memory" to "Secuencia",
          ).forEach { (typeKey, label) ->
            FilterChip(
              selected = missionType == typeKey,
              onClick = {
                missionType = typeKey
                if (typeKey != "photo" && category != "puzzle") {
                  category = "puzzle"
                }
              },
              shape = CircleShape,
              label = { Text(label) },
              leadingIcon = {
                Icon(MissionIcons.typeActionIcon(typeKey), contentDescription = null, modifier = Modifier.size(15.dp))
              },
            )
          }
        }
        if (missionType != "photo") {
          FilterChip(
            selected = timedOneMinute,
            onClick = { timedOneMinute = !timedOneMinute },
            shape = CircleShape,
            label = { Text("Reloj de 1 minuto (60s)") },
            leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(15.dp)) },
          )
        }
        Text("Categoría", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          MissionCategory.entries.forEach { cat ->
            FilterChip(
              selected = category == cat.key,
              onClick = { category = cat.key },
              shape = CircleShape,
              label = { Text(cat.label) },
              leadingIcon = {
                Icon(categoryIcon(cat.key), contentDescription = null, modifier = Modifier.size(15.dp))
              },
            )
          }
        }
        Text("Recompensa XP", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(100L, 140L, 200L).forEach { xp ->
            FilterChip(
              selected = xpReward == xp,
              onClick = { xpReward = xp },
              shape = CircleShape,
              label = { Text("+$xp XP") },
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (title.trim().length >= 3 && description.trim().length >= 5) {
            val chosenIcon = when (missionType) {
              "wires" -> "cable"
              "puzzle" -> "extension"
              "memory" -> "memory"
              else -> when (category) {
                "eco" -> "park"
                "social" -> "volunteer"
                "challenge" -> "camera"
                "fitness" -> "directions_run"
                "creative" -> "brush"
                "puzzle" -> "extension"
                else -> "explore"
              }
            }
            val limit = if (missionType != "photo" && timedOneMinute) 60 else 0
            onCreate(title.trim(), description.trim(), category, xpReward, missionType, chosenIcon, limit)
          }
        },
        enabled = title.trim().length >= 3 && description.trim().length >= 5,
      ) {
        Text("Publicar (+$xpReward XP)")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancelar")
      }
    },
  )
}

private fun categoryIcon(key: String): ImageVector = when (key) {
  "eco" -> Icons.Rounded.LocalFlorist
  "social" -> Icons.Rounded.VolunteerActivism
  "challenge" -> Icons.Rounded.CameraAlt
  "fitness" -> Icons.AutoMirrored.Rounded.DirectionsRun
  "creative" -> Icons.Rounded.Brush
  "puzzle" -> Icons.Rounded.Extension
  else -> Icons.Rounded.Explore
}

private fun categoryColor(key: String, primary: Color, tertiary: Color): Color = when (key) {
  "eco" -> Color(0xFF1B8E6E)
  "social" -> tertiary
  "challenge" -> Color(0xFFD84315)
  "fitness" -> Color(0xFFE67E22)
  "creative" -> Color(0xFF8E44AD)
  "puzzle" -> Color(0xFF5E35B1)
  else -> primary
}
