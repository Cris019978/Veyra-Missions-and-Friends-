package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Challenge
import com.example.ui.viewmodel.RadarQuestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialArenaScreen(
  uiState: RadarQuestUiState,
  currentUserId: String,
  onRefresh: () -> Unit = {},
  onCreateChallenge: (mode: String, title: String, missionId: String, xpStake: Long) -> Unit,
  onJoinChallenge: (Challenge) -> Unit,
  onAdvanceChallenge: (Challenge) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedSection by remember { mutableIntStateOf(0) }
  var showNewChallengeDialog by remember { mutableStateOf(false) }

  PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = onRefresh,
    modifier = modifier.fillMaxSize(),
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
        item(key = "arena_header") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column {
              Text(
                text = "Arena Social",
                style = MaterialTheme.typography.headlineMedium,
              )
              Text(
                text = "Ayuda, compite y juega en vivo con otros",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            FilledTonalButton(
              onClick = { showNewChallengeDialog = true },
              shape = CircleShape,
              modifier = Modifier.testTag("create_challenge_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Nuevo reto")
            }
          }
        }

      item(key = "arena_section_selector") {
        val sections = listOf("Retos", "Ayudas", "Ranking")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          sections.forEachIndexed { idx, label ->
            SegmentedButton(
              selected = selectedSection == idx,
              onClick = { selectedSection = idx },
              shape = SegmentedButtonDefaults.itemShape(index = idx, count = sections.size),
            ) {
              Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }

      when (selectedSection) {
        0 -> {
          item(key = "my_challenges_header") {
            Text("Tus duelos y partidas co-op", style = MaterialTheme.typography.titleMedium)
          }
          if (uiState.myChallenges.isEmpty()) {
            item(key = "my_challenges_empty") {
              EmptySocialCard(
                title = "Sin retos activos",
                subtitle = "Crea un duelo competitivo o únete a un reto abierto abajo.",
              )
            }
          } else {
            items(uiState.myChallenges, key = { "my_${it.id}" }, contentType = { "challenge_card" }) { chal ->
              ChallengeCard(
                challenge = chal,
                currentUserId = currentUserId,
                onJoin = { onJoinChallenge(chal) },
                onAdvance = { onAdvanceChallenge(chal) },
              )
            }
          }

          item(key = "open_challenges_header") {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Retos abiertos en la comunidad", style = MaterialTheme.typography.titleMedium)
          }
          if (uiState.openChallenges.isEmpty()) {
            item(key = "open_challenges_empty") {
              EmptySocialCard(
                title = "No hay retos abiertos ahora",
                subtitle = "Pulsa 'Nuevo reto' para invitar a jugadores cercanos a competir o jugar.",
              )
            }
          } else {
            items(uiState.openChallenges, key = { "open_${it.id}" }, contentType = { "challenge_card" }) { chal ->
              ChallengeCard(
                challenge = chal,
                currentUserId = currentUserId,
                onJoin = { onJoinChallenge(chal) },
                onAdvance = { onAdvanceChallenge(chal) },
              )
            }
          }
        }

        1 -> {
          item(key = "interactions_header") {
            Text("Ayudas y pulsos en tiempo real", style = MaterialTheme.typography.titleMedium)
          }
          if (uiState.interactions.isEmpty()) {
            item(key = "interactions_empty") {
              EmptySocialCard(
                title = "Aún no hay ayudas registradas",
                subtitle = "Toca a cualquier jugador en el mapa radar para enviarle ayuda o un pulso de juego.",
              )
            }
          } else {
            items(uiState.interactions, key = { it.id }, contentType = { "interaction_card" }) { inter ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(40.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = if (inter.type == "help") Icons.Rounded.Handshake else Icons.Rounded.Bolt,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${inter.fromUserName} → ${inter.toUserName}",
                      style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                      text = inter.message,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(50),
                  ) {
                    Text(
                      text = "+${inter.xpBonus} XP",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onTertiaryContainer,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                  }
                }
              }
            }
          }
        }

        2 -> {
          item(key = "leaderboard_header") {
            Text("Clasificación en línea", style = MaterialTheme.typography.titleMedium)
          }
          itemsIndexed(
            items = uiState.leaderboardPlayers,
            key = { _, p -> p.userId },
            contentType = { _, _ -> "leaderboard_row" },
          ) { index, player ->
            val rank = index + 1
            Card(
              colors = CardDefaults.cardColors(
                containerColor = if (player.userId == currentUserId) {
                  MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                  MaterialTheme.colorScheme.surfaceContainer
                },
              ),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                Text(
                  text = "#$rank",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(player.callsign, style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = "Nv ${player.level} • ${player.missionsCompleted} misiones • ${player.helpsGiven} ayudas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Text(
                  text = "${player.xp} XP",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
    }
  }

  if (showNewChallengeDialog) {
    CreateChallengeDialog(
      defaultMissionId = uiState.missions.firstOrNull()?.mission?.id ?: "quest_eco_tree",
      onDismiss = { showNewChallengeDialog = false },
      onCreate = { mode, title, missionId, stake ->
        onCreateChallenge(mode, title, missionId, stake)
        showNewChallengeDialog = false
      },
    )
  }
}

@Composable
private fun ChallengeCard(
  challenge: Challenge,
  currentUserId: String,
  onJoin: () -> Unit,
  onAdvance: () -> Unit,
) {
  val isParticipant = currentUserId in challenge.participantIds
  val isDuel = challenge.mode == "duel"
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
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
        ) {
          Icon(
            imageVector = if (isDuel) Icons.Rounded.EmojiEvents else Icons.Rounded.SportsEsports,
            contentDescription = null,
            tint = if (isDuel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = if (isDuel) "DUELO COMPETITIVO" else "MODO COOPERATIVO",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(challenge.title, style = MaterialTheme.typography.titleMedium)
          }
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = "+${challenge.xpStake} XP",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          )
        }
      }

      Text(
        text = "Creado por ${challenge.creatorName}" +
          if (challenge.opponentName.isNotBlank()) " vs ${challenge.opponentName}" else " • Esperando jugador",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (challenge.status == "active" || challenge.status == "completed") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = "${challenge.creatorName}: ${challenge.creatorProgress}%",
            style = MaterialTheme.typography.labelSmall,
          )
          LinearProgressIndicator(
            progress = { challenge.creatorProgress / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(CircleShape),
          )
          if (challenge.opponentName.isNotBlank()) {
            Text(
              text = "${challenge.opponentName}: ${challenge.opponentProgress}%",
              style = MaterialTheme.typography.labelSmall,
            )
            LinearProgressIndicator(
              progress = { challenge.opponentProgress / 100f },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        when {
          challenge.status == "open" && !isParticipant -> {
            Button(onClick = onJoin) {
              Text(if (isDuel) "Aceptar duelo" else "Unirse al equipo")
            }
          }
          challenge.status == "active" && isParticipant -> {
            Button(onClick = onAdvance) {
              Text("Registrar avance (+50%)")
            }
          }
          challenge.status == "completed" -> {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = CircleShape,
            ) {
              Text(
                text = "Finalizado",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun EmptySocialCard(title: String, subtitle: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Icon(Icons.Rounded.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun CreateChallengeDialog(
  defaultMissionId: String,
  onDismiss: () -> Unit,
  onCreate: (mode: String, title: String, missionId: String, xpStake: Long) -> Unit,
) {
  var title by remember { mutableStateOf("") }
  var mode by remember { mutableStateOf("duel") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Crear reto multijugador") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = mode == "duel",
            onClick = { mode = "duel" },
            shape = CircleShape,
            label = { Text("Competir (Duelo)") },
          )
          FilterChip(
            selected = mode == "coop",
            onClick = { mode = "coop" },
            shape = CircleShape,
            label = { Text("Jugar Co-op") },
          )
        }
        OutlinedTextField(
          value = title,
          onValueChange = { if (it.length <= 80) title = it },
          label = { Text("Nombre del reto") },
          placeholder = { Text("Ej. Carrera foto del parque") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onCreate(mode, title.trim(), defaultMissionId, 180L) },
        enabled = title.trim().length >= 3,
      ) {
        Text("Publicar (+180 XP)")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancelar") }
    },
  )
}
