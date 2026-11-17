package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissionCategory
import com.example.data.model.MissionWithDistance
import com.example.util.GeoUtils

@Composable
fun QueuedMissionPromptDialog(
  queuedMission: MissionWithDistance,
  remainingQueueCount: Int,
  onAcceptNow: () -> Unit,
  onReject: () -> Unit,
  onKeepWaiting: () -> Unit,
) {
  val mission = queuedMission.mission
  val cat = MissionCategory.fromKey(mission.category)
  val icon = MissionIcons.iconForMission(mission)
  val accent = MissionIcons.accentColorForMission(
    mission,
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
  )

  AlertDialog(
    onDismissRequest = onKeepWaiting,
    modifier = Modifier.testTag("queued_mission_prompt_dialog"),
    icon = {
      Box(
        modifier = Modifier
          .size(52.dp)
          .clip(CircleShape)
          .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(28.dp),
        )
      }
    },
    title = {
      Text(
        text = "Misión en espera lista",
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Terminaste tu misión anterior. Tienes esta misión en tu cola:",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Row(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Surface(color = accent, shape = CircleShape) {
                Text(
                  text = MissionIcons.typeLabel(mission.missionType),
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
              Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
                Text(
                  text = "+${mission.xpReward} XP",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
              }
            }
            Text(
              text = mission.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "${cat.label} • A ${GeoUtils.formatDistance(queuedMission.distanceMeters)}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text = mission.description,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        if (remainingQueueCount > 0) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.HourglassTop,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = "+$remainingQueueCount misiones más en espera después de esta.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onAcceptNow,
        shape = CircleShape,
        modifier = Modifier.testTag("queued_prompt_accept_button"),
      ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Completar ahora")
      }
    },
    dismissButton = {
      OutlinedButton(
        onClick = onReject,
        shape = CircleShape,
        modifier = Modifier.testTag("queued_prompt_reject_button"),
      ) {
        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Rechazar")
      }
    },
  )
}
