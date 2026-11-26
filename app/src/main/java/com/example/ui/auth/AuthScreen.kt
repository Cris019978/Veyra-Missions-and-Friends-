package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.local.LocalAuthManager
import com.example.data.local.LocalAuthResult
import com.example.data.local.SecretCodeEntry
import com.example.data.local.SecretCodeSection
import com.example.ui.components.ConfettiCelebrationOverlay
import com.example.ui.components.RoleTitleBadge
import kotlinx.coroutines.delay

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

private val TriforceGold = Color(0xFFFBBF24)

@Composable
fun AuthScreen(
  onLogin: (username: String, pin: String) -> LocalAuthResult,
  onRegister: (username: String, pin: String, inviteCode: String) -> LocalAuthResult,
  onAuthenticated: () -> Unit = {},
  onRegistrationCelebration: (message: String, roleTitle: String) -> Unit = { _, _ -> },
  renderCelebrationOverlay: Boolean = true,
  savedUsernames: List<String> = emptyList(),
  animationTier: String = "high",
  nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) {
  // Block system Back button while unauthenticated on the Auth screen
  BackHandler(enabled = true) {}

  val isLowTier = animationTier == "low"
  val scrollState = rememberScrollState()
  val pinFocusRequester = remember { FocusRequester() }
  val inviteFocusRequester = remember { FocusRequester() }

  var isRegisterMode by rememberSaveable { mutableStateOf(false) }
  var username by rememberSaveable { mutableStateOf("") }
  var pin by rememberSaveable { mutableStateOf("") }
  var pinVisible by rememberSaveable { mutableStateOf(false) }
  var showInviteField by rememberSaveable { mutableStateOf(false) }
  var inviteCode by rememberSaveable { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  var triforceTapCount by remember { mutableIntStateOf(0) }
  var lastTriforceTapMs by remember { mutableLongStateOf(0L) }
  var showSecretCatalogDialog by remember { mutableStateOf(false) }

  var standaloneCelebrationKey by remember { mutableLongStateOf(0L) }
  var standaloneCelebrationMsg by remember { mutableStateOf<String?>(null) }
  var standaloneCelebrationTitle by remember { mutableStateOf("") }

  LaunchedEffect(triforceTapCount, lastTriforceTapMs) {
    if (triforceTapCount in 1..4) {
      delay(1500L)
      if (nowMillisProvider() - lastTriforceTapMs >= 1500L) {
        triforceTapCount = 0
        lastTriforceTapMs = 0L
      }
    }
  }

  fun onTriforceTap() {
    val now = nowMillisProvider()
    val withinWindow = lastTriforceTapMs > 0L && (now - lastTriforceTapMs) <= 1500L
    val nextCount = if (withinWindow) triforceTapCount + 1 else 1
    lastTriforceTapMs = now
    if (nextCount >= 5) {
      triforceTapCount = 0
      lastTriforceTapMs = 0L
      showSecretCatalogDialog = true
    } else {
      triforceTapCount = nextCount
    }
  }

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
        if (isRegisterMode) {
          val celebrationMsg = LocalAuthManager.celebrationMessageForCode(inviteCode)
          if (celebrationMsg != null) {
            onRegistrationCelebration(celebrationMsg, result.session.roleTitle)
            if (renderCelebrationOverlay) {
              standaloneCelebrationKey += 1L
              standaloneCelebrationMsg = celebrationMsg
              standaloneCelebrationTitle = result.session.roleTitle
            }
          }
        }
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
  val bgBrush = remember {
    Brush.radialGradient(
      colors = listOf(
        VeyraTeal.copy(alpha = 0.14f),
        DarkObsidian,
        Color(0xFF07090D),
      ),
      radius = 950f,
    )
  }
  val heroScrimBrush = remember {
    Brush.verticalGradient(
      listOf(
        DarkObsidian.copy(alpha = 0.2f),
        DarkObsidian.copy(alpha = 0.92f),
      ),
    )
  }

  Scaffold(
    containerColor = DarkObsidian,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { _ ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(bgBrush)
        .statusBarsPadding()
        .navigationBarsPadding()
        .imePadding(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(scrollState)
          .testTag("auth_scrollable_container")
          .padding(horizontal = 22.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
        // Minimalist Dark Header Banner
        Surface(
          color = DarkCardSurface,
          shape = RoundedCornerShape(28.dp),
          border = BorderStroke(1.dp, DarkCardBorder),
          modifier = Modifier
            .fillMaxWidth()
            .height(158.dp),
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
                .background(heroScrimBrush),
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
            .let { mod ->
              if (isLowTier) {
                mod
              } else {
                mod.animateContentSize(
                  animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                  ),
                )
              }
            }
            .testTag("veyra_auth_card"),
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                  animateColors = !isLowTier,
                  onClick = {
                    isRegisterMode = false
                    triforceTapCount = 0
                    lastTriforceTapMs = 0L
                    showSecretCatalogDialog = false
                    errorMessage = null
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("auth_mode_login_tab"),
                )
                AuthModePill(
                  label = stringResource(id = R.string.auth_mode_register),
                  selected = isRegisterMode,
                  animateColors = !isLowTier,
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

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              AnimatedContent(
                targetState = isRegisterMode,
                transitionSpec = {
                  if (isLowTier) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                  } else {
                    (fadeIn(tween(150)) + slideInVertically(tween(160)) { it / 6 }) togetherWith
                      fadeOut(tween(100))
                  }
                },
                label = "authModeHeader",
                modifier = Modifier.weight(1f),
              ) { register ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = if (register) "Crea tu acceso rápido" else "Entra a tu radar",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = VeyraTextPrimary,
                  )
                  Text(
                    text = if (register) {
                      "Elige tu @usuario único y un PIN corto (mín. 4 caracteres)."
                    } else {
                      "Ingresa tu @usuario y tu PIN guardado en este dispositivo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = VeyraTextSecondary,
                  )
                }
              }

              if (isRegisterMode) {
                TriforceEasterEggButton(
                  tapCount = triforceTapCount,
                  animateAlpha = !isLowTier,
                  onClick = { onTriforceTap() },
                )
              }
            }

            // Acceso en 1 toque para cuentas guardadas localmente (solo en Iniciar Sesión)
            AnimatedVisibility(
              visible = !isRegisterMode && savedUsernames.isNotEmpty(),
              enter = fadeIn(tween(if (isLowTier) 0 else 140)) + expandVertically(tween(if (isLowTier) 0 else 160)),
              exit = fadeOut(tween(if (isLowTier) 0 else 100)) + shrinkVertically(tween(if (isLowTier) 0 else 130)),
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  text = "Cuentas en este dispositivo (toca para rellenar):",
                  style = MaterialTheme.typography.labelSmall,
                  color = VeyraTextSecondary,
                )
                Row(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                ) {
                  savedUsernames.forEach { saved ->
                    val isCurrent = LocalAuthManager.normalizeUsername(username) == saved
                    Surface(
                      color = if (isCurrent) VeyraTeal.copy(alpha = 0.18f) else DarkInputFill,
                      shape = CircleShape,
                      border = BorderStroke(
                        width = 1.dp,
                        color = if (isCurrent) VeyraTeal else DarkCardBorder,
                      ),
                      modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                          username = "@$saved"
                          errorMessage = null
                          pinFocusRequester.requestFocus()
                        }
                        .testTag("auth_saved_user_chip_$saved"),
                    ) {
                      Text(
                        text = "@$saved",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) VeyraTeal else VeyraTextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                      )
                    }
                  }
                }
              }
            }

            // Field 1: Unique Username (sin mutación de mayúsculas ni Regex en cada tecla)
            OutlinedTextField(
              value = username,
              onValueChange = { input ->
                val cleaned = if (input.any { it.isWhitespace() }) {
                  input.filterNot { it.isWhitespace() }
                } else {
                  input
                }
                username = cleaned.take(25)
                if (errorMessage != null) errorMessage = null
              },
              label = { Text(stringResource(id = R.string.auth_username_label)) },
              placeholder = { Text("@explorador") },
              leadingIcon = {
                Icon(Icons.Rounded.AlternateEmail, contentDescription = null)
              },
              trailingIcon = if (username.isNotEmpty()) {
                {
                  IconButton(
                    onClick = {
                      username = ""
                      errorMessage = null
                    },
                    modifier = Modifier.testTag("auth_clear_username_button"),
                  ) {
                    Icon(
                      imageVector = Icons.Rounded.Close,
                      contentDescription = "Limpiar usuario",
                    )
                  }
                }
              } else {
                null
              },
              singleLine = true,
              colors = textFieldColors,
              shape = RoundedCornerShape(16.dp),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next,
              ),
              keyboardActions = KeyboardActions(
                onNext = { pinFocusRequester.requestFocus() },
              ),
              modifier = Modifier
                .fillMaxWidth()
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
                autoCorrectEnabled = false,
                imeAction = if (isRegisterMode && showInviteField) ImeAction.Next else ImeAction.Done,
              ),
              keyboardActions = KeyboardActions(
                onNext = {
                  if (isRegisterMode && showInviteField) {
                    inviteFocusRequester.requestFocus()
                  } else {
                    submitAuth()
                  }
                },
                onDone = { submitAuth() },
              ),
              modifier = Modifier
                .fillMaxWidth()
                .focusRequester(pinFocusRequester)
                .testTag("auth_pin_input"),
            )

            // Optional expandable invite/special code inside Create Account mode
            AnimatedVisibility(
              visible = isRegisterMode,
              enter = fadeIn(tween(if (isLowTier) 0 else 150)) + expandVertically(tween(if (isLowTier) 0 else 170)),
              exit = fadeOut(tween(if (isLowTier) 0 else 110)) + shrinkVertically(tween(if (isLowTier) 0 else 140)),
            ) {
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
                    text = if (showInviteField) {
                      "Ocultar código de Beta Tester"
                    } else {
                      "¿Tienes código de Beta Tester?"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = VeyraTeal,
                  )
                }

                AnimatedVisibility(
                  visible = showInviteField,
                  enter = fadeIn(tween(if (isLowTier) 0 else 150)) + expandVertically(tween(if (isLowTier) 0 else 170)),
                  exit = fadeOut(tween(if (isLowTier) 0 else 110)) + shrinkVertically(tween(if (isLowTier) 0 else 140)),
                ) {
                  OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it.uppercase().take(24) },
                    label = { Text("Código (opcional)") },
                    placeholder = { Text("Ingresa tu código") },
                    leadingIcon = {
                      Icon(Icons.Rounded.Science, contentDescription = null)
                    },
                    singleLine = true,
                    colors = textFieldColors,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                      autoCorrectEnabled = false,
                      imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                      onDone = { submitAuth() },
                    ),
                    modifier = Modifier
                      .fillMaxWidth()
                      .focusRequester(inviteFocusRequester)
                      .testTag("auth_invite_code_input"),
                  )
                }
              }
            }

            AnimatedVisibility(
              visible = !errorMessage.isNullOrBlank(),
              enter = fadeIn(tween(if (isLowTier) 0 else 140)) + expandVertically(tween(if (isLowTier) 0 else 160)),
              exit = fadeOut(tween(if (isLowTier) 0 else 100)) + shrinkVertically(tween(if (isLowTier) 0 else 130)),
            ) {
              val currentErr = errorMessage.orEmpty()
              val cleanUser = LocalAuthManager.normalizeUsername(username)
              val canSwitchToRegister = !isRegisterMode && cleanUser.length >= 3 && currentErr.contains("No existe")
              val canSwitchToLogin = isRegisterMode && cleanUser.length >= 3 && currentErr.contains("ya existe")

              Surface(
                color = VeyraErrorBg,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VeyraErrorText.copy(alpha = 0.3f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("veyra_auth_error_banner"),
              ) {
                Column(
                  modifier = Modifier.padding(12.dp),
                  verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  Text(
                    text = currentErr,
                    style = MaterialTheme.typography.bodySmall,
                    color = VeyraErrorText,
                  )
                  if (canSwitchToRegister || canSwitchToLogin) {
                    Surface(
                      color = VeyraTeal.copy(alpha = 0.18f),
                      shape = RoundedCornerShape(10.dp),
                      border = BorderStroke(1.dp, VeyraTeal.copy(alpha = 0.55f)),
                      modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                          isRegisterMode = canSwitchToRegister
                          errorMessage = null
                        }
                        .testTag("auth_error_quick_switch_button"),
                    ) {
                      Text(
                        text = if (canSwitchToRegister) {
                          "Crear cuenta con @$cleanUser"
                        } else {
                          "Iniciar sesión con @$cleanUser"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VeyraTeal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      )
                    }
                  }
                }
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
              AnimatedContent(
                targetState = isRegisterMode,
                transitionSpec = {
                  if (isLowTier) {
                    androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                  } else {
                    fadeIn(tween(140)) togetherWith fadeOut(tween(100))
                  }
                },
                label = "authSubmitCta",
              ) { register ->
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.Center,
                ) {
                  Icon(
                    imageVector = if (register) Icons.Rounded.PersonAdd else Icons.AutoMirrored.Rounded.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = if (register) {
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

      if (renderCelebrationOverlay) {
        RegistrationCelebrationHost(
          triggerKey = standaloneCelebrationKey,
          message = standaloneCelebrationMsg,
          roleTitle = standaloneCelebrationTitle,
          onDismiss = { standaloneCelebrationMsg = null },
          animationTier = animationTier,
        )
      }
    }
  }

  if (showSecretCatalogDialog && isRegisterMode) {
    TriforceSecretCodesDialog(
      selectedCode = inviteCode.trim().uppercase(),
      onSelectEntry = { entry ->
        inviteCode = entry.code
        showInviteField = true
        showSecretCatalogDialog = false
        errorMessage = null
      },
      onDismiss = { showSecretCatalogDialog = false },
    )
  }
}

@Composable
private fun TriforceEasterEggButton(
  tapCount: Int,
  animateAlpha: Boolean,
  onClick: () -> Unit,
) {
  val targetAlpha = (0.48f + tapCount * 0.13f).coerceAtMost(1f)
  val alphaBoost = if (animateAlpha) {
    animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(120), label = "triforceAlpha").value
  } else {
    targetAlpha
  }
  val topTriangle = remember { Path() }
  val bottomLeftTriangle = remember { Path() }
  val bottomRightTriangle = remember { Path() }

  Box(
    modifier = Modifier
      .minimumInteractiveComponentSize()
      .clip(CircleShape)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(bounded = false, radius = 22.dp),
        role = Role.Button,
        onClick = onClick,
      )
      .semantics { contentDescription = "Símbolo Trifuerza" }
      .testTag("auth_triforce_easter_egg"),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(modifier = Modifier.size(26.dp)) {
      val w = size.width
      val h = size.height
      val goldColor = TriforceGold.copy(alpha = alphaBoost)

      topTriangle.rewind()
      topTriangle.moveTo(w * 0.5f, 0f)
      topTriangle.lineTo(w * 0.25f, h * 0.5f)
      topTriangle.lineTo(w * 0.75f, h * 0.5f)
      topTriangle.close()

      bottomLeftTriangle.rewind()
      bottomLeftTriangle.moveTo(w * 0.25f, h * 0.5f)
      bottomLeftTriangle.lineTo(0f, h)
      bottomLeftTriangle.lineTo(w * 0.5f, h)
      bottomLeftTriangle.close()

      bottomRightTriangle.rewind()
      bottomRightTriangle.moveTo(w * 0.75f, h * 0.5f)
      bottomRightTriangle.lineTo(w * 0.5f, h)
      bottomRightTriangle.lineTo(w, h)
      bottomRightTriangle.close()

      drawPath(path = topTriangle, color = goldColor)
      drawPath(path = bottomLeftTriangle, color = goldColor)
      drawPath(path = bottomRightTriangle, color = goldColor)
    }
  }
}

@Composable
private fun TriforceSecretCodesDialog(
  selectedCode: String,
  onSelectEntry: (SecretCodeEntry) -> Unit,
  onDismiss: () -> Unit,
) {
  val sections = SecretCodeSection.entries
  Dialog(onDismissRequest = onDismiss) {
    Surface(
      color = DarkCardSurface,
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, TriforceGold.copy(alpha = 0.55f)),
      tonalElevation = 10.dp,
      modifier = Modifier
        .fillMaxWidth()
        .testTag("triforce_secret_codes_dialog"),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = TriforceGold,
              modifier = Modifier.size(20.dp),
            )
            Column {
              Text(
                text = "Archivo Trifuerza",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = VeyraTextPrimary,
              )
              Text(
                text = "Elige un código secreto para tu cuenta",
                style = MaterialTheme.typography.labelSmall,
                color = VeyraTextSecondary,
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("triforce_dialog_close_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.Close,
              contentDescription = "Cerrar menú secreto",
              tint = VeyraTextSecondary,
            )
          }
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          sections.forEach { section ->
            val itemsInSection = LocalAuthManager.SECRET_CODE_CATALOG.filter { it.section == section }
            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.testTag("secret_section_${section.name.lowercase()}"),
            ) {
              Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (section == SecretCodeSection.BETA_TESTERS) {
                  VeyraTeal
                } else {
                  TriforceGold
                },
              )

              itemsInSection.forEach { entry ->
                val isSelected = selectedCode == entry.code
                Surface(
                  color = if (isSelected) {
                    VeyraTeal.copy(alpha = 0.14f)
                  } else {
                    DarkInputFill
                  },
                  shape = RoundedCornerShape(16.dp),
                  border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) VeyraTeal else DarkCardBorder,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectEntry(entry) }
                    .testTag("secret_code_option_${entry.code}"),
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                      RoleTitleBadge(role = entry.role, roleTitle = entry.roleTitle)
                      Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = VeyraTextSecondary,
                      )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                      color = TriforceGold.copy(alpha = 0.14f),
                      shape = RoundedCornerShape(10.dp),
                      border = BorderStroke(1.dp, TriforceGold.copy(alpha = 0.45f)),
                    ) {
                      Text(
                        text = entry.code,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TriforceGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
fun RegistrationCelebrationHost(
  triggerKey: Long,
  message: String?,
  roleTitle: String,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  animationTier: String = "high",
) {
  LaunchedEffect(triggerKey, message) {
    if (triggerKey > 0L && message != null) {
      delay(4200L)
      onDismiss()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = message != null,
      enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
      exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
      Surface(
        color = DarkCardSurface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.5.dp, TriforceGold.copy(alpha = 0.72f)),
        shadowElevation = 0.dp,
        modifier = Modifier
          .widthIn(max = 460.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(22.dp))
          .clickable(onClick = onDismiss)
          .testTag("registration_celebration_banner"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(TriforceGold.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.AutoAwesome,
              contentDescription = null,
              tint = TriforceGold,
              modifier = Modifier.size(22.dp),
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
          ) {
            Text(
              text = message.orEmpty(),
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.ExtraBold,
              color = VeyraTextPrimary,
            )
            if (roleTitle.isNotBlank()) {
              Text(
                text = "Título desbloqueado: $roleTitle",
                style = MaterialTheme.typography.labelMedium,
                color = VeyraTeal,
              )
            }
          }
        }
      }
    }

    ConfettiCelebrationOverlay(
      triggerKey = triggerKey,
      animationTier = animationTier,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

@Composable
private fun AuthModePill(
  label: String,
  selected: Boolean,
  animateColors: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val targetBg = if (selected) VeyraTeal else Color.Transparent
  val targetFg = if (selected) VeyraTealContent else VeyraTextSecondary
  val bgColor = if (animateColors) {
    animateColorAsState(targetValue = targetBg, animationSpec = tween(160), label = "authPillBg").value
  } else {
    targetBg
  }
  val fgColor = if (animateColors) {
    animateColorAsState(targetValue = targetFg, animationSpec = tween(160), label = "authPillFg").value
  } else {
    targetFg
  }

  Surface(
    color = bgColor,
    contentColor = fgColor,
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
        color = fgColor,
      )
    }
  }
}
