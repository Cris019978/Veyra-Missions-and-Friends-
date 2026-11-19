package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.R
import com.example.data.local.LocalAuthManager
import com.example.data.local.LocalAuthResult

private val DarkObsidian = Color(0xFF0B0E14)
private val DarkCardSurface = Color(0xFF141923)
private val DarkCardBorder = Color(0xFF253042)
private val DarkInputFill = Color(0xFF0F141D)
private val VeyraTeal = Color(0xFF00E5C3)
private val VeyraTealContent = Color(0xFF05241F)
private val VeyraTextPrimary = Color(0xFFF1F5F9)
private val VeyraTextSecondary = Color(0xFF94A3B8)
private val VeyraErrorBg = Color(0xFF3A1419)
private val VeyraErrorText = Color(0xFFFFB4AB)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuthScreen(
  onLogin: (username: String, pin: String) -> LocalAuthResult,
  onRegister: (username: String, pin: String, inviteCode: String) -> LocalAuthResult,
  onAuthenticated: () -> Unit = {},
) {
  // Block system Back button while unauthenticated on the Auth screen
  BackHandler(enabled = true) {}

  val scrollState = rememberScrollState()
  val focusScope = rememberCoroutineScope()
  val formBringIntoViewRequester = remember { BringIntoViewRequester() }

  var isRegisterMode by rememberSaveable { mutableStateOf(false) }
  var username by rememberSaveable { mutableStateOf("") }
  var pin by rememberSaveable { mutableStateOf("") }
  var pinVisible by rememberSaveable { mutableStateOf(false) }
  var showInviteField by rememberSaveable { mutableStateOf(false) }
  var inviteCode by rememberSaveable { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun submitAuth() {
    val cleanUser = LocalAuthManager.normalizeUsername(username)
    if (cleanUser.length < 3) {
      errorMessage = "Ingresa un nombre de usuario único de al menos 3 letras o números."
      return
    }
    if (pin.trim().length < 4) {
      errorMessage = "El PIN o contraseña corta debe tener al menos 4 caracteres o dígitos."
      return
    }

    val result = if (isRegisterMode) {
      onRegister(cleanUser, pin, inviteCode)
    } else {
      onLogin(cleanUser, pin)
    }

    when (result) {
      is LocalAuthResult.Success -> {
        errorMessage = null
        onAuthenticated()
      }
      is LocalAuthResult.Error -> {
        errorMessage = result.message
      }
    }
  }

  val textFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = VeyraTextPrimary,
    unfocusedTextColor = VeyraTextPrimary,
    focusedContainerColor = DarkInputFill,
    unfocusedContainerColor = DarkInputFill,
    focusedBorderColor = VeyraTeal,
    unfocusedBorderColor = DarkCardBorder,
    focusedLabelColor = VeyraTeal,
    unfocusedLabelColor = VeyraTextSecondary,
    focusedLeadingIconColor = VeyraTeal,
    unfocusedLeadingIconColor = VeyraTextSecondary,
    focusedTrailingIconColor = VeyraTeal,
    unfocusedTrailingIconColor = VeyraTextSecondary,
    focusedPlaceholderColor = VeyraTextSecondary.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = VeyraTextSecondary.copy(alpha = 0.6f),
    cursorColor = VeyraTeal,
  )

  Scaffold(
    containerColor = DarkObsidian,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { _ ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.radialGradient(
            colors = listOf(
              VeyraTeal.copy(alpha = 0.16f),
              DarkObsidian,
              Color(0xFF07090D),
            ),
            radius = 950f,
          )
        )
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
    ) {
      BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
      ) {
        val viewportHeight = maxHeight
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .testTag("auth_scrollable_container")
            .padding(horizontal = 22.dp, vertical = 20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Column(
            modifier = Modifier
              .widthIn(max = 460.dp)
              .fillMaxWidth()
              .defaultMinSize(minHeight = (viewportHeight - 40.dp).coerceAtLeast(0.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
          ) {
        // Minimalist Dark Header Banner
        Surface(
          color = DarkCardSurface,
          shape = RoundedCornerShape(28.dp),
          border = BorderStroke(1.dp, DarkCardBorder),
          modifier = Modifier
            .fillMaxWidth()
            .height(172.dp),
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            Image(
              painter = painterResource(id = R.drawable.img_auth_hero),
              contentDescription = stringResource(id = R.string.auth_title),
              contentScale = ContentScale.Crop,
              alpha = 0.42f,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      DarkObsidian.copy(alpha = 0.2f),
                      DarkObsidian.copy(alpha = 0.92f),
                    )
                  )
                )
            )
            Column(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(VeyraTeal.copy(alpha = 0.18f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Radar,
                    contentDescription = null,
                    tint = VeyraTeal,
                    modifier = Modifier.size(18.dp),
                  )
                }
                Text(
                  text = stringResource(id = R.string.auth_title),
                  style = MaterialTheme.typography.headlineLarge,
                  fontWeight = FontWeight.ExtraBold,
                  color = VeyraTextPrimary,
                )
              }
              Text(
                text = "Identidad local ligera • Radar en vivo y misiones",
                style = MaterialTheme.typography.bodySmall,
                color = VeyraTextSecondary,
              )
            }
          }
        }

        // Main Minimalist Dark Auth Card
        Card(
          colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
          shape = RoundedCornerShape(28.dp),
          border = BorderStroke(1.dp, DarkCardBorder),
          modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(formBringIntoViewRequester)
            .testTag("veyra_auth_card"),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
          ) {
            // Segmented Toggle: Iniciar Sesión vs Crear Cuenta
            Surface(
              color = DarkInputFill,
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, DarkCardBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                AuthModePill(
                  label = stringResource(id = R.string.auth_mode_login),
                  selected = !isRegisterMode,
                  onClick = {
                    isRegisterMode = false
                    errorMessage = null
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("auth_mode_login_tab"),
                )
                AuthModePill(
                  label = stringResource(id = R.string.auth_mode_register),
                  selected = isRegisterMode,
                  onClick = {
                    isRegisterMode = true
                    errorMessage = null
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("auth_mode_register_tab"),
                )
              }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = if (isRegisterMode) "Crea tu acceso rápido" else "Entra a tu radar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = VeyraTextPrimary,
              )
              Text(
                text = if (isRegisterMode) {
                  "Solo elige tu @usuario único y un PIN corto (mín. 4 caracteres)."
                } else {
                  "Ingresa tu @usuario y tu PIN guardado en este dispositivo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = VeyraTextSecondary,
              )
            }

            // Field 1: Unique Username
            OutlinedTextField(
              value = username,
              onValueChange = { input ->
                val sanitized = input
                  .lowercase()
                  .replace(" ", "")
                  .replace(Regex("[^a-z0-9_@]"), "")
                  .take(25)
                username = sanitized
                if (errorMessage != null) errorMessage = null
              },
              label = { Text(stringResource(id = R.string.auth_username_label)) },
              placeholder = { Text("@explorador") },
              leadingIcon = {
                Icon(Icons.Rounded.AlternateEmail, contentDescription = null)
              },
              singleLine = true,
              colors = textFieldColors,
              shape = RoundedCornerShape(16.dp),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Next,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .onFocusEvent { state ->
                  if (state.isFocused) {
                    focusScope.launch {
                      delay(150L)
                      formBringIntoViewRequester.bringIntoView()
                    }
                  }
                }
                .testTag("auth_username_input"),
            )

            // Field 2: Short PIN / Password
            OutlinedTextField(
              value = pin,
              onValueChange = { input ->
                pin = input.take(20)
                if (errorMessage != null) errorMessage = null
              },
              label = { Text(stringResource(id = R.string.auth_pin_label)) },
              placeholder = { Text("Mínimo 4 dígitos o caracteres") },
              leadingIcon = {
                Icon(Icons.Rounded.Lock, contentDescription = null)
              },
              trailingIcon = {
                IconButton(
                  onClick = { pinVisible = !pinVisible },
                  modifier = Modifier.testTag("auth_pin_visibility_toggle"),
                ) {
                  Icon(
                    imageVector = if (pinVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (pinVisible) "Ocultar PIN" else "Mostrar PIN",
                  )
                }
              },
              visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              colors = textFieldColors,
              shape = RoundedCornerShape(16.dp),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isRegisterMode && showInviteField) ImeAction.Next else ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(
                onDone = { submitAuth() },
              ),
              modifier = Modifier
                .fillMaxWidth()
                .onFocusEvent { state ->
                  if (state.isFocused) {
                    focusScope.launch {
                      delay(150L)
                      formBringIntoViewRequester.bringIntoView()
                    }
                  }
                }
                .testTag("auth_pin_input"),
            )

            // Optional expandable Beta Tester invite code inside Create Account mode
            AnimatedVisibility(visible = isRegisterMode) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                  onClick = { showInviteField = !showInviteField },
                  modifier = Modifier
                    .align(Alignment.Start)
                    .testTag("toggle_invite_code_button"),
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Science,
                    contentDescription = null,
                    tint = VeyraTeal,
                    modifier = Modifier.size(16.dp),
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (showInviteField) "Ocultar código Beta Tester" else "¿Tienes código Beta Tester?",
                    style = MaterialTheme.typography.labelMedium,
                    color = VeyraTeal,
                  )
                }

                AnimatedVisibility(visible = showInviteField) {
                  OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase().take(24) },
                    label = { Text("Código Beta Tester (opcional)") },
                    placeholder = { Text("Ej. VEYRA-BETA") },
                    leadingIcon = {
                      Icon(Icons.Rounded.Science, contentDescription = null)
                    },
                    singleLine = true,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                      imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                      onDone = { submitAuth() },
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .onFocusEvent { state ->
                        if (state.isFocused) {
                          focusScope.launch {
                            delay(150L)
                            formBringIntoViewRequester.bringIntoView()
                          }
                        }
                      }
                      .testTag("auth_invite_code_input"),
                  )
                }
              }
            }

            if (!errorMessage.isNullOrBlank()) {
              Surface(
                color = VeyraErrorBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VeyraErrorText.copy(alpha = 0.3f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("veyra_auth_error_banner"),
              ) {
                Text(
                  text = errorMessage.orEmpty(),
                  style = MaterialTheme.typography.bodySmall,
                  color = VeyraErrorText,
                  modifier = Modifier.padding(12.dp),
                )
              }
            }

            Button(
              onClick = { submitAuth() },
              shape = RoundedCornerShape(18.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = VeyraTeal,
                contentColor = VeyraTealContent,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("veyra_submit_auth_button"),
            ) {
              Icon(
                imageVector = if (isRegisterMode) Icons.Rounded.PersonAdd else Icons.AutoMirrored.Rounded.Login,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (isRegisterMode) {
                  stringResource(id = R.string.auth_create_account_cta)
                } else {
                  stringResource(id = R.string.auth_submit_veyra)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
private fun AuthModePill(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = if (selected) VeyraTeal else Color.Transparent,
    contentColor = if (selected) VeyraTealContent else VeyraTextSecondary,
    shape = RoundedCornerShape(12.dp),
    modifier = modifier
      .height(48.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) VeyraTealContent else VeyraTextSecondary,
      )
    }
  }
}
