package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissionCategory
import com.example.data.model.UserSettings
import com.example.ui.theme.PaletteOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingQuestOverlay(
  initialSettings: UserSettings,
  onCompleteQuest: (
    playStyle: String,
    favoriteCategories: List<String>,
    privacyComfort: String,
    missionPace: String,
    paletteKey: String,
  ) -> Unit,
  onPreviewPalette: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var step by remember { mutableIntStateOf(0) }
  var playStyle by remember(initialSettings.playStyle) {
    mutableStateOf(initialSettings.playStyle.ifBlank { "balanced" })
  }
  val selectedCategories = remember(initialSettings.favoriteCategories) {
    mutableStateListOf<String>().apply {
      val init = initialSettings.favoriteCategories.ifEmpty { listOf("explore", "eco") }
      addAll(init.distinct())
    }
  }
  var missionPace by remember(initialSettings.missionPace) {
    mutableStateOf(initialSettings.missionPace.ifBlank { "casual" })
  }
  var privacyComfort by remember(initialSettings.privacyComfort) {
    mutableStateOf(initialSettings.privacyComfort.ifBlank { "radius" })
  }
  var paletteKey by remember(initialSettings.paletteKey) {
    mutableStateOf(initialSettings.paletteKey.ifBlank { "dynamic" })
  }

  BackHandler(enabled = step > 0) {
    step -= 1
  }

  Surface(
    modifier = modifier
      .fillMaxSize()
      .testTag("onboarding_quest_overlay"),
    color = MaterialTheme.colorScheme.background,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
              MaterialTheme.colorScheme.background,
              MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f),
            )
          )
        )
        .safeDrawingPadding(),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Header + Private Badge
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(
              text = "Quest inicial de Veyra",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Paso ${step + 1} de 3 • Ajusta tu aventura",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Solo visible para ti",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
              )
              Text(
                text = "Solo para ti",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        LinearProgressIndicator(
          progress = { (step + 1) / 3f },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape),
        )

        // Privacy Assurance Banner
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Shield,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = "Privacidad garantizada: tus gustos y respuestas de este quest se guardan solo en tu cuenta y nunca son visibles para otros jugadores.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        AnimatedContent(
          targetState = step,
          transitionSpec = {
            if (targetState > initialState) {
              (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(180)))
            } else {
              (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(240))) togetherWith
                (slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(180)))
            }
          },
          label = "onboardingStep",
        ) { currentStep ->
          when (currentStep) {
            0 -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = "1. ¿Cómo funciona Veyra?",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                ConceptPillCard(
                  icon = Icons.Rounded.Radar,
                  title = "Mapa radar redondo en vivo",
                  desc = "Ve amigos y misiones cercanas dentro de tu radio sin compartir rutas privadas.",
                )
                ConceptPillCard(
                  icon = Icons.Rounded.CameraAlt,
                  title = "Misiones reales confirmadas con foto",
                  desc = "Explora categorías del mundo real y verifica tu logro tomando una foto dentro de la app.",
                )
                ConceptPillCard(
                  icon = Icons.Rounded.Handshake,
                  title = "Ayuda, compite y juega",
                  desc = "Envía impulsos de energía, reta en duelos fotográficos o únete en cooperativo.",
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "¿Cuál será tu rol favorito?",
                  style = MaterialTheme.typography.titleMedium,
                )

                val styles = listOf(
                  Triple("helper", "Ayudar y cooperar", "Apoyar a otros jugadores cercanos"),
                  Triple("competitor", "Competir en duelos", "Ganar retos y subir en el ranking"),
                  Triple("explorer", "Explorar a mi ritmo", "Descubrir lugares y misiones foto"),
                  Triple("balanced", "Un poco de todo", "Mezcla equilibrada de misiones y amigos"),
                )
                styles.forEach { (key, title, subtitle) ->
                  val selected = playStyle == key
                  SelectableQuestCard(
                    selected = selected,
                    icon = when (key) {
                      "helper" -> Icons.Rounded.VolunteerActivism
                      "competitor" -> Icons.Rounded.EmojiEvents
                      "explorer" -> Icons.Rounded.Explore
                      else -> Icons.Rounded.AutoAwesome
                    },
                    title = title,
                    subtitle = subtitle,
                    onClick = { playStyle = key },
                    modifier = Modifier.testTag("quest_style_$key"),
                  )
                }
              }
            }

            1 -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = "2. Elige tus gustos de misiones",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Usaremos esto en privado para destacarte primero las categorías que más disfrutas.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  MissionCategory.entries.forEach { cat ->
                    val selected = cat.key in selectedCategories
                    FilterChip(
                      selected = selected,
                      onClick = {
                        if (selected) {
                          if (selectedCategories.size > 1) selectedCategories.remove(cat.key)
                        } else {
                          selectedCategories.add(cat.key)
                        }
                      },
                      label = { Text(cat.label) },
                      leadingIcon = {
                        Icon(
                          imageVector = categoryIconFor(cat.key),
                          contentDescription = null,
                          modifier = Modifier.size(18.dp),
                        )
                      },
                      modifier = Modifier.testTag("quest_cat_${cat.key}"),
                    )
                  }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "¿Con qué ritmo prefieres recibir misiones?",
                  style = MaterialTheme.typography.titleMedium,
                )
                val paces = listOf(
                  Triple("casual", "Ritmo relajado", "Misiones tranquilas cuando salgo a caminar"),
                  Triple("daily", "Reto diario", "1 o 2 misiones rápidas cada día"),
                  Triple("intense", "Modo leyenda", "Misiones seguidas, duelos y rachas"),
                )
                paces.forEach { (key, title, subtitle) ->
                  SelectableQuestCard(
                    selected = missionPace == key,
                    icon = Icons.AutoMirrored.Rounded.DirectionsRun,
                    title = title,
                    subtitle = subtitle,
                    onClick = { missionPace = key },
                    modifier = Modifier.testTag("quest_pace_$key"),
                  )
                }
              }
            }

            else -> {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                  text = "3. Comodidad en radar y Material You",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  text = "Define cómo prefieres aparecer en el mapa y elige tu paleta inicial.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val comfortOptions = listOf(
                  Triple("radius", "Radar activo en mi zona", "Visible para conectar dentro de tu radio elegido"),
                  Triple("close_only", "Solo muy cerca (500 m)", "Limita el radar a jugadores a pocos pasos"),
                  Triple("ghost", "Empezar en modo discreto", "Explora el mapa sin transmitir tu punto todavía"),
                )
                comfortOptions.forEach { (key, title, subtitle) ->
                  SelectableQuestCard(
                    selected = privacyComfort == key,
                    icon = Icons.Rounded.Radar,
                    title = title,
                    subtitle = subtitle,
                    onClick = { privacyComfort = key },
                    modifier = Modifier.testTag("quest_privacy_$key"),
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Tu color Material You inicial",
                  style = MaterialTheme.typography.titleMedium,
                )
                val swatches = mapOf(
                  "dynamic" to MaterialTheme.colorScheme.primary,
                  "emerald" to Color(0xFF006B5E),
                  "coral" to Color(0xFFB83A24),
                  "indigo" to Color(0xFF4656C4),
                  "amber" to Color(0xFF8B5000),
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  PaletteOption.entries.forEach { opt ->
                    val isSel = paletteKey == opt.key
                    Column(
                      horizontalAlignment = Alignment.CenterHorizontally,
                      modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                          paletteKey = opt.key
                          onPreviewPalette(opt.key)
                        }
                        .padding(6.dp),
                    ) {
                      Box(
                        modifier = Modifier
                          .size(46.dp)
                          .clip(CircleShape)
                          .background(swatches[opt.key] ?: MaterialTheme.colorScheme.primary)
                          .border(
                            width = if (isSel) 3.dp else 0.dp,
                            color = if (isSel) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                          ),
                        contentAlignment = Alignment.Center,
                      ) {
                        if (isSel) {
                          Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                        }
                      }
                      Spacer(modifier = Modifier.height(4.dp))
                      Text(opt.label, style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          if (step > 0) {
            OutlinedButton(
              onClick = { step -= 1 },
              shape = CircleShape,
              modifier = Modifier
                .weight(1f)
                .height(50.dp),
            ) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Atrás")
            }
          }

          Button(
            onClick = {
              if (step < 2) {
                step += 1
              } else {
                onCompleteQuest(
                  playStyle,
                  selectedCategories.toList().ifEmpty { listOf("explore") },
                  privacyComfort,
                  missionPace,
                  paletteKey,
                )
              }
            },
            shape = CircleShape,
            modifier = Modifier
              .weight(1.4f)
              .height(50.dp)
              .testTag("onboarding_next_or_finish_button"),
          ) {
            Text(
              text = if (step < 2) "Continuar" else "Comenzar en Veyra",
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = if (step < 2) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check,
              contentDescription = null,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ConceptPillCard(
  icon: ImageVector,
  title: String,
  desc: String,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
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
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.size(20.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun SelectableQuestCard(
  selected: Boolean,
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
    shape = MaterialTheme.shapes.large,
    modifier = modifier
      .fillMaxWidth()
      .border(
        width = if (selected) 2.dp else 0.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        shape = MaterialTheme.shapes.large,
      )
      .clip(MaterialTheme.shapes.large)
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp),
        )
      }
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
      }
      if (selected) {
        Icon(
          imageVector = Icons.Rounded.Check,
          contentDescription = "Seleccionado",
          tint = MaterialTheme.colorScheme.primary,
        )
      }
    }
  }
}

private fun categoryIconFor(key: String): ImageVector = when (key) {
  "eco" -> Icons.Rounded.LocalFlorist
  "social" -> Icons.Rounded.VolunteerActivism
  "challenge" -> Icons.Rounded.CameraAlt
  "fitness" -> Icons.AutoMirrored.Rounded.DirectionsRun
  "creative" -> Icons.Rounded.Brush
  else -> Icons.Rounded.Explore
}
