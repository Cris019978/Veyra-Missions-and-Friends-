package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MissionCategory
import com.example.data.model.NearbyMissionPopup
import com.example.util.GeoUtils

/**
 * In-app pop-up notification strictly suggesting nearby missions.
 * Contains NO accept/reject buttons and dismisses on swipe left or right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyMissionPopupOverlay(
  popups: List<NearbyMissionPopup>,
  disableAnimations: Boolean,
  onDismissPopup: (missionId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val current = popups.firstOrNull()

  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 14.dp, vertical = 8.dp),
    contentAlignment = Alignment.TopCenter,
  ) {
    if (disableAnimations) {
      if (current != null) {
        SwipeableNearbyPopupCard(
          popup = current,
          onDismissPopup = onDismissPopup,
        )
      }
    } else {
      AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
      ) {
        if (current != null) {
          SwipeableNearbyPopupCard(
            popup = current,
            onDismissPopup = onDismissPopup,
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNearbyPopupCard(
  popup: NearbyMissionPopup,
  onDismissPopup: (missionId: String) -> Unit,
) {
  key(popup.missionId) {
    val dismissState = rememberSwipeToDismissBoxState(
      confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
          onDismissPopup(popup.missionId)
          true
        } else {
          false
        }
      },
    )

    val cat = MissionCategory.fromKey(popup.category)
    val catIcon = when (cat.key) {
      "eco" -> Icons.Rounded.LocalFlorist
      "social" -> Icons.Rounded.VolunteerActivism
      "challenge" -> Icons.Rounded.CameraAlt
      "fitness" -> Icons.AutoMirrored.Rounded.DirectionsRun
      "creative" -> Icons.Rounded.Brush
      else -> Icons.Rounded.Explore
    }

    SwipeToDismissBox(
      state = dismissState,
      enableDismissFromStartToEnd = true,
      enableDismissFromEndToStart = true,
      modifier = Modifier
        .widthIn(max = 560.dp)
        .fillMaxWidth()
        .testTag("nearby_mission_popup_${popup.missionId}"),
      backgroundContent = {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(Color.Transparent),
        )
      },
    ) {
      Card(
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.inverseSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.inversePrimary),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = catIcon,
              contentDescription = cat.label,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(20.dp),
            )
          }

          Column(modifier = Modifier.weight(1f)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "Cerca de ti • ${cat.label}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.inversePrimary,
              )
              Surface(
                color = MaterialTheme.colorScheme.inversePrimary.copy(alpha = 0.22f),
                shape = CircleShape,
              ) {
                Text(
                  text = "+${popup.xpReward} XP",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.inverseOnSurface,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }

            Text(
              text = popup.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.inverseOnSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = "A ${GeoUtils.formatDistance(popup.distanceMeters)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.85f),
              )
              Spacer(modifier = Modifier.width(2.dp))
              Icon(
                imageVector = Icons.Rounded.Swipe,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f),
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "Desliza ← o → para quitar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.65f),
              )
            }
          }
        }
      }
    }
  }
}
