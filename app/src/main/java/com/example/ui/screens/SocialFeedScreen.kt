package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.data.model.MissionCategory
import com.example.data.model.MissionCompletion
import com.example.data.model.PlayerProfile
import com.example.ui.components.RoleTitleBadge
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.PhotoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
  uiState: RadarQuestUiState,
  currentUserId: String,
  onRefresh: () -> Unit = {},
  onCheerCompletion: (MissionCompletion) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember { mutableStateOf("all") }
  var cheeredIds by remember { mutableStateOf<Set<String>>(emptySet()) }
  var zoomedPost by remember { mutableStateOf<MissionCompletion?>(null) }

  val playersById = remember(uiState.leaderboardPlayers, uiState.myProfile) {
    val map = uiState.leaderboardPlayers.associateBy { it.userId }.toMutableMap()
    uiState.myProfile?.let { map[it.userId] = it }
    map
  }

  val filteredPosts = remember(uiState.publicCompletions, selectedCategory) {
    if (selectedCategory == "all") {
      uiState.publicCompletions
    } else {
      uiState.publicCompletions.filter { it.category == selectedCategory }
    }
  }

  PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier
      .fillMaxSize()
      .testTag("social_feed_screen"),
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
          start = 16.dp,
          end = 16.dp,
          top = 16.dp,
          bottom = if (uiState.settings.gestureNavEnabled) 28.dp else 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
      // Header
      item {
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
                "Fotos publicadas por exploradores al confirmar misiones",
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
              text = "${uiState.publicCompletions.size} fotos",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
          }
        }
      }

      // Category Filter Row
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item(key = "all") {
            FilterChip(
              selected = selectedCategory == "all",
              onClick = { selectedCategory = "all" },
              shape = CircleShape,
              label = { Text("Todas") },
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

      if (filteredPosts.isEmpty()) {
        item {
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
      } else {
        items(filteredPosts, key = { it.id }) { comp ->
          val authorProfile = playersById[comp.userId]
          val isMine = comp.userId == currentUserId
          val alreadyCheered = comp.id in cheeredIds

          SocialFeedPostCard(
            comp = comp,
            authorProfile = authorProfile,
            isMine = isMine,
            alreadyCheered = alreadyCheered,
            onPhotoClick = { zoomedPost = comp },
            onCheer = {
              cheeredIds = cheeredIds + comp.id
              onCheerCompletion(comp)
            },
          )
        }
      }
    }
    }
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

@Composable
private fun SocialFeedPostCard(
  comp: MissionCompletion,
  authorProfile: PlayerProfile?,
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
  val auraColor = runCatching {
    Color(android.graphics.Color.parseColor(authorProfile?.avatarColorHex ?: "#00BFA5"))
  }.getOrElse { MaterialTheme.colorScheme.primary }
  val cat = MissionCategory.fromKey(comp.category)

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
