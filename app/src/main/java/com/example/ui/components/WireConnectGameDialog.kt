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
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.Mission

private data class WireColorNode(
  val id: String,
  val label: String,
  val code: String,
  val color: Color,
)

private val WIRE_NODES = listOf(
  WireColorNode("red", "Rojo", "A1", Color(0xFFEF4444)),
  WireColorNode("blue", "Azul", "B2", Color(0xFF3B82F6)),
  WireColorNode("green", "Verde", "C3", Color(0xFF10B981)),
  WireColorNode("amber", "Ámbar", "D4", Color(0xFFF59E0B)),
  WireColorNode("violet", "Violeta", "E5", Color(0xFF8B5CF6)),
)

@Composable
fun WireConnectGameDialog(
  mission: Mission,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onComplete: (caption: String, isPublic: Boolean) -> Unit,
) {
  val rightOrder = remember(mission.id) {
    val list = WIRE_NODES.shuffled()
    // Ensure it is not trivially identical order
    if (list.map { it.id } == WIRE_NODES.map { it.id }) list.reversed() else list
  }

  var selectedLeftId by remember { mutableStateOf<String?>(null) }
  val connectedIds = remember { mutableStateMapOf<String, Boolean>() }
  var mistakes by remember { mutableIntStateOf(0) }
  var statusHint by remember { mutableStateOf("Toca una terminal izquierda y conéctala con su color derecho.") }
  var shareToFeed by remember { mutableStateOf(true) }

  val leftCenters = remember { mutableStateMapOf<String, Offset>() }
  val rightCenters = remember { mutableStateMapOf<String, Offset>() }
  var boardOrigin by remember { mutableStateOf(Offset.Zero) }
  var leftColumnOffset by remember { mutableStateOf(Offset.Zero) }
  var rightColumnOffset by remember { mutableStateOf(Offset.Zero) }

  val allConnected = connectedIds.size == WIRE_NODES.size

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
        .testTag("wire_connect_game_dialog"),
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
                .background(Color(0xFF00897B).copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Cable,
                contentDescription = null,
                tint = Color(0xFF00897B),
              )
            }
            Column {
              Text(
                text = "Conectar Cables",
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
          IconButton(onClick = {
            connectedIds.clear()
            selectedLeftId = null
            mistakes = 0
            statusHint = "Tablero reiniciado. Conecta cada color."
          }) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Reiniciar cables")
          }
          IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Cerrar")
          }
        }

        // Progress + status chip
        Surface(
          color = if (allConnected) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
          shape = MaterialTheme.shapes.medium,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = if (allConnected) "¡Circuito completo! Todos los cables conectados." else statusHint,
              style = MaterialTheme.typography.bodySmall,
              color = if (allConnected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${connectedIds.size}/${WIRE_NODES.size}",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        }

        // Interactive Circuit Board
        Surface(
          color = Color(0xFF0F172A),
          shape = RoundedCornerShape(22.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .onGloballyPositioned { boardOrigin = it.positionInParent() },
        ) {
          Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            // Wire connections Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
              connectedIds.keys.forEach { id ->
                val node = WIRE_NODES.firstOrNull { it.id == id } ?: return@forEach
                val start = leftCenters[id]
                val end = rightCenters[id]
                if (start != null && end != null) {
                  val midX = (start.x + end.x) / 2f
                  val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(midX, start.y, midX, end.y, end.x, end.y)
                  }
                  drawPath(
                    path = path,
                    color = node.color.copy(alpha = 0.35f),
                    style = Stroke(width = 16f, cap = StrokeCap.Round),
                  )
                  drawPath(
                    path = path,
                    color = node.color,
                    style = Stroke(width = 8f, cap = StrokeCap.Round),
                  )
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxSize(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              // Left column terminals
              Column(
                modifier = Modifier.onGloballyPositioned { leftColumnOffset = it.positionInParent() },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
              ) {
                WIRE_NODES.forEach { node ->
                  val isConnected = connectedIds[node.id] == true
                  val isSelected = selectedLeftId == node.id
                  Surface(
                    shape = CircleShape,
                    color = node.color.copy(alpha = if (isConnected || isSelected) 1f else 0.85f),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                      .size(50.dp)
                      .border(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.45f),
                        shape = CircleShape,
                      )
                      .clip(CircleShape)
                      .onGloballyPositioned { coords ->
                        val pos = coords.positionInParent()
                        val w = coords.size.width.toFloat()
                        val h = coords.size.height.toFloat()
                        leftCenters[node.id] = Offset(
                          x = leftColumnOffset.x + pos.x + w / 2f,
                          y = leftColumnOffset.y + pos.y + h / 2f,
                        )
                      }
                      .clickable(enabled = !isConnected) {
                        selectedLeftId = node.id
                        statusHint = "Cable ${node.label} seleccionado. Toca ${node.code} a la derecha."
                      }
                      .testTag("wire_left_${node.id}")
                      .semantics { contentDescription = "Terminal izquierda ${node.label}" },
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      if (isConnected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                      } else {
                        Text(
                          text = node.code,
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.Bold,
                          color = Color.White,
                        )
                      }
                    }
                  }
                }
              }

              // Right column terminals (shuffled)
              Column(
                modifier = Modifier.onGloballyPositioned { rightColumnOffset = it.positionInParent() },
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
              ) {
                rightOrder.forEach { node ->
                  val isConnected = connectedIds[node.id] == true
                  Surface(
                    shape = CircleShape,
                    color = node.color.copy(alpha = if (isConnected) 1f else 0.85f),
                    modifier = Modifier
                      .size(50.dp)
                      .border(
                        width = 1.5.dp,
                        color = Color.White.copy(alpha = if (isConnected) 0.95f else 0.45f),
                        shape = CircleShape,
                      )
                      .clip(CircleShape)
                      .onGloballyPositioned { coords ->
                        val pos = coords.positionInParent()
                        val w = coords.size.width.toFloat()
                        val h = coords.size.height.toFloat()
                        rightCenters[node.id] = Offset(
                          x = rightColumnOffset.x + pos.x + w / 2f,
                          y = rightColumnOffset.y + pos.y + h / 2f,
                        )
                      }
                      .clickable(enabled = !isConnected && selectedLeftId != null) {
                        val activeLeft = selectedLeftId
                        if (activeLeft == node.id) {
                          connectedIds[node.id] = true
                          selectedLeftId = null
                          statusHint = if (connectedIds.size == WIRE_NODES.size) {
                            "¡Todos los cables conectados!"
                          } else {
                            "¡Cable ${node.label} conectado!"
                          }
                        } else {
                          mistakes += 1
                          selectedLeftId = null
                          statusHint = "Color incorrecto (fallos: $mistakes). Intenta de nuevo."
                        }
                      }
                      .testTag("wire_right_${node.id}")
                      .semantics { contentDescription = "Terminal derecha ${node.label}" },
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      if (isConnected) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                      } else {
                        Text(
                          text = node.code,
                          style = MaterialTheme.typography.labelLarge,
                          fontWeight = FontWeight.Bold,
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

        // Public share toggle
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
            val caption = "Cables conectados en '${mission.title}' (errores: $mistakes)"
            onComplete(caption, shareToFeed)
          },
          enabled = allConnected && !isSubmitting,
          shape = CircleShape,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("wire_complete_mission_button"),
        ) {
          if (isSubmitting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (allConnected) "Reclamar +${mission.xpReward} XP" else "Conecta los ${WIRE_NODES.size - connectedIds.size} cables restantes",
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}
