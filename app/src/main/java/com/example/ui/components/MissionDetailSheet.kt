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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
  isAccepted: Boolean = false,
  waitQueuePosition: Int? = null,
  onDismiss: () -> Unit,
  onStartMissionAction: () -> Unit,
  onAcceptMission: () -> Unit = {},
  onWaitMission: () -> Unit = {},
  onRejectMission: () -> Unit = {},
  onOpenExpandedMap: () -> Unit,
  onRequestLocationPermission: () -> Unit,
) {
  val context = LocalContext.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val mission = item.mission
  val cat = MissionCategory.fromKey(mission.category)
  val missionIcon = MissionIcons.iconForMission(mission)
  val accentColor = MissionIcons.accentColorForMission(
    mission,
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.tertiary,
  )
  val actionIcon = MissionIcons.typeActionIcon(mission.missionType)

  val proximityProgress = if (hasLocationPermission) {
    (1f - (item.distanceMeters / (GeoUtils.PROXIMITY_RADIUS_METERS * 3f))).coerceIn(0.08f, 1f)
  } else {
    0.05f
  }
  val remainingMeters = (item.distanceMeters - GeoUtils.PROXIMITY_RADIUS_METERS).coerceAtLeast(0f)
  val dirText = "${GeoUtils.compassArrowForBearing(item.bearingDegrees)} ${GeoUtils.cardinalDirectionEs(item.bearingDegrees)}"

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
      verticalArrangement = Arrangement.spacedBy(12.dp),
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
              .size(46.dp)
              .clip(CircleShape)
              .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = missionIcon,
              contentDescription = mission.title,
              tint = accentColor,
              modifier = Modifier.size(24.dp),
            )
          }
          Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
              Surface(color = accentColor, shape = CircleShape) {
                Text(
                  text = MissionIcons.typeLabel(mission.missionType),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
              }
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
          text = "Publicada por ${mission.creatorName} • Rumbo $dirText",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Accept / Wait / Reject Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilledTonalButton(
          onClick = { onAcceptMission() },
          shape = CircleShape,
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("mission_sheet_accept_button"),
        ) {
          Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isAccepted) "Activa" else "Aceptar",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
          )
        }
        OutlinedButton(
          onClick = { onWaitMission() },
          shape = CircleShape,
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("mission_sheet_wait_button"),
        ) {
          Icon(Icons.Rounded.HourglassTop, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (waitQueuePosition != null) "Cola #$waitQueuePosition" else "Esperar",
            style = MaterialTheme.typography.labelMedium,
          )
        }
        OutlinedButton(
          onClick = { onRejectMission() },
          shape = CircleShape,
          modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .testTag("mission_sheet_reject_button"),
        ) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Rechazar", style = MaterialTheme.typography.labelMedium)
        }
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
                  item.distanceMeters <= GeoUtils.CLOSE_GUIDANCE_RADIUS_METERS -> "¡Punto final a unos pasos!"
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
                "Activa el permiso de ubicación para desbloquear al acercarte a menos de ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m."
              item.isWithinProximity -> when (mission.missionType) {
                "wires" -> "Ya estás en el punto. Conecta los 5 cables de colores para completarla."
                "puzzle" -> "Ya estás en el punto. Resuelve el rompecabezas 3×3 para completarla."
                else -> "Ya puedes tomar la foto dentro de la app para completar esta misión."
              }
              else ->
                "Acércate ${GeoUtils.formatDistance(remainingMeters)} más hacia el ${GeoUtils.cardinalDirectionEs(item.bearingDegrees)} (radio de ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m)."
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
          onClick = onStartMissionAction,
          enabled = item.isWithinProximity,
          shape = CircleShape,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("mission_sheet_confirm_button"),
        ) {
          Icon(
            imageVector = if (item.isWithinProximity) actionIcon else Icons.Rounded.Lock,
            contentDescription = null,
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (item.isWithinProximity) {
              when (mission.missionType) {
                "wires" -> "Jugar: Conectar cables"
                "puzzle" -> "Jugar: Rompecabezas"
                else -> if (item.isCompletedByMe) "Repetir foto en el punto" else "Confirmar con foto"
              }
            } else {
              "Acércate a menos de ${GeoUtils.PROXIMITY_RADIUS_METERS.toInt()} m"
            },
            fontWeight = FontWeight.Bold,
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedButton(
          onClick = {
            GeoUtils.openInGoogleMaps(
              context = context,
              latitude = mission.latitude,
              longitude = mission.longitude,
              title = mission.title,
            )
          },
          shape = CircleShape,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("mission_sheet_google_maps_button"),
        ) {
          Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Google Maps")
        }

        if (showExpandMapButton) {
          OutlinedButton(
            onClick = onOpenExpandedMap,
            shape = CircleShape,
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("mission_sheet_expand_map_button"),
          ) {
            Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Mapa radar")
          }
        }
      }
    }
  }
}
