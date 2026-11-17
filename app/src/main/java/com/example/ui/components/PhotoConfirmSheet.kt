package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.Mission
import com.example.util.PhotoUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoConfirmSheet(
  mission: Mission,
  isSubmitting: Boolean,
  onDismiss: () -> Unit,
  onConfirmPhoto: (caption: String, base64Thumbnail: String, isPublic: Boolean) -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  var cameraError by remember { mutableStateOf<String?>(null) }
  var processedPhoto by remember { mutableStateOf<PhotoUtils.ProcessedPhoto?>(null) }
  var caption by remember { mutableStateOf("") }
  var isPublic by remember { mutableStateOf(true) }
  var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasCameraPermission = granted
  }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      processedPhoto = PhotoUtils.processUriToProof(context, uri)
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .padding(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Confirmar misión con foto",
            style = MaterialTheme.typography.titleLarge,
          )
          Text(
            text = "${mission.title} • +${mission.xpReward} XP",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cerrar")
        }
      }

      if (processedPhoto == null) {
        // In-App CameraX Viewfinder or Permission Request
        Surface(
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerHighest,
          modifier = Modifier
            .fillMaxWidth()
            .height(290.dp),
        ) {
          if (hasCameraPermission && cameraError == null) {
            Box(modifier = Modifier.fillMaxSize()) {
              CameraXViewfinder(
                lifecycleOwner = lifecycleOwner,
                onImageCaptureReady = { imageCapture = it },
                onCameraError = { cameraError = it },
              )
              Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                  .align(Alignment.TopCenter)
                  .padding(12.dp),
              ) {
                Text(
                  text = mission.description,
                  style = MaterialTheme.typography.bodySmall,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
              }
            }
          } else {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = cameraError ?: "Activa la cámara para tomar la foto de prueba dentro de la app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(12.dp))
              if (!hasCameraPermission) {
                Button(
                  onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                  modifier = Modifier.testTag("grant_camera_button"),
                ) {
                  Text("Permitir cámara")
                }
              }
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("pick_gallery_photo_button"),
          ) {
            Icon(imageVector = Icons.Rounded.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Galería")
          }

          Button(
            onClick = {
              val capture = imageCapture ?: return@Button
              val tempFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.jpg")
              val outputOpts = ImageCapture.OutputFileOptions.Builder(tempFile).build()
              capture.takePicture(
                outputOpts,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    processedPhoto = PhotoUtils.processFileToProof(context, tempFile)
                  }

                  override fun onError(exception: ImageCaptureException) {
                    cameraError = "No se pudo capturar la foto (${exception.localizedMessage})"
                  }
                },
              )
            },
            enabled = hasCameraPermission && imageCapture != null && cameraError == null,
            modifier = Modifier
              .weight(1.4f)
              .height(50.dp)
              .testTag("capture_photo_button"),
          ) {
            Icon(imageVector = Icons.Rounded.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Tomar foto")
          }
        }
      } else {
        val currentProof = processedPhoto!!
        val previewBitmap = remember(currentProof.localFilePath) {
          BitmapFactory.decodeFile(currentProof.localFilePath)?.asImageBitmap()
        }
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
          if (previewBitmap != null) {
            Image(
              bitmap = previewBitmap,
              contentDescription = "Foto de misión",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
          }
          FilledTonalButton(
            onClick = { processedPhoto = null },
            shape = CircleShape,
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(10.dp),
          ) {
            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Repetir")
            Spacer(modifier = Modifier.size(4.dp))
            Text("Repetir")
          }
        }

        OutlinedTextField(
          value = caption,
          onValueChange = { if (it.length <= 200) caption = it },
          label = { Text("Nota de tu hallazgo (opcional)") },
          placeholder = { Text("Ej. Encontré este árbol en el parque central") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("mission_photo_caption_input"),
        )

        Surface(
          color = if (isPublic) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = if (isPublic) Icons.Rounded.Public else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Column {
                Text(
                  text = if (isPublic) "Publicar en Red Social" else "Solo en mi galería privada",
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = if (isPublic) {
                    "Los demás exploradores podrán ver esta foto."
                  } else {
                    "Se guardará únicamente para ti."
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            Switch(
              checked = isPublic,
              onCheckedChange = { isPublic = it },
              modifier = Modifier.testTag("publish_to_social_switch"),
            )
          }
        }

        Button(
          onClick = {
            onConfirmPhoto(caption, currentProof.base64Thumbnail, isPublic)
          },
          enabled = !isSubmitting,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("submit_photo_confirmation_button"),
        ) {
          Icon(imageVector = Icons.Rounded.CheckCircle, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text(if (isSubmitting) "Enviando..." else "Confirmar y ganar +${mission.xpReward} XP")
        }
      }
    }
  }
}

@Composable
private fun CameraXViewfinder(
  lifecycleOwner: androidx.lifecycle.LifecycleOwner,
  onImageCaptureReady: (ImageCapture) -> Unit,
  onCameraError: (String) -> Unit,
) {
  val context = LocalContext.current
  val previewView = remember { PreviewView(context) }

  DisposableEffect(lifecycleOwner) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    val executor = ContextCompat.getMainExecutor(context)
    cameraProviderFuture.addListener(
      {
        try {
          val provider = cameraProviderFuture.get()
          val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
          }
          val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
          provider.unbindAll()
          provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            capture,
          )
          onImageCaptureReady(capture)
        } catch (e: Exception) {
          onCameraError("Cámara no disponible en este dispositivo. Usa 'Galería'.")
        }
      },
      executor,
    )
    onDispose {
      runCatching { cameraProviderFuture.get().unbindAll() }
    }
  }

  AndroidView(
    factory = { previewView },
    modifier = Modifier.fillMaxSize(),
  )
}
