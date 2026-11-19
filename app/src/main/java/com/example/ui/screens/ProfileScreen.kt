package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.RoleTitleBadge
import com.example.ui.theme.PaletteOption
import com.example.ui.viewmodel.RadarQuestUiState
import com.example.util.PhotoUtils

@Composable
fun ProfileScreen(
  uiState: RadarQuestUiState,
  onSaveProfile: (
    username: String,
    callsign: String,
    avatarIcon: String,
    avatarColorHex: String,
    avatarPhotoBase64: String,
    bannerPhotoBase64: String,
    bannerPreset: String,
    statusMessage: String,
    seekingHelp: Boolean,
  ) -> Unit,
  onQuickSelectPalette: (String) -> Unit,
  onOpenStyleCustomization: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val profile = uiState.myProfile

  var username by remember(profile?.username) { mutableStateOf(profile?.username ?: "") }
  var callsign by remember(profile?.callsign) { mutableStateOf(profile?.callsign ?: "Explorador") }
  var statusMsg by remember(profile?.statusMessage) { mutableStateOf(profile?.statusMessage ?: "Listo para misiones") }
  var seekingHelp by remember(profile?.seekingHelp) { mutableStateOf(profile?.seekingHelp ?: false) }
  var avatarIcon by remember(profile?.avatarIcon) { mutableStateOf(profile?.avatarIcon ?: "explore") }
  var auraHex by remember(profile?.avatarColorHex) { mutableStateOf(profile?.avatarColorHex ?: "#00BFA5") }
  var avatarPhotoBase64 by remember(profile?.avatarPhotoBase64) { mutableStateOf(profile?.avatarPhotoBase64 ?: "") }
  var bannerPhotoBase64 by remember(profile?.bannerPhotoBase64) { mutableStateOf(profile?.bannerPhotoBase64 ?: "") }
  var bannerPreset by remember(profile?.bannerPreset) { mutableStateOf(profile?.bannerPreset ?: "topo") }

  LaunchedEffect(profile?.userId, profile?.updatedAt) {
    if (profile != null) {
      if (username.isBlank() && profile.username.isNotBlank()) username = profile.username
      callsign = profile.callsign
      statusMsg = profile.statusMessage
      seekingHelp = profile.seekingHelp
      avatarIcon = profile.avatarIcon
      auraHex = profile.avatarColorHex
      avatarPhotoBase64 = profile.avatarPhotoBase64
      bannerPhotoBase64 = profile.bannerPhotoBase64
      bannerPreset = profile.bannerPreset.ifBlank { "topo" }
    }
  }

  val avatarPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      PhotoUtils.processUriToBase64Thumb(context, uri, maxDimension = 220, quality = 68)?.let { encoded ->
        avatarPhotoBase64 = encoded
      }
    }
  }

  val bannerPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      PhotoUtils.processUriToBase64Thumb(context, uri, maxDimension = 480, quality = 62)?.let { encoded ->
        bannerPhotoBase64 = encoded
        bannerPreset = "custom"
      }
    }
  }

  val fallbackPrimary = MaterialTheme.colorScheme.primary
  val auraColor = remember(auraHex, fallbackPrimary) {
    runCatching {
      Color(android.graphics.Color.parseColor(auraHex))
    }.getOrElse { fallbackPrimary }
  }

  val avatarBitmap = remember(avatarPhotoBase64) {
    PhotoUtils.decodeBase64Thumbnail(avatarPhotoBase64)?.asImageBitmap()
  }
  val bannerBitmap = remember(bannerPhotoBase64) {
    PhotoUtils.decodeBase64Thumbnail(bannerPhotoBase64)?.asImageBitmap()
  }

  val auraOptions = listOf("#00BFA5", "#FF6B4A", "#5C6BC0", "#FFA726", "#EC407A", "#26C6DA")
  val iconOptions: List<Pair<String, ImageVector>> = listOf(
    "explore" to Icons.Rounded.Explore,
    "bolt" to Icons.Rounded.Bolt,
    "star" to Icons.Rounded.Star,
    "pets" to Icons.Rounded.Pets,
    "terrain" to Icons.Rounded.Terrain,
    "auto" to Icons.Rounded.AutoAwesome,
  )

  val isUsernameValid = username.isEmpty() || (username.length in 3..24 && username.matches(Regex("^[a-zA-Z0-9_]+$")))

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .widthIn(max = 680.dp)
        .fillMaxSize(),
      contentPadding = PaddingValues(
        bottom = if (uiState.settings.gestureNavEnabled) 28.dp else 104.dp,
      ),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Hero Profile Header: Banner BEHIND Overlapping Circular Profile Photo
      item(key = "profile_hero_header") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(228.dp)
              .testTag("profile_banner_container"),
          ) {
            // Banner Surface sitting behind the avatar photo
            Surface(
              shape = MaterialTheme.shapes.extraLarge,
              tonalElevation = 4.dp,
              shadowElevation = 6.dp,
              modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .align(Alignment.TopCenter),
            ) {
              Box(modifier = Modifier.fillMaxSize()) {
                when {
                  bannerPreset == "custom" && bannerBitmap != null -> {
                    Image(
                      bitmap = bannerBitmap,
                      contentDescription = "Banner de perfil",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                  bannerPreset == "sunset" -> {
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(
                          Brush.linearGradient(
                            listOf(
                              MaterialTheme.colorScheme.tertiary,
                              MaterialTheme.colorScheme.primaryContainer,
                              MaterialTheme.colorScheme.secondary,
                            )
                          )
                        )
                    )
                  }
                  bannerPreset == "cosmos" -> {
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(
                          Brush.linearGradient(
                            listOf(
                              MaterialTheme.colorScheme.primary,
                              MaterialTheme.colorScheme.tertiaryContainer,
                              MaterialTheme.colorScheme.primaryContainer,
                            )
                          )
                        )
                    )
                  }
                  bannerPreset == "emerald" -> {
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(
                          Brush.radialGradient(
                            listOf(
                              MaterialTheme.colorScheme.primaryContainer,
                              MaterialTheme.colorScheme.primary,
                            )
                          )
                        )
                    )
                  }
                  else -> {
                    Image(
                      painter = painterResource(id = R.drawable.img_profile_banner),
                      contentDescription = "Banner de perfil",
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize(),
                    )
                  }
                }

                // Subtle Material You gradient scrim for readability
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(
                      Brush.verticalGradient(
                        listOf(
                          Color.Transparent,
                          MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        )
                      )
                    )
                )

                // Hidden Style icon tucked inside Profile banner top-start
                FilledTonalIconButton(
                  onClick = onOpenStyleCustomization,
                  modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .testTag("profile_hidden_style_icon_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = "Abrir Estilo y ajustes",
                  )
                }

                // Banner upload CTA at top-end
                FilledTonalButton(
                  onClick = {
                    bannerPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                  shape = CircleShape,
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .testTag("change_banner_button"),
                ) {
                  Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(stringResource(id = R.string.profile_change_banner), style = MaterialTheme.typography.labelMedium)
                }
              }
            }

            // Circular Profile Photo overlapping bottom edge of the Banner
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(106.dp),
            ) {
              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier
                  .fillMaxSize()
                  .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape)
                  .padding(4.dp)
                  .border(3.dp, auraColor, CircleShape)
                  .clickable {
                    avatarPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  }
                  .testTag("profile_avatar_photo"),
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .background(auraColor.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center,
                ) {
                  if (avatarBitmap != null) {
                    Image(
                      bitmap = avatarBitmap,
                      contentDescription = callsign,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    )
                  } else {
                    val chosenIcon = iconOptions.firstOrNull { it.first == avatarIcon }?.second ?: Icons.Rounded.Explore
                    Icon(
                      imageVector = chosenIcon,
                      contentDescription = callsign,
                      tint = auraColor,
                      modifier = Modifier.size(46.dp),
                    )
                  }
                }
              }

              Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 4.dp,
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .size(34.dp)
                  .clickable {
                    avatarPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  }
                  .testTag("change_avatar_button"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = stringResource(id = R.string.profile_change_photo),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = callsign.ifBlank { "Explorador" },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = CircleShape,
            ) {
              Text(
                text = if (username.isNotBlank()) "@$username" else "@sin_usuario",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              )
            }
            RoleTitleBadge(
              role = profile?.role ?: "explorer",
              roleTitle = profile?.roleTitle ?: "Explorador",
              modifier = Modifier.testTag("profile_role_badge"),
            )
          }
          if (statusMsg.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = statusMsg,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
          }
        }
      }

      // 2. Player Stats Strip
      item(key = "profile_stats_strip") {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatPill(label = "Nivel", value = "${profile?.level ?: 1}", modifier = Modifier.weight(1f))
          StatPill(label = "XP", value = "${profile?.xp ?: 0}", modifier = Modifier.weight(1f))
          StatPill(label = "Misiones", value = "${profile?.missionsCompleted ?: 0}", modifier = Modifier.weight(1f))
          StatPill(label = "Ayudas", value = "${profile?.helpsGiven ?: 0}", modifier = Modifier.weight(1f))
        }
      }

      // 3. Create / Edit Profile & Unique Username Form
      item(key = "profile_edit_form") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = stringResource(id = R.string.profile_title),
              style = MaterialTheme.typography.titleLarge,
            )
            Text(
              text = stringResource(id = R.string.profile_create_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
              value = username,
              onValueChange = { input ->
                val clean = input.lowercase().replace(Regex("[^a-z0-9_]"), "").take(24)
                username = clean
              },
              prefix = { Text("@", fontWeight = FontWeight.Bold) },
              label = { Text("Nombre de usuario propio") },
              placeholder = { Text("tu_nombre_pro") },
              supportingText = {
                Text(
                  if (!isUsernameValid) "Mínimo 3 caracteres (letras, números o _)"
                  else "Tu identificador único en Veyra (${username.length}/24)"
                )
              },
              isError = !isUsernameValid,
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_username_input"),
            )

            OutlinedTextField(
              value = callsign,
              onValueChange = { if (it.length <= 32) callsign = it },
              label = { Text("Nombre visible") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_callsign_input"),
            )

            OutlinedTextField(
              value = statusMsg,
              onValueChange = { if (it.length <= 140) statusMsg = it },
              label = { Text("Biografía o estado en el radar") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
            )

            Text("Fondo del banner", style = MaterialTheme.typography.titleSmall)
            val presets = remember {
              listOf(
                "topo" to "Topográfico",
                "sunset" to "Atardecer You",
                "cosmos" to "Cósmico",
                "emerald" to "Esmeralda",
              )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(presets, key = { it.first }, contentType = { "banner_preset_chip" }) { (key, label) ->
                FilterChip(
                  selected = bannerPreset == key,
                  onClick = { bannerPreset = key },
                  label = { Text(label) },
                )
              }
              if (bannerPhotoBase64.isNotBlank()) {
                item(key = "banner_preset_custom") {
                  FilterChip(
                    selected = bannerPreset == "custom",
                    onClick = { bannerPreset = "custom" },
                    label = { Text("Mi foto") },
                  )
                }
              }
            }

            if (avatarPhotoBase64.isNotBlank()) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Usando foto personalizada", style = MaterialTheme.typography.bodySmall)
                FilterChip(
                  selected = false,
                  onClick = { avatarPhotoBase64 = "" },
                  label = { Text("Usar ícono") },
                )
              }
            } else {
              Text("Ícono del avatar", style = MaterialTheme.typography.titleSmall)
              LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(iconOptions, key = { it.first }, contentType = { "avatar_icon_option" }) { (key, icon) ->
                  val selected = avatarIcon == key
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                      )
                      .border(
                        width = if (selected) 2.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape,
                      )
                      .clickable { avatarIcon = key },
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      imageVector = icon,
                      contentDescription = key,
                      tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }

            Text("Aura Material You", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              auraOptions.forEach { hex ->
                val color = remember(hex) { Color(android.graphics.Color.parseColor(hex)) }
                Box(
                  modifier = Modifier
                    .size(38.dp)
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
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Mostrar icono de pedir ayuda", style = MaterialTheme.typography.bodyMedium)
              Switch(checked = seekingHelp, onCheckedChange = { seekingHelp = it })
            }

            Button(
              onClick = {
                onSaveProfile(
                  username,
                  callsign.trim().ifBlank { "Explorador" },
                  avatarIcon,
                  auraHex,
                  avatarPhotoBase64,
                  bannerPhotoBase64,
                  bannerPreset,
                  statusMsg.trim(),
                  seekingHelp,
                )
              },
              enabled = isUsernameValid && callsign.trim().length >= 2,
              modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_custom_profile_button"),
            ) {
              Icon(Icons.Rounded.Save, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text(stringResource(id = R.string.profile_save_btn))
            }
          }
        }
      }

      // 4. Quick Material You Theme Sync Card
      item(key = "profile_theme_sync_card") {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Personalización Material You", style = MaterialTheme.typography.titleMedium)
              }
              FilledTonalButton(
                onClick = onOpenStyleCustomization,
                shape = CircleShape,
              ) {
                Text("Avanzado")
              }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(PaletteOption.entries, key = { it.key }, contentType = { "palette_chip" }) { opt ->
                FilterChip(
                  selected = uiState.settings.paletteKey == opt.key,
                  onClick = { onQuickSelectPalette(opt.key) },
                  shape = CircleShape,
                  label = { Text(opt.label) },
                )
              }
            }
          }
        }
      }

      // 5. Photo Proofs Gallery
      if (uiState.myCompletions.isNotEmpty()) {
        item(key = "profile_photo_gallery") {
          Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text("Misiones confirmadas (${uiState.myCompletions.size})", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(uiState.myCompletions, key = { it.id }, contentType = { "proof_thumb" }) { comp ->
                val bmp = remember(comp.photoThumbnail) {
                  PhotoUtils.decodeBase64Thumbnail(comp.photoThumbnail)?.asImageBitmap()
                }
                Card(
                  shape = MaterialTheme.shapes.medium,
                  modifier = Modifier.size(130.dp),
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
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
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
      }
    }
  }
}

@Composable
private fun StatPill(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(18.dp),
    modifier = modifier,
  ) {
    Column(
      modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
