package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object PhotoUtils {

  data class ProcessedPhoto(
    val localFilePath: String,
    val base64Thumbnail: String,
  )

  fun processUriToProof(context: Context, uri: Uri): ProcessedPhoto? {
    return runCatching {
      val inputStream = context.contentResolver.openInputStream(uri) ?: return null
      val originalBitmap = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
      saveAndEncodeBitmap(context, originalBitmap)
    }.getOrNull()
  }

  fun processUriToBase64Thumb(
    context: Context,
    uri: Uri,
    maxDimension: Int = 360,
    quality: Int = 62,
  ): String? {
    return runCatching {
      val inputStream = context.contentResolver.openInputStream(uri) ?: return null
      val originalBitmap = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
      val scaled = scaleToMaxDimension(originalBitmap, maxDimension)
      val baos = ByteArrayOutputStream()
      scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
      Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()
  }

  fun processFileToProof(context: Context, file: File, rotationDegrees: Int = 0): ProcessedPhoto? {
    return runCatching {
      val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
      val rotated = if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
      } else {
        decoded
      }
      saveAndEncodeBitmap(context, rotated)
    }.getOrNull()
  }

  private fun saveAndEncodeBitmap(context: Context, bitmap: Bitmap): ProcessedPhoto {
    val proofsDir = File(context.filesDir, "mission_proofs").apply { mkdirs() }
    val outFile = File(proofsDir, "proof_${System.currentTimeMillis()}.jpg")

    val fullScaled = scaleToMaxDimension(bitmap, 1080)
    FileOutputStream(outFile).use { fos ->
      fullScaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
    }

    val thumbScaled = scaleToMaxDimension(bitmap, 220)
    val baos = ByteArrayOutputStream()
    thumbScaled.compress(Bitmap.CompressFormat.JPEG, 65, baos)
    val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

    return ProcessedPhoto(
      localFilePath = outFile.absolutePath,
      base64Thumbnail = base64,
    )
  }

  private fun scaleToMaxDimension(bitmap: Bitmap, maxDim: Int): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDim && h <= maxDim) return bitmap
    val ratio = maxDim.toFloat() / maxOf(w, h).toFloat()
    val newW = (w * ratio).toInt().coerceAtLeast(1)
    val newH = (h * ratio).toInt().coerceAtLeast(1)
    return bitmap.scale(newW, newH)
  }

  fun decodeBase64Thumbnail(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return runCatching {
      val bytes = Base64.decode(base64, Base64.DEFAULT)
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
  }
}
