package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Mission
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class InlineWireNode(
  val id: String,
  val label: String,
  val code: String,
  val color: Color,
)

private val INLINE_WIRE_NODES = listOf(
  InlineWireNode("red", "Rojo", "A1", Color(0xFFEF4444)),
  InlineWireNode("blue", "Azul", "B2", Color(0xFF3B82F6)),
  InlineWireNode("green", "Verde", "C3", Color(0xFF10B981)),
  InlineWireNode("amber", "Ámbar", "D4", Color(0xFFF59E0B)),
  InlineWireNode("violet", "Violeta", "E5", Color(0xFF8B5CF6)),
)

private data class MemoryPad(
  val index: Int,
  val symbol: String,
  val label: String,
  val color: Color,
)

private val MEMORY_PADS = listOf(
  MemoryPad(0, "▲", "Norte", Color(0xFFFF7043)),
  MemoryPad(1, "◆", "Este", Color(0xFF29B6F6)),
  MemoryPad(2, "●", "Sur", Color(0xFF66BB6A)),
  MemoryPad(3, "★", "Pulso", Color(0xFFAB47BC)),
)

private val INLINE_SOLVED_TILES = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0)

/**
 * Interactive puzzle module embedded directly inside [MissionDetailSheet] so puzzle missions
 * resolve inside the mission's own menu at the demonstrated geographic point, supporting an
 * optional 1-minute countdown clock (timeLimitSeconds = 60).
 */
@Composable
fun InlineMissionPuzzlePanel(
  mission: Mission,
  isSubmitting: Boolean,
  onComplete: (caption: String, isPublic: Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hasTimer = mission.timeLimitSeconds > 0
  val totalSeconds = mission.timeLimitSeconds.coerceAtLeast(0)
  var attemptKey by remember(mission.id) { mutableIntStateOf(0) }
  var secondsLeft by remember(mission.id, attemptKey) { mutableIntStateOf(totalSeconds) }
  var isSolved by remember(mission.id, attemptKey) { mutableStateOf(false) }
  var isExpired by remember(mission.id, attemptKey) { mutableStateOf(false) }
  var shareToFeed by remember { mutableStateOf(true) }
  var resultSummary by remember(mission.id, attemptKey) { mutableStateOf("") }

  LaunchedEffect(mission.id, attemptKey, isSolved) {
    if (!hasTimer || isSolved) return@LaunchedEffect
    secondsLeft = totalSeconds
    isExpired = false
    while (secondsLeft > 0 && !isSolved) {
      delay(1000L)
      if (!isSolved) {
        secondsLeft -= 1
      }
    }
    if (secondsLeft <= 0 && !isSolved) {
      isExpired = true
    }
  }

  val timerColor = when {
    isSolved -> MaterialTheme.colorScheme.primary
    isExpired || (hasTimer && secondsLeft <= 15) -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.tertiary
  }

  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
    shape = RoundedCornerShape(24.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("inline_mission_puzzle_panel"),
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      // Header + Optional 1-Minute Countdown Clock
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f),
        ) {
          Icon(
            imageVector = when (mission.missionType) {
              "wires" -> Icons.Rounded.Bolt
              "memory" -> Icons.Rounded.Memory
              else -> Icons.Rounded.Extension
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
          Column {
            Text(
              text = when (mission.missionType) {
                "wires" -> "Puzzle de Cables en el Punto"
                "memory" -> "Puzzle de Secuencia Secreta"
                else -> "Rompecabezas 3×3 en el Punto"
              },
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Resuelve aquí mismo para confirmar tu llegada",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        if (hasTimer) {
          val mins = secondsLeft / 60
          val secs = secondsLeft % 60
          Surface(
            color = timerColor.copy(alpha = 0.16f),
            shape = CircleShape,
            modifier = Modifier.testTag("inline_puzzle_timer_badge"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = "Temporizador",
                tint = timerColor,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = String.format("%02d:%02d", mins, secs),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = timerColor,
              )
            }
          }
        }

        IconButton(
          onClick = { attemptKey += 1 },
          modifier = Modifier.testTag("inline_puzzle_reset_button"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Reiniciar puzzle")
        }
      }

      if (hasTimer) {
        val frac = (secondsLeft.toFloat() / totalSeconds.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { frac },
          color = timerColor,
          trackColor = timerColor.copy(alpha = 0.2f),
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape),
        )
      }

      if (isExpired && !isSolved) {
        Surface(
          color = MaterialTheme.colorScheme.errorContainer,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_puzzle_timeout_banner"),
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "¡Tiempo agotado (${totalSeconds}s)!",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
              text = "Sigues en el punto geográfico correcto. Reinicia el reloj para intentarlo de nuevo.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
            FilledTonalButton(
              onClick = { attemptKey += 1 },
              shape = CircleShape,
              colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
              ),
              modifier = Modifier.testTag("inline_puzzle_retry_button"),
            ) {
              Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Reintentar (1:00)", fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        when (mission.missionType) {
          "wires" -> InlineWireBoard(
            seed = mission.id.hashCode() + attemptKey,
            locked = isExpired,
            onSolved = { mistakes ->
              isSolved = true
              resultSummary = "Cables conectados en '${mission.title}' (errores: $mistakes)"
            },
          )

          "memory" -> InlineMemorySequenceBoard(
            seed = mission.id.hashCode() + attemptKey,
            locked = isExpired,
            onSolved = { mistakes ->
              isSolved = true
              resultSummary = "Secuencia descifrada en '${mission.title}' (errores: $mistakes)"
            },
          )

          else -> InlineSlidingTileBoard(
            seed = mission.id.hashCode() + 23 + attemptKey * 11,
            locked = isExpired,
            onSolved = { moves ->
              isSolved = true
              resultSummary = "Rompecabezas resuelto en '${mission.title}' ($moves mov.)"
            },
          )
        }
      }

      // Social share switch & Claim XP button
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Publicar logro en Red Social", style = MaterialTheme.typography.labelMedium)
          }
          Switch(checked = shareToFeed, onCheckedChange = { shareToFeed = it })
        }
      }

      Button(
        onClick = {
          val caption = resultSummary.ifBlank { "Puzzle completado en '${mission.title}'" }
          onComplete(caption, shareToFeed)
        },
        enabled = isSolved && !isSubmitting,
        shape = CircleShape,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("inline_puzzle_complete_button"),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          Icon(Icons.Rounded.CheckCircle, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (isSolved) {
              "Reclamar +${mission.xpReward} XP"
            } else {
              "Completa el puzzle dentro del menú"
            },
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
  }
}

@Composable
private fun InlineWireBoard(
  seed: Int,
  locked: Boolean,
  onSolved: (mistakes: Int) -> Unit,
) {
  val rightOrder = remember(seed) {
    val list = INLINE_WIRE_NODES.shuffled(Random(seed))
    if (list.map { it.id } == INLINE_WIRE_NODES.map { it.id }) list.reversed() else list
  }
  var selectedLeftId by remember(seed) { mutableStateOf<String?>(null) }
  val connectedIds = remember(seed) { mutableStateMapOf<String, Boolean>() }
  var mistakes by remember(seed) { mutableIntStateOf(0) }
  var hint by remember(seed) { mutableStateOf("Toca una terminal izquierda y únela con su pareja derecha.") }

  val leftCenters = remember(seed) { mutableStateMapOf<String, Offset>() }
  val rightCenters = remember(seed) { mutableStateMapOf<String, Offset>() }
  var leftColOffset by remember(seed) { mutableStateOf(Offset.Zero) }
  var rightColOffset by remember(seed) { mutableStateOf(Offset.Zero) }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = "$hint (${connectedIds.size}/${INLINE_WIRE_NODES.size})",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
      color = Color(0xFF0F172A),
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(270.dp),
    ) {
      Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          connectedIds.keys.forEach { id ->
            val node = INLINE_WIRE_NODES.firstOrNull { it.id == id } ?: return@forEach
            val start = leftCenters[id]
            val end = rightCenters[id]
            if (start != null && end != null) {
              val midX = (start.x + end.x) / 2f
              val path = Path().apply {
                moveTo(start.x, start.y)
                cubicTo(midX, start.y, midX, end.y, end.x, end.y)
              }
              drawPath(path, node.color.copy(alpha = 0.35f), style = Stroke(width = 14f, cap = StrokeCap.Round))
              drawPath(path, node.color, style = Stroke(width = 7f, cap = StrokeCap.Round))
            }
          }
        }
        Row(
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(
            modifier = Modifier.onGloballyPositioned { leftColOffset = it.positionInParent() },
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            INLINE_WIRE_NODES.forEach { node ->
              val isConnected = connectedIds[node.id] == true
              val isSelected = selectedLeftId == node.id
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(node.color)
                  .border(if (isSelected) 3.dp else 1.5.dp, Color.White, CircleShape)
                  .onGloballyPositioned { coords ->
                    val pos = coords.positionInParent()
                    leftCenters[node.id] = Offset(
                      leftColOffset.x + pos.x + coords.size.width / 2f,
                      leftColOffset.y + pos.y + coords.size.height / 2f,
                    )
                  }
                  .clickable(enabled = !locked && !isConnected) {
                    selectedLeftId = node.id
                    hint = "Seleccionado ${node.label}: toca ${node.code} a la derecha"
                  }
                  .testTag("inline_wire_left_${node.id}"),
                contentAlignment = Alignment.Center,
              ) {
                if (isConnected) {
                  Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                  Text(node.code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
            }
          }

          Column(
            modifier = Modifier.onGloballyPositioned { rightColOffset = it.positionInParent() },
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            rightOrder.forEach { node ->
              val isConnected = connectedIds[node.id] == true
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(node.color)
                  .border(1.5.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                  .onGloballyPositioned { coords ->
                    val pos = coords.positionInParent()
                    rightCenters[node.id] = Offset(
                      rightColOffset.x + pos.x + coords.size.width / 2f,
                      rightColOffset.y + pos.y + coords.size.height / 2f,
                    )
                  }
                  .clickable(enabled = !locked && !isConnected && selectedLeftId != null) {
                    if (selectedLeftId == node.id) {
                      connectedIds[node.id] = true
                      selectedLeftId = null
                      if (connectedIds.size == INLINE_WIRE_NODES.size) {
                        hint = "¡Circuito completo!"
                        onSolved(mistakes)
                      } else {
                        hint = "¡${node.label} conectado!"
                      }
                    } else {
                      mistakes += 1
                      selectedLeftId = null
                      hint = "Cable incorrecto (fallos: $mistakes)"
                    }
                  }
                  .testTag("inline_wire_right_${node.id}"),
                contentAlignment = Alignment.Center,
              ) {
                if (isConnected) {
                  Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                  Text(node.code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InlineSlidingTileBoard(
  seed: Int,
  locked: Boolean,
  onSolved: (moves: Int) -> Unit,
) {
  var tiles by remember(seed) { mutableStateOf(generateSolvablePuzzleBoard(seed, shuffleSteps = 18)) }
  var moves by remember(seed) { mutableIntStateOf(0) }
  val isSolved = tiles == INLINE_SOLVED_TILES
  val palette = remember {
    listOf(
      Color(0xFF5E35B1),
      Color(0xFF3949AB),
      Color(0xFF1E88E5),
      Color(0xFF00897B),
      Color(0xFF43A047),
      Color(0xFFFB8C00),
      Color(0xFFE53935),
      Color(0xFF8E24AA),
    )
  }

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val correctCount = tiles.indices.count { i -> tiles[i] != 0 && tiles[i] == INLINE_SOLVED_TILES[i] }
    Text(
      text = if (isSolved) "¡Ordenado en $moves movimientos!" else "Ordena del 1 al 8 ($correctCount/8 • Mov: $moves)",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      shape = RoundedCornerShape(18.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        for (r in 0..2) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            for (c in 0..2) {
              val idx = r * 3 + c
              val value = tiles[idx]
              val blankIdx = tiles.indexOf(0)
              val canMove = !locked && !isSolved && value != 0 && isTileAdjacentToBlank(idx, blankIdx)
              val inRightSpot = value != 0 && value == INLINE_SOLVED_TILES[idx]

              if (value == 0) {
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                )
              } else {
                val baseColor = palette[(value - 1).coerceIn(0, palette.lastIndex)]
                Box(
                  modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (inRightSpot) baseColor else baseColor.copy(alpha = 0.82f))
                    .clickable(enabled = canMove) {
                      val next = tiles.toMutableList()
                      next[blankIdx] = value
                      next[idx] = 0
                      tiles = next
                      moves += 1
                      if (next == INLINE_SOLVED_TILES) {
                        onSolved(moves)
                      }
                    }
                    .testTag("inline_puzzle_tile_$value")
                    .semantics { contentDescription = "Pieza $value" },
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = value.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InlineMemorySequenceBoard(
  seed: Int,
  locked: Boolean,
  onSolved: (mistakes: Int) -> Unit,
) {
  val targetSequence = remember(seed) {
    val rng = Random(seed)
    listOf(0, 1, 2, 3).shuffled(rng) + listOf(rng.nextInt(4))
  }
  val entered = remember(seed) { mutableStateListOf<Int>() }
  var mistakes by remember(seed) { mutableIntStateOf(0) }
  var completed by remember(seed) { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      text = if (completed) {
        "¡Secuencia de 5 símbolos descifrada!"
      } else {
        "Pulsa la secuencia en orden exacto (${entered.size}/${targetSequence.size}):"
      },
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // Pattern prompt strip
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      targetSequence.forEachIndexed { stepIdx, padIdx ->
        val pad = MEMORY_PADS[padIdx]
        val hit = stepIdx < entered.size
        Surface(
          color = if (hit) pad.color else pad.color.copy(alpha = 0.22f),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.weight(1f),
        ) {
          Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = pad.symbol,
              style = MaterialTheme.typography.titleMedium,
              color = if (hit) Color.White else pad.color,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "#${stepIdx + 1}",
              style = MaterialTheme.typography.labelSmall,
              color = if (hit) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }

    // Interactive 2x2 Pad Grid
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(0..1, 2..3).forEach { range ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          range.forEach { padIdx ->
            val pad = MEMORY_PADS[padIdx]
            Surface(
              color = pad.color,
              shape = RoundedCornerShape(16.dp),
              modifier = Modifier
                .weight(1f)
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = !locked && !completed) {
                  val expected = targetSequence[entered.size]
                  if (padIdx == expected) {
                    entered.add(padIdx)
                    if (entered.size == targetSequence.size) {
                      completed = true
                      onSolved(mistakes)
                    }
                  } else {
                    mistakes += 1
                    entered.clear()
                  }
                }
                .testTag("inline_memory_pad_$padIdx"),
            ) {
              Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                Text(pad.symbol, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text(pad.label, style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }
}
