package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun AuthScreen(
  firebaseAuth: FirebaseAuth? = null,
  playServicesAvailable: Boolean = true,
  playServicesMessage: String? = null,
  allowAutoSignIn: Boolean = true,
  onAuthSuccess: () -> Unit,
  onContinueLocalMode: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) {
    runCatching { CredentialManager.create(context) }.getOrNull()
  }
  val clientId = remember(context) {
    runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
  }
  val canAttemptGoogleSignIn = firebaseAuth != null && clientId.isNotBlank() && playServicesAvailable && credentialManager != null

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember(playServicesMessage) { mutableStateOf(playServicesMessage) }

  LaunchedEffect(canAttemptGoogleSignIn, firebaseAuth, allowAutoSignIn) {
    if (firebaseAuth != null && runCatching { firebaseAuth.currentUser != null }.getOrDefault(false)) {
      onAuthSuccess()
      return@LaunchedEffect
    }
    if (canAttemptGoogleSignIn && allowAutoSignIn) {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        firebaseAuth = firebaseAuth,
        clientId = clientId,
        onAuthSuccess = onAuthSuccess,
        scope = scope,
      )
    }
  }

  Scaffold { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
              MaterialTheme.colorScheme.background,
              MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f),
            )
          )
        )
        .padding(innerPadding)
        .statusBarsPadding(),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 520.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
      ) {
        Surface(
          shape = MaterialTheme.shapes.extraLarge,
          tonalElevation = 4.dp,
          shadowElevation = 6.dp,
          modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        ) {
          Box(modifier = Modifier.fillMaxSize()) {
            Image(
              painter = painterResource(id = R.drawable.img_auth_hero),
              contentDescription = stringResource(id = R.string.auth_title),
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      MaterialTheme.colorScheme.surface.copy(alpha = 0.05f),
                      MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                    )
                  )
                )
            )
            Column(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            ) {
              Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50),
              ) {
                Text(
                  text = stringResource(id = R.string.publication_name).uppercase(),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = stringResource(id = R.string.auth_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }

        Text(
          text = stringResource(id = R.string.auth_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Card(
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          ),
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            FeatureBullet(
              icon = Icons.Rounded.Radar,
              text = stringResource(id = R.string.auth_feature_radar),
            )
            FeatureBullet(
              icon = Icons.Rounded.CameraAlt,
              text = stringResource(id = R.string.auth_feature_missions),
            )
            FeatureBullet(
              icon = Icons.Rounded.Groups,
              text = stringResource(id = R.string.auth_feature_social),
            )
            FeatureBullet(
              icon = Icons.Rounded.Palette,
              text = stringResource(id = R.string.auth_feature_material_you),
            )
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_error_banner"),
          ) {
            Text(
              text = errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Button(
          onClick = {
            if (!canAttemptGoogleSignIn || firebaseAuth == null || credentialManager == null) {
              errorMessage = playServicesMessage ?: "Google Services no disponible en este entorno. Usa el modo exploración local."
              return@Button
            }
            isLoading = true
            errorMessage = null
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              firebaseAuth = firebaseAuth,
              clientId = clientId,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              onAuthCancelled = {
                isLoading = false
                errorMessage = "Inicio de sesión cancelado. Reintenta o explora en modo local."
              },
              scope = scope,
            )
          },
          enabled = !isLoading && firebaseAuth != null && clientId.isNotBlank(),
          shape = MaterialTheme.shapes.large,
          contentPadding = ButtonDefaults.ContentPadding,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Text(
              text = stringResource(id = R.string.auth_sign_in_google),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        if (onContinueLocalMode != null) {
          OutlinedButton(
            onClick = onContinueLocalMode,
            shape = MaterialTheme.shapes.large,
            contentPadding = ButtonDefaults.ContentPadding,
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("continue_local_mode_button"),
          ) {
            Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Explorar en modo local",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
            )
          }
        }
      }
    }
  }
}

private fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager?,
  firebaseAuth: FirebaseAuth?,
  clientId: String,
  onAuthSuccess: () -> Unit,
  scope: CoroutineScope,
) {
  if (credentialManager == null || firebaseAuth == null || clientId.isBlank()) return
  if (runCatching { firebaseAuth.currentUser != null }.getOrDefault(false)) {
    onAuthSuccess()
    return
  }
  val googleIdOption = runCatching {
    GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(true)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(true)
      .build()
  }.getOrNull() ?: return

  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  scope.launch {
    try {
      val result = withTimeoutOrNull(8_000L) {
        credentialManager.getCredential(context, request)
      } ?: return@launch
      val cred = result.credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      }
    } catch (e: Exception) {
      Log.d("Auth", "Silent auto sign-in skipped: ${e.message}")
    }
  }
}

private fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  firebaseAuth: FirebaseAuth,
  clientId: String,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  onAuthCancelled: () -> Unit,
  scope: CoroutineScope,
) {
  val activityContext = context as? Activity
  if (activityContext == null) {
    onAuthError("No se encontró una Activity válida para iniciar sesión.")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    try {
      val cred = credentialManager.getCredential(activityContext, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onAuthError("Tipo de credencial inesperado al iniciar sesión.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: NoCredentialException) {
      Log.w("Auth", "No Google credentials available on device/emulator: ${e.message}", e)
      onAuthError("No hay cuenta de Google disponible en este dispositivo/emulador. Usa el modo exploración local.")
    } catch (e: Exception) {
      Log.e("Auth", "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "No se pudo conectar con Google Services.")
    }
  }
}

@Composable
private fun FeatureBullet(icon: ImageVector, text: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
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
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}
