package com.example.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.data.location.LocationTracker
import com.example.util.GeoUtils
import com.example.util.VeyraNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Play-compliant Foreground Service (location type, active only while user has accepted a mission)
 * providing a subtle live guidance notification and triggering a close-spot directional alert
 * when entering [GeoUtils.CLOSE_GUIDANCE_RADIUS_METERS].
 */
class ProximityGuidanceService : Service() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var trackingJob: Job? = null
  private lateinit var notifManager: VeyraNotificationManager

  private var activeMissionId: String = ""
  private var activeMissionTitle: String = ""
  private var targetLat: Double = 0.0
  private var targetLon: Double = 0.0
  private var closeSpotAlertSentForId: String? = null

  override fun onCreate() {
    super.onCreate()
    notifManager = VeyraNotificationManager(applicationContext)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }

    val missionId = intent?.getStringExtra(EXTRA_MISSION_ID).orEmpty()
    val title = intent?.getStringExtra(EXTRA_MISSION_TITLE).orEmpty()
    val lat = intent?.getDoubleExtra(EXTRA_LAT, 0.0) ?: 0.0
    val lon = intent?.getDoubleExtra(EXTRA_LON, 0.0) ?: 0.0

    if (missionId.isBlank() || (lat == 0.0 && lon == 0.0) || !hasLocationPermission()) {
      stopSelf()
      return START_NOT_STICKY
    }

    if (activeMissionId != missionId) {
      closeSpotAlertSentForId = null
    }
    activeMissionId = missionId
    activeMissionTitle = title.ifBlank { "Misión activa" }
    targetLat = lat
    targetLon = lon

    val stopIntent = Intent(this, ProximityGuidanceService::class.java).apply { action = ACTION_STOP }
    val stopPending = PendingIntent.getService(
      this,
      9001,
      stopIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val initialNotif = notifManager.buildSubtleGuidanceNotification(
      missionTitle = activeMissionTitle,
      distanceMeters = null,
      bearingDegrees = null,
      stopPendingIntent = stopPending,
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(
        VeyraNotificationManager.GUIDANCE_NOTIFICATION_ID,
        initialNotif,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
      )
    } else {
      startForeground(VeyraNotificationManager.GUIDANCE_NOTIFICATION_ID, initialNotif)
    }

    startTracking(stopPending)
    return START_STICKY
  }

  private fun startTracking(stopPending: PendingIntent) {
    trackingJob?.cancel()
    val tracker = LocationTracker(applicationContext)
    trackingJob = serviceScope.launch {
      tracker.observeLocationUpdates(intervalMs = 3500L)
        .catch { /* Ignore transient location errors while foregrounded */ }
        .collect { point ->
          val dist = GeoUtils.distanceMeters(point.latitude, point.longitude, targetLat, targetLon)
          val bearing = GeoUtils.bearingDegrees(point.latitude, point.longitude, targetLat, targetLon)

          val updatedNotif = notifManager.buildSubtleGuidanceNotification(
            missionTitle = activeMissionTitle,
            distanceMeters = dist,
            bearingDegrees = bearing,
            stopPendingIntent = stopPending,
          )
          val sysManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
          sysManager?.notify(VeyraNotificationManager.GUIDANCE_NOTIFICATION_ID, updatedNotif)

          if (dist <= GeoUtils.CLOSE_GUIDANCE_RADIUS_METERS && closeSpotAlertSentForId != activeMissionId) {
            closeSpotAlertSentForId = activeMissionId
            val arrow = GeoUtils.compassArrowForBearing(bearing)
            val dir = GeoUtils.cardinalDirectionEs(bearing)
            notifManager.postNotification(
              id = ("close_spot_$activeMissionId").hashCode(),
              title = "¡Estás a ${GeoUtils.formatDistance(dist)} del punto final!",
              message = "Sigue $arrow al $dir para llegar exactamente a '$activeMissionTitle'.",
              isProximity = true,
            )
          }
        }
    }
  }

  private fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED ||
      ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
      PackageManager.PERMISSION_GRANTED
  }

  override fun onDestroy() {
    trackingJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  companion object {
    const val ACTION_START = "com.example.action.START_GUIDANCE"
    const val ACTION_STOP = "com.example.action.STOP_GUIDANCE"
    const val EXTRA_MISSION_ID = "extra_mission_id"
    const val EXTRA_MISSION_TITLE = "extra_mission_title"
    const val EXTRA_LAT = "extra_lat"
    const val EXTRA_LON = "extra_lon"

    fun startForMission(
      context: Context,
      missionId: String,
      missionTitle: String,
      latitude: Double,
      longitude: Double,
    ) {
      val intent = Intent(context, ProximityGuidanceService::class.java).apply {
        action = ACTION_START
        putExtra(EXTRA_MISSION_ID, missionId)
        putExtra(EXTRA_MISSION_TITLE, missionTitle)
        putExtra(EXTRA_LAT, latitude)
        putExtra(EXTRA_LON, longitude)
      }
      runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      }
    }

    fun stop(context: Context) {
      runCatching {
        context.stopService(Intent(context, ProximityGuidanceService::class.java))
      }
    }
  }
}
