package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Mission
import kotlin.math.abs
import kotlin.random.Random

private val SOLVED_TILES = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0)

internal fun generateSolvablePuzzleBoard(seed: Int, shuffleSteps: Int = 26): List<Int> {
  val board = SOLVED_TILES.toMutableList()
  val rng = Random(seed)
  var blankIdx = 8
  var prevIdx = -1
  repeat(shuffleSteps) {
    val row = blankIdx / 3
    val col = blankIdx % 3
    val neighbors = buildList {
      if (row > 0) add(blankIdx - 3)
      if (row < 2) add(blankIdx + 3)
      if (col > 0) add(blankIdx - 1)
      if (col < 2) add(blankIdx + 1)
    }.filter { it != prevIdx }
    val next = neighbors[rng.nextInt(neighbors.size)]
    board[blankIdx] = board[next]
    board[next] = 0
    prevIdx = blankIdx
    blankIdx = next
  }
  // If shuffle accidentally returned solved, swap once legally
  if (board == SOLVED_TILES) {
    val n = blankIdx - 1
    board[blankIdx] = board[n]
    board[n] = 0
  }
  return board
}

internal fun isTileAdjacentToBlank(tileIndex: Int, blankIndex: Int): Boolean {
  val r1 = tileIndex / 3
  val c1 = tileIndex % 3
  val r2 = blankIndex / 3
  val c2 = blankIndex % 3
  return (abs(r1 - r2) + abs(c1 - c2)) == 1
}

@Composable
fun TilePuzzleGameDialog(
  mission: Mission,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onComplete: (caption: String, isPublic: Boolean) -> Unit,
) {
  var shuffleSeed by remember(mission.id) { mutableIntStateOf(mission.id.hashCode() + 17) }
  var tiles by remember(shuffleSeed) { mutableStateOf(generateSolvablePuzzleBoard(shuffleSeed)) }
  var moves by remember(shuffleSeed) { mutableIntStateOf(0) }
  var shareToFeed by remember { mutableStateOf(true) }

  val isSolved = tiles == SOLVED_TILES
  val correctCount = tiles.indices.count { idx ->
    tiles[idx] != 0 && tiles[idx] == SOLVED_TILES[idx]
  }

  val tilePalette = remember {
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

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      shape = MaterialTheme.shapes.extraLarge,
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .testTag("tile_puzzle_game_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(20.dp),
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
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF5E35B1).copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = null,
                tint = Color(0xFF5E35B1),
              )
            }
            Column {
              Text(
                text = "Rompecabezas 3×3",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = mission.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
          IconButton(onClick = { shuffleSeed += 13 }) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Mezclar de nuevo")
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
          }
        }

        Surface(
          color = if (isSolved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = if (isSolved) {
                "¡Rompecabezas resuelto en $moves movimientos!"
              } else {
                "Ordena del 1 al 8 deslizando hacia el espacio vacío"
              },
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "$correctCount/8 • Mov: $moves",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        // 3x3 Sliding Tile Grid
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            for (r in 0..2) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                for (c in 0..2) {
                  val idx = r * 3 + c
                  val value = tiles[idx]
                  val blankIdx = tiles.indexOf(0)
                  val canMove = !isSolved && value != 0 && isTileAdjacentToBlank(idx, blankIdx)
                  val inRightSpot = value != 0 && value == SOLVED_TILES[idx]

                  if (value == 0) {
                    Box(
                      modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                    )
                  } else {
                    val baseColor = tilePalette[(value - 1).coerceIn(0, tilePalette.lastIndex)]
                    Surface(
                      color = if (inRightSpot) baseColor else baseColor.copy(alpha = 0.80f),
                      shape = RoundedCornerShape(16.dp),
                      shadowElevation = if (canMove) 5.dp else 1.dp,
                      modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = canMove) {
                          val next = tiles.toMutableList()
                          next[blankIdx] = value
                          next[idx] = 0
                          tiles = next
                          moves += 1
                        }
                        .testTag("puzzle_tile_$value")
                        .semantics { contentDescription = "Pieza $value" },
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                          Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                          )
                          if (inRightSpot) {
                            Icon(
                              imageVector = Icons.Rounded.CheckCircle,
                              contentDescription = null,
                              tint = Color.White.copy(alpha = 0.85f),
                              modifier = Modifier.size(14.dp),
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
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Text("Publicar logro en Red Social", style = MaterialTheme.typography.labelLarge)
            }
            Switch(checked = shareToFeed, onCheckedChange = { shareToFeed = it })
          }
        }

        Button(
          onClick = {
            val caption = "Rompecabezas de '${mission.title}' resuelto en $moves movimientos"
            onComplete(caption, shareToFeed)
          },
          enabled = isSolved && !isSubmitting,
          shape = CircleShape,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("puzzle_complete_mission_button"),
        ) {
          if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isSolved) "Reclamar +${mission.xpReward} XP" else "Ordena todas las piezas (1–8)",
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}
