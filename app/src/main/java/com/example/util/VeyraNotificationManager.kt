package com.example.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class VeyraNotificationManager(private val context: Context) {

  init {
    createChannels()
  }

  private fun createChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return

      val socialChannel = NotificationChannel(
        CHANNEL_SOCIAL,
        "Ayudas, pulsos y retos",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = "Alertas cuando jugadores cercanos te ayudan, envían pulsos o se unen a retos."
      }

      val proximityChannel = NotificationChannel(
        CHANNEL_PROXIMITY,
        "Proximidad de misiones",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Avisos cuando te acercas al punto de una misión activa en el mapa."
      }

      val guidanceChannel = NotificationChannel(
        CHANNEL_GUIDANCE,
        "Guía sutil hacia misión activa",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Indicador discreto de distancia y dirección hacia tu misión aceptada."
        setShowBadge(false)
      }

      manager.createNotificationChannel(socialChannel)
      manager.createNotificationChannel(proximityChannel)
      manager.createNotificationChannel(guidanceChannel)
    }
  }

  fun hasPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    } else {
      NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
  }

  fun postNotification(
    id: Int,
    title: String,
    message: String,
    isProximity: Boolean = false,
  ) {
    if (!hasPermission()) return

    val intent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
      context,
      id,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val channelId = if (isProximity) CHANNEL_PROXIMITY else CHANNEL_SOCIAL
    val priority = if (isProximity) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

    val notification = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(title)
      .setContentText(message)
      .setStyle(NotificationCompat.BigTextStyle().bigText(message))
      .setPriority(priority)
      .setAutoCancel(true)
      .setContentIntent(pendingIntent)
      .build()

    try {
      NotificationManagerCompat.from(context).notify(id, notification)
    } catch (_: SecurityException) {
      // Ignore if permission was revoked concurrently
    }
  }

  fun buildSubtleGuidanceNotification(
    missionTitle: String,
    distanceMeters: Float?,
    bearingDegrees: Float?,
    stopPendingIntent: PendingIntent?,
  ): Notification {
    val mainIntent = Intent(context, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val openAppIntent = PendingIntent.getActivity(
      context,
      GUIDANCE_NOTIFICATION_ID,
      mainIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val title: String
    val text: String
    if (distanceMeters != null && bearingDegrees != null) {
      val arrow = GeoUtils.compassArrowForBearing(bearingDegrees)
      val dir = GeoUtils.cardinalDirectionEs(bearingDegrees)
      val distLabel = GeoUtils.formatDistance(distanceMeters)
      if (distanceMeters <= GeoUtils.CLOSE_GUIDANCE_RADIUS_METERS) {
        title = "¡Punto final muy cerca! $arrow $distLabel"
        text = "'$missionTitle': camina $distLabel hacia el $dir para completar la misión."
      } else {
        title = "Guiando a '$missionTitle' • $distLabel"
        text = "Sigue $arrow rumbo al $dir para acercarte al punto."
      }
    } else {
      title = "Guía de misión activa"
      text = "Buscando señal GPS precisa hacia '$missionTitle'..."
    }

    val builder = NotificationCompat.Builder(context, CHANNEL_GUIDANCE)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setContentIntent(openAppIntent)

    if (stopPendingIntent != null) {
      builder.addAction(0, "Pausar guía", stopPendingIntent)
    }
    return builder.build()
  }

  companion object {
    const val CHANNEL_SOCIAL = "veyra_social"
    const val CHANNEL_PROXIMITY = "veyra_proximity"
    const val CHANNEL_GUIDANCE = "veyra_guidance"
    const val GUIDANCE_NOTIFICATION_ID = 4401
  }
}
