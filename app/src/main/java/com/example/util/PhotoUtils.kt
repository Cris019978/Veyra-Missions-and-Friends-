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

  fun createMiniGameBadgeBase64(missionTitle: String, missionType: String): String {
    val bmp = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val isWires = missionType == "wires"
    val isMemory = missionType == "memory"

    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      shader = android.graphics.LinearGradient(
        0f,
        0f,
        320f,
        200f,
        when {
          isWires -> 0xFF0F2027.toInt()
          isMemory -> 0xFF1A237E.toInt()
          else -> 0xFF2B1055.toInt()
        },
        when {
          isWires -> 0xFF00897B.toInt()
          isMemory -> 0xFF00ACC1.toInt()
          else -> 0xFF753A88.toInt()
        },
        android.graphics.Shader.TileMode.CLAMP,
      )
    }
    canvas.drawRect(0f, 0f, 320f, 200f, bgPaint)

    val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      strokeWidth = 6f
      style = android.graphics.Paint.Style.STROKE
      strokeCap = android.graphics.Paint.Cap.ROUND
    }
    if (isWires) {
      val colors = intArrayOf(0xFFEF4444.toInt(), 0xFF3B82F6.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt())
      colors.forEachIndexed { idx, c ->
        linePaint.color = c
        val y = 42f + idx * 34f
        canvas.drawLine(46f, y, 274f, 42f + ((3 - idx) * 34f), linePaint)
      }
    } else if (isMemory) {
      val nodeColors = intArrayOf(0xFFFF7043.toInt(), 0xFF29B6F6.toInt(), 0xFF66BB6A.toInt(), 0xFFAB47BC.toInt())
      val nodePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
      nodeColors.forEachIndexed { idx, c ->
        nodePaint.color = c
        canvas.drawCircle(76f + idx * 56f, 88f, 22f, nodePaint)
      }
    } else {
      linePaint.color = 0x88FFFFFF.toInt()
      linePaint.strokeWidth = 3f
      for (r in 0..2) {
        for (c in 0..2) {
          val left = 104f + c * 38f
          val top = 34f + r * 38f
          canvas.drawRoundRect(left, top, left + 34f, top + 34f, 8f, 8f, linePaint)
        }
      }
    }

    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      color = android.graphics.Color.WHITE
      textSize = 18f
      isFakeBoldText = true
      textAlign = android.graphics.Paint.Align.CENTER
    }
    val label = when {
      isWires -> "CABLES CONECTADOS"
      isMemory -> "SECUENCIA DESCIFRADA"
      else -> "ROMPECABEZAS RESUELTO"
    }
    canvas.drawText(label, 160f, 174f, textPaint)
    val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xDDFFFFFF.toInt()
      textSize = 13f
      textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText(missionTitle.take(28), 160f, 192f, subPaint)

    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 90, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
  }
}
