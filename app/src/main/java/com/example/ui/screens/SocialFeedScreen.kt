package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LocalMissionFeedItem
import com.example.data.model.LocalMissionPost
import com.example.data.model.MissionCategory
import com.example.data.model.MissionCompletion
import com.example.data.model.PlayerProfile
import com.example.ui.components.ConfettiCelebrationOverlay
import com.example.ui.components.ContributionScoreBadge
import com.example.ui.components.RoleTitleBadge
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.GeoUtils
import com.example.util.HapticFeedbackHelper
import com.example.util.PhotoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
  uiState: RadarQuestUiState,
  currentUserId: String,
  onRefresh: () -> Unit = {},
  onCheerCompletion: (MissionCompletion) -> Unit,
  onPublishLocalMission: (title: String, description: String, category: String, xpReward: Long, placeLabel: String) -> Unit = { _, _, _, _, _ -> },
  onGenerateLocalMissions: () -> Unit = {},
  onOpenFeedMissionOnRadar: (LocalMissionPost) -> Unit = {},
  onVerifyFeedMission: (LocalMissionPost) -> Unit = {},
  useExternalConfettiHost: Boolean = false,
  modifier: Modifier = Modifier,
) {
  var feedScope by remember { mutableStateOf("all") } // all, missions, immediate, photos
  val context = LocalContext.current
  var selectedCategory by remember { mutableStateOf("all") }
  var cheeredIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var localVerifiedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var localCelebrationTrigger by remember { mutableLongStateOf(0L) }
  var zoomedPost by remember { mutableStateOf<MissionCompletion?>(null) }
  var showCreateFeedPostDialog by remember { mutableStateOf(false) }

  val playersById = remember(uiState.leaderboardPlayers, uiState.myProfile) {
    val map = uiState.leaderboardPlayers.associateBy { it.userId }.toMutableMap()
    uiState.myProfile?.let { map[it.userId] = it }
    map
  }

  val filteredMissionFeed = remember(uiState.localMissionFeed, selectedCategory, feedScope) {
    uiState.localMissionFeed.filter { item ->
      val catOk = selectedCategory == "all" || item.post.category == selectedCategory
      val proxOk = feedScope != "immediate" || item.isImmediateProximity
      catOk && proxOk
    }
  }

  val filteredPhotoPosts = remember(uiState.publicCompletions, selectedCategory) {
    if (selectedCategory == "all") {
      uiState.publicCompletions
    } else {
      uiState.publicCompletions.filter { it.category == selectedCategory }
    }
  }

  val completionCountsByAuthor = remember(uiState.publicCompletions) {
    uiState.publicCompletions.groupingBy { it.userId.trim().lowercase() }.eachCount()
  }

  val showMissionSection = feedScope != "photos"
  val showPhotoSection = feedScope == "all" || feedScope == "photos"
  val immediateCount = remember(uiState.localMissionFeed) {
    uiState.localMissionFeed.count { it.isImmediateProximity }
  }

  PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier
      .fillMaxSize()
      .testTag("social_feed_screen"),
  ) {
    LazyColumn(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .widthIn(max = 680.dp)
        .fillMaxSize()
        .testTag("social_feed_list"),
        contentPadding = PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 16.dp,
          bottom = if (uiState.settings.gestureNavEnabled) 28.dp else 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Header
        item(key = "social_feed_header") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Public,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
              }
              Column {
                Text("Red Social", style = MaterialTheme.typography.headlineMedium)
                Text(
                  "Feed local en vivo por proximidad GPS y fotos de exploradores",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = CircleShape,
            ) {
              Text(
                text = "${uiState.localMissionFeed.size} cerca",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }

        // Local Real-Time GPS Proximity Actions Row
        item(key = "local_feed_actions_card") {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("local_feed_live_banner"),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.MyLocation,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp),
                )
                Text(
                  text = if (uiState.currentLocation != null) {
                    "GPS activo • $immediateCount misiones en rango inmediato (≤${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m)"
                  } else {
                    "Almacenamiento local Room activo • Actualizando por proximidad"
                  },
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  fontWeight = FontWeight.SemiBold,
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  onClick = { showCreateFeedPostDialog = true },
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("create_local_feed_post_button"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Publicar misión", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                FilledTonalButton(
                  onClick = onGenerateLocalMissions,
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                  modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("generate_local_feed_button"),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Generar cerca", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
          }
        }

        // Feed Scope Selector (All / Live Proximity / Immediate <=120m / Photos)
        item(key = "feed_scope_chips") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "scope_all") {
              FilterChip(
                selected = feedScope == "all",
                onClick = { feedScope = "all" },
                shape = CircleShape,
                label = { Text("Todo") },
                modifier = Modifier.testTag("feed_mode_all"),
              )
            }
            item(key = "scope_missions") {
              FilterChip(
                selected = feedScope == "missions",
                onClick = { feedScope = "missions" },
                shape = CircleShape,
                label = { Text("Misiones en vivo (${uiState.localMissionFeed.size})") },
                leadingIcon = {
                  Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("feed_mode_missions"),
              )
            }
            item(key = "scope_immediate") {
              FilterChip(
                selected = feedScope == "immediate",
                onClick = { feedScope = "immediate" },
                shape = CircleShape,
                label = { Text("Muy cerca ($immediateCount)") },
                leadingIcon = {
                  Icon(Icons.Rounded.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("feed_mode_immediate"),
              )
            }
            item(key = "scope_photos") {
              FilterChip(
                selected = feedScope == "photos",
                onClick = { feedScope = "photos" },
                shape = CircleShape,
                label = { Text("Fotos (${uiState.publicCompletions.size})") },
                modifier = Modifier.testTag("feed_mode_photos"),
              )
            }
          }
        }

        // Category Filter Row
        item(key = "feed_category_chips") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "cat_all") {
              FilterChip(
                selected = selectedCategory == "all",
                onClick = { selectedCategory = "all" },
                shape = CircleShape,
                label = { Text("Todas las categorías") },
                leadingIcon = {
                  Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                },
              )
            }
            items(MissionCategory.entries, key = { it.key }) { cat ->
              FilterChip(
                selected = selectedCategory == cat.key,
                onClick = { selectedCategory = cat.key },
                shape = CircleShape,
                label = { Text(cat.label) },
              )
            }
          }
        }

        // Section 1: Real-Time Local Proximity Mission Feed
        if (showMissionSection) {
          if (filteredMissionFeed.isNotEmpty()) {
            item(key = "live_mission_feed_header") {
              Text(
                text = if (feedScope == "immediate") {
                  "Misiones en proximidad inmediata (≤120 m)"
                } else {
                  "Feed de misiones locales en tiempo real"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            items(
              items = filteredMissionFeed,
              key = { "live_${it.post.id}" },
              contentType = { "local_mission_feed_card" },
            ) { feedItem ->
              val isVerified = feedItem.post.id in uiState.verifiedFeedPostIds || feedItem.post.id in localVerifiedIds
              LocalMissionFeedCard(
                item = feedItem,
                isMine = feedItem.post.authorUserId == currentUserId,
                isVerified = isVerified,
                onVerifyMission = {
                  localVerifiedIds = localVerifiedIds + feedItem.post.id
                  if (!useExternalConfettiHost) {
                    localCelebrationTrigger += 1L
                    HapticFeedbackHelper.performMissionVerified(context)
                  }
                  onVerifyFeedMission(feedItem.post)
                },
                onOpenOnRadar = { onOpenFeedMissionOnRadar(feedItem.post) },
              )
            }
          } else if (feedScope != "all" || filteredPhotoPosts.isEmpty()) {
            item(key = "live_mission_feed_empty") {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                  )
                  Text(
                    text = if (feedScope == "immediate") {
                      "No hay misiones a menos de 120 m en este filtro"
                    } else {
                      "Aún no hay publicaciones locales en este filtro"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                  )
                  FilledTonalButton(onClick = onGenerateLocalMissions) {
                    Text("Generar misiones cercanas")
                  }
                }
              }
            }
          }
        }

        // Section 2: Community Photo Proofs
        if (showPhotoSection) {
          if (filteredPhotoPosts.isNotEmpty()) {
            item(key = "photo_feed_header") {
              Text(
                text = "Fotos confirmadas de la comunidad (${filteredPhotoPosts.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
            }
            items(
              items = filteredPhotoPosts,
              key = { "photo_${it.id}" },
              contentType = { "social_photo_card" },
            ) { comp ->
              val authorProfile = playersById[comp.userId]
              val isMine = comp.userId == currentUserId
              val alreadyCheered = comp.id in cheeredIds
              val uidKey = comp.userId.trim().lowercase()
              val unameKey = authorProfile?.username?.trim()?.removePrefix("@")?.lowercase().orEmpty()
              val authorContributionScore = maxOf(
                uiState.authorContributionScores[uidKey] ?: 0L,
                uiState.authorContributionScores[unameKey] ?: 0L,
                authorProfile?.missionsCompleted ?: 0L,
                (completionCountsByAuthor[uidKey] ?: 0).toLong(),
              )

              SocialFeedPostCard(
                comp = comp,
                authorProfile = authorProfile,
                authorContributionScore = authorContributionScore,
                isMine = isMine,
                alreadyCheered = alreadyCheered,
                onPhotoClick = { zoomedPost = comp },
                onCheer = {
                  cheeredIds = cheeredIds + comp.id
                  onCheerCompletion(comp)
                },
              )
            }
          } else if (feedScope == "photos") {
            item(key = "photo_feed_empty") {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(56.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.CameraAlt,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                      modifier = Modifier.size(28.dp),
                    )
                  }
                  Text(
                    text = "El muro social está esperando tus fotos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text(
                    text = "Cuando confirmes una misión con tu cámara y actives 'Publicar en Red Social', la foto aparecerá aquí para toda la comunidad.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }
      }

      if (!useExternalConfettiHost) {
        ConfettiCelebrationOverlay(
          triggerKey = localCelebrationTrigger,
          disableAnimations = uiState.settings.animationTier == "low",
          animationTier = uiState.settings.animationTier,
        )
      }
  }

  if (showCreateFeedPostDialog) {
    CreateLocalMissionFeedDialog(
      onDismiss = { showCreateFeedPostDialog = false },
      onPublish = { title, desc, cat, xp, place ->
        onPublishLocalMission(title, desc, cat, xp, place)
        showCreateFeedPostDialog = false
      },
    )
  }

  zoomedPost?.let { post ->
    val bmp = remember(post.photoThumbnail) {
      PhotoUtils.decodeBase64Thumbnail(post.photoThumbnail)?.asImageBitmap()
    }
    Dialog(
      onDismissRequest = { zoomedPost = null },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(
        color = Color.Black.copy(alpha = 0.92f),
        modifier = Modifier.fillMaxSize(),
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          if (bmp != null) {
            Image(
              bitmap = bmp,
              contentDescription = post.missionTitle,
              contentScale = ContentScale.Fit,
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            )
          }
          IconButton(
            onClick = { zoomedPost = null },
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(20.dp),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar foto", tint = Color.White)
          }
          Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .fillMaxWidth()
              .padding(16.dp),
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text("${post.userName} • ${post.missionTitle}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              if (post.caption.isNotBlank()) {
                Text(post.caption, style = MaterialTheme.typography.bodySmall)
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalMissionFeedCard(
  item: LocalMissionFeedItem,
  isMine: Boolean,
  isVerified: Boolean,
  onVerifyMission: () -> Unit,
  onOpenOnRadar: () -> Unit,
) {
  val post = item.post
  val cat = remember(post.category) { MissionCategory.fromKey(post.category) }
  val distanceText = remember(item.distanceMeters) { GeoUtils.formatDistance(item.distanceMeters) }
  val arrow = remember(item.bearingDegrees) { GeoUtils.compassArrowForBearing(item.bearingDegrees) }
  val dir = remember(item.bearingDegrees) { GeoUtils.cardinalDirectionEs(item.bearingDegrees) }
  val relativeTime = remember(post.createdAtMillis) {
    GeoUtils.formatRelativeTimestamp(post.createdAtMillis)
  }

  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (item.isImmediateProximity) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    border = if (item.isImmediateProximity) {
      BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
      null
    },
    shape = MaterialTheme.shapes.large,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("local_mission_feed_card_${post.id}"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Author row with Role Badge + Timestamp
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = post.authorDisplayName.take(1).uppercase().ifBlank { "V" },
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FlowRow(
              verticalArrangement = Arrangement.spacedBy(4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = if (isMine) "${post.authorDisplayName} (Tú)" else post.authorDisplayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically),
              )
              Text(
                text = "@${post.authorUsername}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
              )
              RoleTitleBadge(
                role = post.authorRole,
                roleTitle = post.authorRoleTitle,
                modifier = Modifier
                  .align(Alignment.CenterVertically)
                  .testTag("feed_post_role_badge_${post.id}"),
              )
              ContributionScoreBadge(
                score = item.authorContributionScore,
                modifier = Modifier
                  .align(Alignment.CenterVertically)
                  .testTag("feed_post_contribution_score_${post.id}"),
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = "Hora de publicación",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = relativeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("feed_post_timestamp_${post.id}"),
              )
              Text(
                text = "• ${cat.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = CircleShape,
        ) {
          Text(
            text = "+${post.xpReward} XP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Mission Title & Description
      Text(
        text = post.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = post.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      // Distance / Proximity & Place + Actions
      Surface(
        color = if (item.isImmediateProximity) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.secondaryContainer
        },
        shape = CircleShape,
        modifier = Modifier.testTag("feed_post_distance_${post.id}"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
          Icon(
            imageVector = if (item.isImmediateProximity) Icons.Rounded.NearMe else Icons.Rounded.LocationOn,
            contentDescription = "Distancia GPS",
            tint = if (item.isImmediateProximity) {
              MaterialTheme.colorScheme.onPrimary
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            },
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = if (item.isImmediateProximity) {
              "$distanceText • ¡En tu rango!"
            } else {
              "$distanceText • $arrow $dir"
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (item.isImmediateProximity) {
              MaterialTheme.colorScheme.onPrimary
            } else {
              MaterialTheme.colorScheme.onSecondaryContainer
            },
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = onOpenOnRadar,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("open_feed_mission_radar_${post.id}"),
        ) {
          Icon(Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Ver en Radar", style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Button(
          onClick = onVerifyMission,
          shape = CircleShape,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("verify_feed_mission_button_${post.id}"),
        ) {
          Icon(
            imageVector = if (isVerified) Icons.Rounded.CheckCircle else Icons.Rounded.Verified,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (isVerified) "Verificada" else "Verify",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      if (post.placeLabel.isNotBlank()) {
        Text(
          text = "Zona: ${post.placeLabel}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun CreateLocalMissionFeedDialog(
  onDismiss: () -> Unit,
  onPublish: (title: String, description: String, category: String, xpReward: Long, placeLabel: String) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var placeLabel by remember { mutableStateOf("") }
  var selectedCat by remember { mutableStateOf("explore") }
  var xpReward by remember { mutableLongStateOf(150L) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = MaterialTheme.shapes.extraLarge,
      tonalElevation = 6.dp,
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp)
        .padding(horizontal = 20.dp, vertical = 24.dp)
        .testTag("create_local_feed_dialog"),
    ) {
      Column(
        modifier = Modifier
          .verticalScroll(rememberScrollState())
          .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Publicar misión en el feed local",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "Se guardará localmente con tu rol actual y tu coordenada GPS en tiempo real.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 80) title = it },
          label = { Text("Título de la misión") },
          placeholder = { Text("Ej. Mirador del parque") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_post_title_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { if (it.length <= 240) description = it },
          label = { Text("Descripción") },
          placeholder = { Text("Qué debe encontrar o fotografiar el explorador") },
          minLines = 2,
          maxLines = 4,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_post_description_input"),
        )
        OutlinedTextField(
          value = placeLabel,
          onValueChange = { if (it.length <= 80) placeLabel = it },
          label = { Text("Lugar o referencia (opcional)") },
          placeholder = { Text("Plaza principal / Parque") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          MissionCategory.entries.take(5).forEach { cat ->
            FilterChip(
              selected = selectedCat == cat.key,
              onClick = { selectedCat = cat.key },
              label = { Text(cat.label) },
            )
          }
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          listOf(100L, 150L, 250L).forEach { xp ->
            FilterChip(
              selected = xpReward == xp,
              onClick = { xpReward = xp },
              label = { Text("+$xp XP") },
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) {
            Text("Cancelar")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              onPublish(
                title.trim().ifBlank { "Misión rápida cercana" },
                description.trim().ifBlank { "Explora esta ubicación cerca de tu radar y confírmala." },
                selectedCat,
                xpReward,
                placeLabel.trim().ifBlank { "Ubicación GPS actual" },
              )
            },
            modifier = Modifier.testTag("confirm_publish_feed_post_button"),
          ) {
            Text("Publicar")
          }
        }
      }
    }
  }
}

@Composable
private fun SocialFeedPostCard(
  comp: MissionCompletion,
  authorProfile: PlayerProfile?,
  authorContributionScore: Long,
  isMine: Boolean,
  alreadyCheered: Boolean,
  onPhotoClick: () -> Unit,
  onCheer: () -> Unit,
) {
  val postBitmap = remember(comp.photoThumbnail) {
    PhotoUtils.decodeBase64Thumbnail(comp.photoThumbnail)?.asImageBitmap()
  }
  val authorAvatarBitmap = remember(authorProfile?.avatarPhotoBase64) {
    val raw = authorProfile?.avatarPhotoBase64.orEmpty()
    if (raw.isNotBlank()) PhotoUtils.decodeBase64Thumbnail(raw)?.asImageBitmap() else null
  }
  val primaryFallback = MaterialTheme.colorScheme.primary
  val auraHex = authorProfile?.avatarColorHex ?: "#00BFA5"
  val auraColor = remember(auraHex, primaryFallback) {
    runCatching {
      Color(android.graphics.Color.parseColor(auraHex))
    }.getOrElse { primaryFallback }
  }
  val cat = remember(comp.category) { MissionCategory.fromKey(comp.category) }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("social_post_card_${comp.id}"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Author Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(auraColor.copy(alpha = 0.22f))
              .border(2.dp, auraColor, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            if (authorAvatarBitmap != null) {
              Image(
                bitmap = authorAvatarBitmap,
                contentDescription = comp.userName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxSize()
                  .clip(CircleShape),
              )
            } else {
              Text(
                text = comp.userName.take(1).uppercase().ifBlank { "V" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = auraColor,
              )
            }
          }

          val postRole = authorProfile?.role?.takeIf { it.isNotBlank() } ?: comp.authorRole
          val postRoleTitle = authorProfile?.roleTitle?.takeIf { it.isNotBlank() } ?: comp.authorRoleTitle
          @OptIn(ExperimentalLayoutApi::class)
          Column {
            FlowRow(
              verticalArrangement = Arrangement.spacedBy(4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = if (isMine) "${comp.userName} (Tú)" else comp.userName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterVertically),
              )
              if (!authorProfile?.username.isNullOrBlank()) {
                Text(
                  text = "@${authorProfile?.username}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.align(Alignment.CenterVertically),
                )
              }
              RoleTitleBadge(
                role = postRole,
                roleTitle = postRoleTitle,
                modifier = Modifier
                  .align(Alignment.CenterVertically)
                  .testTag("social_post_role_badge_${comp.id}"),
              )
              ContributionScoreBadge(
                score = authorContributionScore,
                modifier = Modifier
                  .align(Alignment.CenterVertically)
                  .testTag("social_post_contribution_score_${comp.id}"),
              )
            }
            Text(
              text = "${cat.label} • Misión confirmada",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = CircleShape,
        ) {
          Text(
            text = "+${comp.xpEarned} XP",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      // Photo Proof
      if (postBitmap != null) {
        Image(
          bitmap = postBitmap,
          contentDescription = comp.missionTitle,
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onPhotoClick),
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(32.dp))
        }
      }

      // Mission Tag + Caption + Social Interaction
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Verified,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = comp.missionTitle,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
        )
      }

      if (comp.caption.isNotBlank()) {
        Text(
          text = comp.caption,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (!isMine) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          FilledTonalButton(
            onClick = onCheer,
            enabled = !alreadyCheered,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.testTag("cheer_post_button_${comp.id}"),
          ) {
            Icon(
              imageVector = when {
                alreadyCheered -> Icons.Rounded.Check
                else -> Icons.Rounded.FavoriteBorder
              },
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (alreadyCheered) "Aplauso enviado" else "¡Genial! (+15 XP)",
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      } else {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.Favorite,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
          )
          Text(
            text = "Publicado en la Red Social de Veyra",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}
