package com.example.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DailyStreakState
import com.example.data.local.StreakDayDot
import com.example.data.model.PlayerProfile
import com.example.ui.components.RoleTitleBadge
import com.example.ui.viewmodel.RadarQuestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakScreen(
  uiState: RadarQuestUiState,
  currentUserId: String,
  onRefresh: () -> Unit = {},
  onGoToRadar: () -> Unit = {},
  onGoToSocialFeed: () -> Unit = {},
  onCheerPlayer: (PlayerProfile) -> Unit = {},
  onPulsePlayer: (PlayerProfile) -> Unit = {},
  modifier: Modifier = Modifier,
) {
  val streak = uiState.dailyStreak
  val disableAnimations = uiState.settings.animationTier == "low"
  val communityPeers = buildList {
    val seenIds = mutableSetOf<String>()
    uiState.leaderboardPlayers.forEach { player ->
      if (player.userId != currentUserId && seenIds.add(player.userId)) {
        add(player)
      }
    }
    uiState.nearbyPlayers.forEach { item ->
      if (item.profile.userId != currentUserId && seenIds.add(item.profile.userId)) {
        add(item.profile)
      }
    }
  }.take(12)

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
          .fillMaxSize()
          .testTag("streak_screen_list"),
        contentPadding = PaddingValues(
          start = 16.dp,
          end = 16.dp,
          top = 16.dp,
          bottom = if (uiState.settings.gestureNavEnabled) 28.dp else 104.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        item(key = "streak_header") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Racha Diaria",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Completa al menos 1 misión cada día y motiva a otros exploradores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Surface(
              color = MaterialTheme.colorScheme.tertiaryContainer,
              shape = RoundedCornerShape(50),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  imageVector = Icons.Rounded.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onTertiaryContainer,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text = "Boost ${streak.xpBoostLabel}",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
              }
            }
          }
        }

        item(key = "streak_hero") {
          StreakHeroCard(
            streak = streak,
            disableAnimations = disableAnimations,
            onGoToRadar = onGoToRadar,
            onGoToSocialFeed = onGoToSocialFeed,
          )
        }

        item(key = "streak_stats_row") {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            StreakMetricTile(
              icon = Icons.Rounded.EmojiEvents,
              label = "Mejor racha",
              value = "${streak.bestStreakDays}d",
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier
                .weight(1f)
                .testTag("streak_best_stat_card"),
            )
            StreakMetricTile(
              icon = Icons.Rounded.Today,
              label = "Hoy",
              value = "${streak.missionsCompletedToday}",
              containerColor = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier
                .weight(1f)
                .testTag("streak_today_stat_card"),
            )
            StreakMetricTile(
              icon = Icons.Rounded.CalendarMonth,
              label = "Días activos",
              value = "${streak.totalActiveDays}",
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier
                .weight(1f)
                .testTag("streak_total_days_card"),
            )
          }
        }

        if (streak.recentSevenDays.isNotEmpty()) {
          item(key = "streak_week_card") {
            StreakWeekStripCard(days = streak.recentSevenDays)
          }
        }

        item(key = "streak_social_header") {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
              text = "Impulso Social",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Anima a exploradores cercanos para mantener la racha viva (+15 XP)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        uiState.myProfile?.let { me ->
          item(key = "streak_my_summary") {
            MyStreakSocialCard(
              profile = me,
              streak = streak,
            )
          }
        }

        if (communityPeers.isEmpty()) {
          item(key = "streak_empty_social") {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
              shape = MaterialTheme.shapes.large,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("streak_empty_social_card"),
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                Text(
                  text = "Sin exploradores conectados aún",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
                Text(
                  text = "Cuando otros jugadores aparezcan en tu radar o clasificación, podrás enviarles ánimos y pulsos desde aquí.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        } else {
          items(
            items = communityPeers,
            key = { "streak_peer_${it.userId}" },
            contentType = { "streak_peer_card" },
          ) { peer ->
            CommunityStreakPeerCard(
              player = peer,
              onCheer = { onCheerPlayer(peer) },
              onPulse = { onPulsePlayer(peer) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun StreakHeroCard(
  streak: DailyStreakState,
  disableAnimations: Boolean,
  onGoToRadar: () -> Unit,
  onGoToSocialFeed: () -> Unit,
) {
  val flameScale = if (disableAnimations || !streak.completedToday) {
    1f
  } else {
    val transition = rememberInfiniteTransition(label = "streakFlame")
    val scale by transition.animateFloat(
      initialValue = 0.96f,
      targetValue = 1.07f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1200),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "streakFlameScale",
    )
    scale
  }

  val dayLabel = if (streak.currentStreakDays == 1) "día consecutivo" else "días consecutivos"
  val prevMilestone = when (streak.nextMilestoneDays) {
    3 -> 0
    7 -> 3
    14 -> 7
    30 -> 14
    50 -> 30
    100 -> 50
    else -> (streak.nextMilestoneDays - 50).coerceAtLeast(0)
  }
  val span = (streak.nextMilestoneDays - prevMilestone).coerceAtLeast(1)
  val milestoneProgress = ((streak.currentStreakDays - prevMilestone).toFloat() / span.toFloat())
    .coerceIn(0f, 1f)

  val containerBrush = if (streak.completedToday) {
    Brush.linearGradient(
      listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
      )
    )
  } else {
    Brush.linearGradient(
      listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
      )
    )
  }

  Card(
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    border = BorderStroke(
      width = 1.dp,
      color = if (streak.completedToday) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
      } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
      },
    ),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("streak_hero_card"),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .background(containerBrush)
        .padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .scale(flameScale)
            .clip(CircleShape)
            .background(
              if (streak.completedToday) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
              }
            ),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.LocalFireDepartment,
            contentDescription = "Icono de racha diaria",
            tint = if (streak.completedToday) {
              MaterialTheme.colorScheme.onPrimary
            } else {
              MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(34.dp),
          )
        }

        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = "${streak.currentStreakDays}",
              style = MaterialTheme.typography.displaySmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("streak_current_days_text"),
            )
            Text(
              text = dayLabel,
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 5.dp),
            )
          }

          Surface(
            color = if (streak.completedToday) {
              MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
              MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.testTag("streak_status_badge"),
          ) {
            Text(
              text = if (streak.completedToday) {
                "¡Racha asegurada hoy! (${streak.missionsCompletedToday} hoy)"
              } else {
                "Completa 1 misión hoy para sumar racha"
              },
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (streak.completedToday) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onErrorContainer
              },
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "Siguiente meta: ${streak.nextMilestoneDays} días",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "${streak.currentStreakDays}/${streak.nextMilestoneDays}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        LinearProgressIndicator(
          progress = { milestoneProgress },
          strokeCap = StrokeCap.Round,
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Button(
          onClick = if (streak.completedToday) onGoToSocialFeed else onGoToRadar,
          shape = CircleShape,
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("streak_primary_cta_button"),
        ) {
          Icon(
            imageVector = if (streak.completedToday) Icons.Rounded.Share else Icons.Rounded.Explore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (streak.completedToday) "Ver Red Social" else "Explorar misiones en Radar",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun StreakMetricTile(
  icon: ImageVector,
  label: String,
  value: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = containerColor),
    shape = MaterialTheme.shapes.large,
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(20.dp),
      )
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = contentColor,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor.copy(alpha = 0.82f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun StreakWeekStripCard(days: List<StreakDayDot>) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.extraLarge,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("streak_week_strip"),
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = "Últimos 7 días",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        days.forEach { dot ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("streak_day_dot_${dot.dayKey}"),
          ) {
            Text(
              text = dot.dayInitial,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (dot.isToday) FontWeight.Bold else FontWeight.Medium,
              color = if (dot.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
              modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                  when {
                    dot.isCompleted -> MaterialTheme.colorScheme.primary
                    dot.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                  }
                )
                .then(
                  if (dot.isToday) {
                    Modifier.border(
                      width = 2.dp,
                      color = MaterialTheme.colorScheme.primary,
                      shape = CircleShape,
                    )
                  } else {
                    Modifier
                  }
                ),
              contentAlignment = Alignment.Center,
            ) {
              if (dot.isCompleted) {
                Icon(
                  imageVector = Icons.Rounded.Check,
                  contentDescription = "Día ${dot.dayNumber} completado",
                  tint = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.size(18.dp),
                )
              } else {
                Text(
                  text = dot.dayNumber,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (dot.isToday) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MyStreakSocialCard(
  profile: PlayerProfile,
  streak: DailyStreakState,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = Icons.Rounded.LocalFireDepartment,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimary,
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "${profile.callsign} (Tú)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          RoleTitleBadge(role = profile.role, roleTitle = profile.roleTitle)
        }
        Text(
          text = "Racha ${streak.currentStreakDays}d • ${profile.missionsCompleted} misiones • ${profile.xp} XP",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun CommunityStreakPeerCard(
  player: PlayerProfile,
  onCheer: () -> Unit,
  onPulse: () -> Unit,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = player.callsign.ifBlank { player.username.ifBlank { "Explorador" } },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          RoleTitleBadge(role = player.role, roleTitle = player.roleTitle)
        }
        Text(
          text = "Nvl ${player.level} • ${player.missionsCompleted} misiones • ${player.xp} XP",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      FilledTonalButton(
        onClick = onCheer,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("streak_cheer_player_${player.userId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Favorite,
          contentDescription = "Animar a ${player.callsign}",
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Animar", maxLines = 1)
      }

      FilledTonalButton(
        onClick = onPulse,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier
          .minimumInteractiveComponentSize()
          .testTag("streak_pulse_player_${player.userId}"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Bolt,
          contentDescription = "Enviar pulso a ${player.callsign}",
          modifier = Modifier.size(16.dp),
        )
      }
    }
  }
}
