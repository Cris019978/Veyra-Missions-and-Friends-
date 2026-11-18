package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.location.LocationListenerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

data class LiveLocationPoint(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
)

/**
 * Provides live location updates using Google Play Services [FusedLocationProviderClient]
 * when available, and automatically falls back to Android's native [LocationManager]
 * (GPS + Network providers) if Google Play Services are missing, outdated, or fail at runtime.
 */
class LocationTracker(
  context: Context,
  private val playServicesAvailabilityChecker: (Context) -> Boolean = Companion::defaultPlayServicesCheck,
) {

  private val appContext = context.applicationContext

  private fun tryGetFusedClient(): FusedLocationProviderClient? {
    return try {
      if (!playServicesAvailabilityChecker(appContext)) return null
      LocationServices.getFusedLocationProviderClient(appContext)
    } catch (t: Throwable) {
      Log.w(TAG, "FusedLocationProviderClient unavailable, using native LocationManager: ${t.message}")
      null
    }
  }

  @SuppressLint("MissingPermission")
  fun observeLocationUpdates(intervalMs: Long = 5000L): Flow<LiveLocationPoint> = callbackFlow {
    val nativeStarted = AtomicBoolean(false)
    val fusedAttached = AtomicBoolean(false)
    var fusedClient: FusedLocationProviderClient? = null
    var fusedCallback: LocationCallback? = null
    var nativeManager: LocationManager? = null
    var nativeListener: LocationListenerCompat? = null

    fun emitLocation(location: Location) {
      trySend(
        LiveLocationPoint(
          latitude = location.latitude,
          longitude = location.longitude,
          accuracyMeters = if (location.hasAccuracy()) location.accuracy else 25f,
        )
      )
    }

    fun startNativeLocationManager() {
      if (!nativeStarted.compareAndSet(false, true)) return
      try {
        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
          Log.w(TAG, "System LocationManager is unavailable.")
          return
        }
        nativeManager = lm

        val candidateProviders = listOf(
          LocationManager.GPS_PROVIDER,
          LocationManager.NETWORK_PROVIDER,
          LocationManager.PASSIVE_PROVIDER,
        )

        var bestLast: Location? = null
        candidateProviders.forEach { provider ->
          val last = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
          if (last != null && (bestLast == null || last.time > (bestLast?.time ?: 0L))) {
            bestLast = last
          }
        }
        bestLast?.let { emitLocation(it) }

        val listener = LocationListenerCompat { location ->
          emitLocation(location)
        }
        nativeListener = listener

        val activeProviders = candidateProviders.filter { provider ->
          runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)
        }.ifEmpty {
          listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        }

        var registeredAny = false
        activeProviders.forEach { provider ->
          val registered = runCatching {
            lm.requestLocationUpdates(
              provider,
              intervalMs.coerceAtLeast(1500L),
              3f,
              listener,
              Looper.getMainLooper(),
            )
            true
          }.getOrElse { err ->
            Log.w(TAG, "Provider $provider registration skipped: ${err.message}")
            false
          }
          if (registered) registeredAny = true
        }

        if (!registeredAny) {
          Log.w(TAG, "No native location provider could be registered right now.")
        }
      } catch (t: Throwable) {
        Log.w(TAG, "Native LocationManager fallback failed: ${t.message}", t)
      }
    }

    val client = tryGetFusedClient()
    if (client != null) {
      fusedClient = client
      try {
        client.lastLocation
          .addOnSuccessListener { lastLoc ->
            if (lastLoc != null) emitLocation(lastLoc)
          }
          .addOnFailureListener { err ->
            Log.w(TAG, "Fused lastLocation failed (${err.message}); activating native LocationManager")
            startNativeLocationManager()
          }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
          .setMinUpdateDistanceMeters(3f)
          .setMinUpdateIntervalMillis(2500L)
          .build()

        val callback = object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            emitLocation(loc)
          }
        }
        fusedCallback = callback

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
          .addOnSuccessListener {
            fusedAttached.set(true)
          }
          .addOnFailureListener { err ->
            Log.w(TAG, "Fused requestLocationUpdates failed (${err.message}); falling back to LocationManager")
            startNativeLocationManager()
          }
      } catch (t: Throwable) {
        Log.w(TAG, "Fused location threw (${t.message}); falling back to LocationManager", t)
        startNativeLocationManager()
      }
    } else {
      startNativeLocationManager()
    }

    awaitClose {
      val cb = fusedCallback
      val fc = fusedClient
      if (cb != null && fc != null && fusedAttached.get()) {
        runCatching { fc.removeLocationUpdates(cb) }
      }
      val nl = nativeListener
      val nm = nativeManager
      if (nl != null && nm != null) {
        runCatching { nm.removeUpdates(nl) }
      }
    }
  }

  companion object {
    private const val TAG = "LocationTracker"

    fun defaultPlayServicesCheck(context: Context): Boolean {
      return try {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
      } catch (_: Throwable) {
        false
      }
    }
  }
}
