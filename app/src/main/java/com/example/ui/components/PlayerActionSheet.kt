package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Mission
import com.example.data.model.NearbyPlayerItem
import com.example.util.GeoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerActionSheet(
  playerItem: NearbyPlayerItem,
  featuredMission: Mission?,
  onDismiss: () -> Unit,
  onHelpPlayer: () -> Unit,
  onCompeteDuel: (Mission) -> Unit,
  onPlayCoop: (Mission) -> Unit,
  onSendPulse: () -> Unit,
) {
  val player = playerItem.profile
  val dotColor = runCatching {
    Color(android.graphics.Color.parseColor(player.avatarColorHex))
  }.getOrElse { MaterialTheme.colorScheme.primary }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(dotColor),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = if (player.seekingHelp) Icons.Rounded.VolunteerActivism else Icons.Rounded.Person,
            contentDescription = player.callsign,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = player.callsign,
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "Nivel ${player.level} • ${player.xp} XP • ${
              if (playerItem.distanceMeters > 0f) "a ${GeoUtils.formatDistance(playerItem.distanceMeters)}"
              else "En línea"
            }",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
          if (player.statusMessage.isNotBlank()) {
            Text(
              text = player.statusMessage,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (player.seekingHelp) {
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.VolunteerActivism,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
              text = "Este jugador pidió ayuda en el radar. ¡Envíale energía para ganar +30 XP!",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = {
            onHelpPlayer()
            onDismiss()
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("sheet_help_player_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Handshake, contentDescription = null)
          Spacer(modifier = Modifier.size(6.dp))
          Text("Ayudar")
        }

        FilledTonalButton(
          onClick = {
            onSendPulse()
            onDismiss()
          },
          modifier = Modifier
            .weight(1f)
            .height(50.dp)
            .testTag("sheet_pulse_player_button"),
        ) {
          Icon(imageVector = Icons.Rounded.Bolt, contentDescription = null)
          Spacer(modifier = Modifier.size(6.dp))
          Text("Pulso")
        }
      }

      if (featuredMission != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Button(
            onClick = {
              onCompeteDuel(featuredMission)
              onDismiss()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.tertiary,
              contentColor = MaterialTheme.colorScheme.onTertiary,
            ),
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("sheet_compete_duel_button"),
          ) {
            Icon(imageVector = Icons.Rounded.EmojiEvents, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Competir")
          }

          OutlinedButton(
            onClick = {
              onPlayCoop(featuredMission)
              onDismiss()
            },
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("sheet_play_coop_button"),
          ) {
            Icon(imageVector = Icons.Rounded.SportsEsports, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Jugar Co-op")
          }
        }
      }
    }
  }
}
