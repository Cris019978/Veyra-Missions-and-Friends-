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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissionCategory
import com.example.data.model.MissionWithDistance
import com.example.util.GeoUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailSheet(
  item: MissionWithDistance,
  hasLocationPermission: Boolean,
  showExpandMapButton: Boolean,
  onDismiss: () -> Unit,
  onStartPhotoConfirm: () -> Unit,
  onOpenExpandedMap: () -> Unit,
  onRequestLocationPermission: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
  val mission = item.mission
  val cat = MissionCategory.fromKey(mission.category)
  val catIcon = when (cat.key) {
    "eco" -> Icons.Rounded.LocalFlorist
    "social" -> Icons.Rounded.VolunteerActivism
    "challenge" -> Icons.Rounded.CameraAlt
    "fitness" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "creative" -> Icons.Rounded.Brush
    else -> Icons.Rounded.Explore
  }

  val proximityProgress = if (hasLocationPermission) {
    (1f - (item.distanceMeters / (GeoUtils.PROXIMITY_RADIUS_METERS * 3f))).coerceIn(0.08f, 1f)
  } else {
    0.05f
  }
  val remainingMeters = (item.distanceMeters - GeoUtils.PROXIMITY_RADIUS_METERS).coerceAtLeast(0f)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = Modifier.testTag("mission_detail_sheet"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
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
              .size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = catIcon,
              contentDescription = cat.label,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(22.dp),
            )
          }
          Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = CircleShape,
              ) {
                Text(
                  text = cat.label,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
              Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
              ) {
                Text(
                  text = "+${mission.xpReward} XP",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
              text = mission.title,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
        }
      }

      Text(
        text = mission.description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Icon(
          imageVector = Icons.Rounded.Flag,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp),
        )
        Text(
          text = "Publicada por ${mission.creatorName}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Proximity Card
      Surface(
        color = if (item.isWithinProximity) {
          MaterialTheme.colorScheme.primaryContainer
        } else {
          MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
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
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = when {
                  !hasLocationPermission -> Icons.Rounded.LocationOn
                  item.isWithinProximity -> Icons.Rounded.CheckCircle
                  else -> Icons.Rounded.Lock
                },
                contentDescription = null,
                tint = if (item.isWithinProximity) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = when {
                  !hasLocationPermission -> "GPS requerido para validar punto"
                  item.isWithinProximity -> "¡Estás dentro del punto de proximidad!"
                  else -> "Punto de proximidad bloqueado"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
              )
            }
            if (hasLocationPermission) {
              Text(
                text = GeoUtils.formatDistance(item.distanceMeters),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }

          LinearProgressIndicator(
            progress = { proximityProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(7.dp)
              .clip(CircleShape),
          )

          Text(
            text = when {
              !hasLocationPermission ->
                "Activa el permiso de ubicación para comprobar cuando te acerques a menos de ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m."
              item.isWithinProximity ->
                "Ya puedes tomar la foto dentro de la app para completar esta misión."
              else ->
                "Acércate ${GeoUtils.formatDistance(remainingMeters)} más (radio de ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m) para desbloquearla."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (!hasLocationPermission) {
        Button(
          onClick = onRequestLocationPermission,
          shape = CircleShape,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        ) {
          Icon(Icons.Rounded.LocationOn, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Activar ubicación")
        }
      } else {
        Button(
          onClick = onStartPhotoConfirm,
          enabled = item.isWithinProximity,
          shape = CircleShape,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("mission_sheet_confirm_button"),
        ) {
          Icon(
            imageVector = if (item.isWithinProximity) Icons.Rounded.CameraAlt else Icons.Rounded.Lock,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (item.isWithinProximity) {
              if (item.isCompletedByMe) "Repetir foto en el punto" else "Confirmar con foto"
            } else {
              "Acércate a menos de ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m"
            },
            fontWeight = FontWeight.Bold,
          )
        }
      }

      if (showExpandMapButton) {
        OutlinedButton(
          onClick = onOpenExpandedMap,
          shape = CircleShape,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("mission_sheet_expand_map_button"),
        ) {
          Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Ver punto en mapa expandido")
        }
      }
    }
  }
}
