package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class LiveLocationPoint(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
)

class LocationTracker(context: Context) {

  private val fusedClient = LocationServices.getFusedLocationProviderClient(context.applicationContext)

  @SuppressLint("MissingPermission")
  fun observeLocationUpdates(intervalMs: Long = 5000L): Flow<LiveLocationPoint> = callbackFlow {
    fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
      if (lastLoc != null) {
        trySend(
          LiveLocationPoint(
            latitude = lastLoc.latitude,
            longitude = lastLoc.longitude,
            accuracyMeters = lastLoc.accuracy,
          )
        )
      }
    }

    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
      .setMinUpdateDistanceMeters(3f)
      .setMinUpdateIntervalMillis(2500L)
      .build()

    val callback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        val loc = result.lastLocation ?: return
        trySend(
          LiveLocationPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = loc.accuracy,
          )
        )
      }
    }

    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
    awaitClose {
      fusedClient.removeLocationUpdates(callback)
    }
  }
}
