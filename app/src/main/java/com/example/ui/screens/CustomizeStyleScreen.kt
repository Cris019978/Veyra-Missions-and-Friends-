package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.AnimationTierOption
import com.example.ui.theme.CornerStyleOption
import com.example.ui.theme.PaletteOption
import com.example.ui.theme.RadarStyleOption
import com.example.ui.theme.ThemeModeOption
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.PhotoUtils

@Composable
fun CustomizeStyleScreen(
  uiState: RadarQuestUiState,
  onSaveTheme: (themeMode: String, paletteKey: String, radarStyle: String, cornerStyle: String) -> Unit,
  onSaveAnimationTier: (String) -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onToggleGestureNav: (Boolean) -> Unit,
  onRequestNotificationPermission: () -> Unit,
  onSaveProfile: (callsign: String, avatarIcon: String, avatarColorHex: String, statusMessage: String, seekingHelp: Boolean) -> Unit,
  onReopenOnboardingQuest: () -> Unit,
  onSignOut: () -> Unit,
  onBack: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  val settings = uiState.settings
  val profile = uiState.myProfile

  var callsign by remember(profile?.callsign) { mutableStateOf(profile?.callsign ?: "Explorador") }
  var statusMsg by remember(profile?.statusMessage) { mutableStateOf(profile?.statusMessage ?: "Listo para misiones") }
  var seekingHelp by remember(profile?.seekingHelp) { mutableStateOf(profile?.seekingHelp ?: false) }
  var auraHex by remember(profile?.avatarColorHex) { mutableStateOf(profile?.avatarColorHex ?: "#00BFA5") }
  var showSignOutConfirmDialog by remember { mutableStateOf(false) }

  LaunchedEffect(profile?.userId) {
    if (profile != null) {
      callsign = profile.callsign
      statusMsg = profile.statusMessage
      seekingHelp = profile.seekingHelp
      auraHex = profile.avatarColorHex
    }
  }

  val palettePreviewColors = mapOf(
    "dynamic" to MaterialTheme.colorScheme.primary,
    "emerald" to Color(0xFF006B5E),
    "coral" to Color(0xFFB83A24),
    "indigo" to Color(0xFF4656C4),
    "amber" to Color(0xFF8B5000),
  )

  val auraOptions = listOf("#00BFA5", "#FF6B4A", "#5C6BC0", "#FFA726", "#EC407A", "#26C6DA")

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 680.dp)
        .fillMaxSize()
        .testTag("customize_style_list"),
      contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 16.dp,
        bottom = if (settings.gestureNavEnabled) 28.dp else 104.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "style_header") {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          if (onBack != null) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("back_from_style_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Volver a Perfil",
              )
            }
          }
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Palette,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(24.dp),
            )
          }
          Column {
            Text("Material You y Rendimiento", style = MaterialTheme.typography.headlineMedium)
            Text(
              "Colores dinámicos, navegación por gestos, animaciones y avisos",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      // 1. Material You Color & Theme Customization
      item(key = "style_theme_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
          ) {
            Text("Modo de tema", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
              ThemeModeOption.entries.forEachIndexed { idx, opt ->
                SegmentedButton(
                  selected = settings.themeMode == opt.key,
                  onClick = {
                    onSaveTheme(opt.key, settings.paletteKey, settings.radarStyle, settings.cornerStyle)
                  },
                  shape = SegmentedButtonDefaults.itemShape(idx, ThemeModeOption.entries.size),
                ) {
                  Text(opt.label)
                }
              }
            }

            Text("Paleta Material You", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(PaletteOption.entries, key = { it.key }, contentType = { "palette_swatch" }) { opt ->
                val selected = settings.paletteKey == opt.key
                val swatchColor = palettePreviewColors[opt.key] ?: MaterialTheme.colorScheme.primary
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                      onSaveTheme(settings.themeMode, opt.key, settings.radarStyle, settings.cornerStyle)
                    }
                    .padding(6.dp)
                    .testTag("palette_option_${opt.key}"),
                ) {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(swatchColor)
                      .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape,
                      ),
                    contentAlignment = Alignment.Center,
                  ) {
                    if (selected) {
                      Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(opt.label, style = MaterialTheme.typography.labelSmall)
                }
              }
            }

            Text("Estilo de bordes", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              CornerStyleOption.entries.forEach { opt ->
                FilterChip(
                  selected = settings.cornerStyle == opt.key,
                  onClick = {
                    onSaveTheme(settings.themeMode, settings.paletteKey, settings.radarStyle, opt.key)
                  },
                  shape = CircleShape,
                  label = { Text(opt.label) },
                )
              }
            }

            Text("Estilo visual del radar", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              RadarStyleOption.entries.forEach { opt ->
                FilterChip(
                  selected = settings.radarStyle == opt.key,
                  onClick = {
                    onSaveTheme(settings.themeMode, settings.paletteKey, opt.key, settings.cornerStyle)
                  },
                  shape = CircleShape,
                  label = { Text(opt.label) },
                )
              }
            }
          }
        }
      }

      // 1A. Gesture Navigation (Hide bottom bar & swipe horizontally across app)
      item(key = "style_gesture_nav_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier.weight(1f),
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Swipe,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.size(22.dp),
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Navegación por gestos", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Esconde la barra flotante inferior y desliza de izquierda a derecha para moverte por toda la app.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
              checked = settings.gestureNavEnabled,
              onCheckedChange = onToggleGestureNav,
              modifier = Modifier.testTag("gesture_nav_toggle_switch"),
            )
          }
        }
      }

      // 1B. 3-Tier Animation Optimization Selector (Low / Medium / High)
      item(key = "style_animation_tier_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Column {
                Text("Animaciones y rendimiento", style = MaterialTheme.typography.titleMedium)
                Text(
                  "Las animaciones esenciales (como el barrido del radar) siempre se mantienen activas.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }

            AnimationTierOption.entries.forEach { tier ->
              val selected = settings.animationTier == tier.key
              val tierIcon = when (tier) {
                AnimationTierOption.LOW -> Icons.Rounded.BatterySaver
                AnimationTierOption.MEDIUM -> Icons.Rounded.Speed
                AnimationTierOption.HIGH -> Icons.Rounded.AutoAwesome
              }
              Surface(
                color = if (selected) {
                  MaterialTheme.colorScheme.primaryContainer
                } else {
                  MaterialTheme.colorScheme.surfaceContainerLow
                },
                shape = MaterialTheme.shapes.medium,
                tonalElevation = if (selected) 3.dp else 0.dp,
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(MaterialTheme.shapes.medium)
                  .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                  )
                  .clickable { onSaveAnimationTier(tier.key) }
                  .testTag("animation_tier_option_${tier.key}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                  Icon(
                    imageVector = tierIcon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = tier.label,
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                      text = tier.desc,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          }
        }
      }

      // 1C. Notification Preferences
      item(key = "style_notifications_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                modifier = Modifier.weight(1f),
              ) {
                Icon(
                  imageVector = Icons.Rounded.NotificationsActive,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                  Text("Notificaciones de Veyra", style = MaterialTheme.typography.titleMedium)
                  Text(
                    "Avisos cuando entras cerca de una misión, recibes ayuda o te retan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Switch(
                checked = settings.notificationsEnabled,
                onCheckedChange = onToggleNotifications,
                modifier = Modifier.testTag("notifications_toggle_switch"),
              )
            }

            if (settings.notificationsEnabled && !uiState.hasNotificationPermission) {
              OutlinedButton(
                onClick = onRequestNotificationPermission,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Conceder permiso de notificaciones del sistema")
              }
            }
          }
        }
      }

      // 2. Player Callsign & Aura
      item(key = "style_player_aura_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text("Tu perfil en el radar", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
              value = callsign,
              onValueChange = { if (it.length <= 32) callsign = it },
              label = { Text("Nombre de jugador") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
              value = statusMsg,
              onValueChange = { if (it.length <= 140) statusMsg = it },
              label = { Text("Estado visible en el mapa") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )

            Text("Color de tu aura en el radar", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              auraOptions.forEach { hex ->
                val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                      width = if (auraHex == hex) 3.dp else 0.dp,
                      color = if (auraHex == hex) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                      shape = CircleShape,
                    )
                    .clickable { auraHex = hex },
                  contentAlignment = Alignment.Center,
                ) {
                  if (auraHex == hex) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Mostrar icono de petición de ayuda", style = MaterialTheme.typography.bodyMedium)
              Switch(checked = seekingHelp, onCheckedChange = { seekingHelp = it })
            }

            Button(
              onClick = {
                onSaveProfile(
                  callsign.trim().ifBlank { "Explorador" },
                  "explore",
                  auraHex,
                  statusMsg.trim(),
                  seekingHelp,
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_profile_button"),
            ) {
              Icon(Icons.Rounded.Save, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Guardar identidad")
            }
          }
        }
      }

      // 3. Personal Photo Proofs Gallery
      item(key = "style_gallery_header") {
        Text("Tus fotos de misiones (${uiState.myCompletions.size})", style = MaterialTheme.typography.titleMedium)
      }
      if (uiState.myCompletions.isNotEmpty()) {
        item(key = "style_gallery_row") {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(uiState.myCompletions, key = { it.id }, contentType = { "style_proof_thumb" }) { comp ->
              val bmp = remember(comp.photoThumbnail) {
                PhotoUtils.decodeBase64Thumbnail(comp.photoThumbnail)?.asImageBitmap()
              }
              Card(
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(136.dp),
              ) {
                Box(modifier = Modifier.fillMaxSize()) {
                  if (bmp != null) {
                    Image(
                      bitmap = bmp,
                      contentDescription = comp.missionTitle,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                  Box(
                    modifier = Modifier
                      .align(Alignment.BottomStart)
                      .fillMaxWidth()
                      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
                      .padding(6.dp),
                  ) {
                    Text(
                      text = comp.missionTitle,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // 4. Private Onboarding Quest Preferences
      item(key = "style_onboarding_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = "Quest de gustos (Solo visible para ti)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            Text(
              text = "Estilo: ${settings.playStyle} • Categorías favoritas: ${settings.favoriteCategories.size.coerceAtLeast(1)} • Privacidad: ${settings.privacyComfort}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
            OutlinedButton(
              onClick = onReopenOnboardingQuest,
              modifier = Modifier.testTag("reopen_onboarding_quest_button"),
            ) {
              Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Ajustar quest inicial privado")
            }
          }
        }
      }

      // 5. Saved Account Info & Sign Out Button (Always shown at bottom of Estilo)
      item(key = "style_account_signout_card") {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("local_mode_account_card"),
          ) {
            val activeHandle = profile?.username?.takeIf { it.isNotBlank() } ?: uiState.savedAccountEmail
            val activeRoleTitle = profile?.roleTitle?.takeIf { it.isNotBlank() } ?: "Explorador"
            Text(
              text = buildString {
                if (activeHandle.isNotBlank()) {
                  append("Sesión activa: @${activeHandle.removePrefix("@")} • $activeRoleTitle. ")
                } else {
                  append("Cuenta guardada localmente en este dispositivo. ")
                }
                append("Tu sesión y XP se conservan automáticamente al reabrir Veyra.")
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(14.dp),
            )
          }

          OutlinedButton(
            onClick = { showSignOutConfirmDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("sign_out_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = stringResource(id = R.string.sign_out))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(id = R.string.sign_out),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }

    if (showSignOutConfirmDialog) {
      Dialog(
        onDismissRequest = { showSignOutConfirmDialog = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier
              .widthIn(max = 420.dp)
              .fillMaxWidth()
              .testTag("sign_out_confirmation_dialog"),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Rounded.Logout,
                  contentDescription = stringResource(id = R.string.sign_out_confirm_title),
                  tint = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.size(28.dp),
                )
              }

              Text(
                text = stringResource(id = R.string.sign_out_confirm_message),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
              )

              Text(
                text = stringResource(id = R.string.sign_out_confirm_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
              )

              Spacer(modifier = Modifier.height(4.dp))

              Button(
                onClick = {
                  showSignOutConfirmDialog = false
                  onSignOut()
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.error,
                  contentColor = MaterialTheme.colorScheme.onError,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("confirm_sign_out_button"),
              ) {
                Text(
                  text = stringResource(id = R.string.sign_out_confirm_accept),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
              }

              OutlinedButton(
                onClick = { showSignOutConfirmDialog = false },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("cancel_sign_out_button"),
              ) {
                Text(
                  text = stringResource(id = R.string.sign_out_confirm_cancel),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.SemiBold,
                )
              }
            }
          }
        }
      }
    }
  }
}
