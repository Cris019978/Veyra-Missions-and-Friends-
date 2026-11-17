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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Star
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import com.example.ui.components.ExpandedRadarMapDialog
import com.example.ui.components.MissionDetailSheet
import com.example.ui.components.PhotoConfirmSheet
import com.example.ui.components.PlayerActionSheet
import com.example.ui.components.RoundRadarMap
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.GeoUtils

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
  onCreateMission: (title: String, description: String, category: String, xpReward: Long) -> Unit,
  onConfirmMissionPhoto: (mission: Mission, caption: String, base64Thumb: String, isPublic: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember { mutableStateOf("all") }
  var selectedPlayer by remember { mutableStateOf<NearbyPlayerItem?>(null) }
  var selectedMissionId by remember { mutableStateOf<String?>(null) }
  var showExpandedMap by remember { mutableStateOf(false) }
  var missionToConfirm by remember { mutableStateOf<Mission?>(null) }
  var showCreateMissionDialog by remember { mutableStateOf(false) }

  val disableDecorAnimations = uiState.settings.animationTier == "low"

  val activeSelectedMission = remember(selectedMissionId, uiState.missions) {
    selectedMissionId?.let { id -> uiState.missions.firstOrNull { it.mission.id == id } }
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
      else -> uiState.missions.filter { it.mission.category == selectedCategory }
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
      item {
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

      // 2. Location Permission Prompt if not yet granted
      if (!uiState.hasLocationPermission) {
        item {
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

      // 3. Top-Center Animated Round Radar Map
      item {
        RoundRadarMap(
          myProfile = uiState.myProfile,
          nearbyPlayers = uiState.nearbyPlayers,
          missions = uiState.missions,
          searchRadiusMeters = uiState.settings.searchRadiusMeters,
          radarStyle = uiState.settings.radarStyle,
          animationTier = uiState.settings.animationTier,
          isBroadcasting = uiState.isBroadcastingRadar,
          hasLocationPermission = uiState.hasLocationPermission,
          onPlayerClick = { selectedPlayer = it },
          onMissionClick = { selectedMissionId = it.mission.id },
          onCycleRadius = onCycleRadius,
          onExpandMap = { showExpandedMap = true },
          modifier = Modifier.padding(top = 2.dp),
        )
      }

      // 4. Nearby Players Strip
      item {
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
              items(uiState.nearbyPlayers, key = { it.profile.userId }) { item ->
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

      // 5. Bottom Section: Categorized Real-Life Missions to Confirm via In-App Photo
      item {
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
            Column {
              Text(
                text = stringResource(id = R.string.missions_section_title),
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                text = "Acércate a ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m del punto para confirmar con foto",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            FilledTonalButton(
              onClick = { showCreateMissionDialog = true },
              shape = CircleShape,
              contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
              modifier = Modifier.testTag("open_create_mission_button"),
            ) {
              Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(stringResource(id = R.string.create_mission), style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }

      // Interactive Category Pills Bar
      item {
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

          items(MissionCategory.entries, key = { it.key }) { cat ->
            MissionCategoryCard(
              key = cat.key,
              label = cat.label,
              count = categoryCounts[cat.key] ?: 0,
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
        item {
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
                text = "Sin misiones en esta categoría aún",
                style = MaterialTheme.typography.titleSmall,
              )
              FilledTonalButton(onClick = { showCreateMissionDialog = true }) {
                Text("Crear la primera aquí")
              }
            }
          }
        }
      } else {
        items(filteredMissions, key = { it.mission.id }) { item ->
          MissionCard(
            item = item,
            isPrivateMatch = item.mission.category in favoriteCategories,
            hasLocationPermission = uiState.hasLocationPermission,
            disableAnimations = disableDecorAnimations,
            onOpenDetails = { selectedMissionId = item.mission.id },
            onConfirmWithPhoto = {
              if (item.isWithinProximity && uiState.hasLocationPermission) {
                missionToConfirm = item.mission
              } else {
                selectedMissionId = item.mission.id
              }
            },
            modifier = Modifier.padding(horizontal = 16.dp),
          )
        }
      }
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
      onDismiss = { showExpandedMap = false },
      onMissionClick = { selectedMissionId = it.mission.id },
      onPlayerClick = { selectedPlayer = it },
    )
  }

  activeSelectedMission?.let { item ->
    MissionDetailSheet(
      item = item,
      hasLocationPermission = uiState.hasLocationPermission,
      showExpandMapButton = !showExpandedMap,
      onDismiss = { selectedMissionId = null },
      onStartPhotoConfirm = {
        selectedMissionId = null
        if (item.isWithinProximity && uiState.hasLocationPermission) {
          missionToConfirm = item.mission
        }
      },
      onOpenExpandedMap = {
        selectedMissionId = null
        showExpandedMap = true
      },
      onRequestLocationPermission = {
        selectedMissionId = null
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

  missionToConfirm?.let { mission ->
    PhotoConfirmSheet(
      mission = mission,
      isSubmitting = uiState.isSubmitting,
      onDismiss = { missionToConfirm = null },
      onConfirmPhoto = { caption, base64, isPublic ->
        onConfirmMissionPhoto(mission, caption, base64, isPublic)
        missionToConfirm = null
      },
    )
  }

  if (showCreateMissionDialog) {
    CreateMissionDialog(
      onDismiss = { showCreateMissionDialog = false },
      onCreate = { title, desc, cat, xp ->
        onCreateMission(title, desc, cat, xp)
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
  val dotColor = runCatching {
    Color(android.graphics.Color.parseColor(player.avatarColorHex))
  }.getOrElse { MaterialTheme.colorScheme.primary }

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
  hasLocationPermission: Boolean,
  disableAnimations: Boolean,
  onOpenDetails: () -> Unit,
  onConfirmWithPhoto: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val mission = item.mission
  val cat = MissionCategory.fromKey(mission.category)
  val catIcon = categoryIcon(cat.key)
  val catColor = categoryColor(cat.key, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
  val unlocked = hasLocationPermission && item.isWithinProximity

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (item.isCompletedByMe) {
        MaterialTheme.colorScheme.surfaceContainerLow
      } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
      },
    ),
    shape = MaterialTheme.shapes.large,
    modifier = modifier
      .fillMaxWidth()
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
              .size(36.dp)
              .clip(CircleShape)
              .background(catColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = catIcon,
              contentDescription = cat.label,
              tint = catColor,
              modifier = Modifier.size(19.dp),
            )
          }
          Column {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = cat.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = catColor,
              )
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
              "A ${GeoUtils.formatDistance(item.distanceMeters)} • por ${mission.creatorName}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (unlocked) {
          Button(
            onClick = onConfirmWithPhoto,
            shape = CircleShape,
            modifier = Modifier
              .height(44.dp)
              .testTag("confirm_mission_photo_button_${mission.id}"),
          ) {
            Icon(
              imageVector = if (item.isCompletedByMe) Icons.Rounded.CheckCircle else Icons.Rounded.CameraAlt,
              contentDescription = null,
              modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (item.isCompletedByMe) "Repetir foto" else stringResource(id = R.string.confirm_with_photo),
              style = MaterialTheme.typography.labelLarge,
            )
          }
        } else {
          FilledTonalButton(
            onClick = onOpenDetails,
            shape = CircleShape,
            modifier = Modifier
              .height(44.dp)
              .testTag("inspect_mission_button_${mission.id}"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Lock,
              contentDescription = null,
              modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Ver punto",
              style = MaterialTheme.typography.labelLarge,
            )
          }
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
  onCreate: (title: String, description: String, category: String, xpReward: Long) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("explore") }
  var xpReward by remember { mutableLongStateOf(140L) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Nueva misión real") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 80) title = it },
          label = { Text("Título de la misión") },
          placeholder = { Text("Ej. Encuentra un mural colorido") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { if (it.length <= 280) description = it },
          label = { Text("Instrucciones para la foto") },
          placeholder = { Text("Describe qué debe verse en la foto...") },
          minLines = 2,
          maxLines = 3,
          modifier = Modifier.fillMaxWidth(),
        )
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
            onCreate(title.trim(), description.trim(), category, xpReward)
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
  else -> Icons.Rounded.Explore
}

private fun categoryColor(key: String, primary: Color, tertiary: Color): Color = when (key) {
  "eco" -> Color(0xFF1B8E6E)
  "social" -> tertiary
  "challenge" -> Color(0xFFD84315)
  "fitness" -> Color(0xFFE67E22)
  "creative" -> Color(0xFF8E44AD)
  else -> primary
}
