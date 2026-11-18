package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import com.example.data.remote.OsmSafePlaceFinder
import com.example.ui.crash.CrashReportActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

data class GooglePlayServicesStatus(
  val isAvailable: Boolean,
  val statusCode: Int,
  val statusMessage: String? = null,
)

data class FirebaseRuntimeState(
  val isAvailable: Boolean,
  val firestore: FirebaseFirestore? = null,
  val playServicesStatus: GooglePlayServicesStatus = GooglePlayServicesStatus(false, -1, null),
  val failureReason: String? = null,
)

/**
 * Centralizes crash-proof startup initialization:
 * 1. Global UncaughtExceptionHandler -> launches [CrashReportActivity] with Copy & Retry.
 * 2. Safe osmdroid tile cache configuration into app-private cacheDir (no storage permissions).
 * 3. Safe Google Play Services & Firebase initialization returning [FirebaseRuntimeState] without
 *    throwing or hanging if google-services.json or Google Play Services are unavailable.
 */
object SafeRuntimeBootstrap {

  private const val TAG = "SafeRuntimeBootstrap"
  private const val PREF_CRASH_GUARD = "veyra_crash_guard"
  private const val KEY_LAST_CRASH_MS = "last_crash_timestamp_ms"
  private const val CRASH_LOOP_WINDOW_MS = 2500L

  @Volatile
  private var handlerInstalled = false

  @Volatile
  private var previousHandler: Thread.UncaughtExceptionHandler? = null

  fun installGlobalExceptionHandler(
    context: Context,
    terminateOnCrash: Boolean = true,
    onLaunchCrashScreen: ((Intent) -> Unit)? = null,
  ) {
    if (handlerInstalled) return
    synchronized(this) {
      if (handlerInstalled) return
      val appCtx = context.applicationContext
      previousHandler = Thread.getDefaultUncaughtExceptionHandler()
      Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
          Log.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
          val thrownFromCrashScreen = generateSequence(throwable) { it.cause }
            .any { err ->
              err.stackTrace.any { frame ->
                frame.className == CrashReportActivity::class.java.name
              }
            }

          if (!thrownFromCrashScreen && !isWithinCrashLoopWindow(appCtx)) {
            markCrashTimestamp(appCtx)
            val report = formatCrashReport(thread.name, throwable)
            val intent = CrashReportActivity.createIntent(appCtx, thread.name, report)
            if (onLaunchCrashScreen != null) {
              onLaunchCrashScreen(intent)
            } else {
              appCtx.startActivity(intent)
            }
          } else {
            previousHandler?.uncaughtException(thread, throwable)
          }
        } catch (launchError: Throwable) {
          Log.e(TAG, "Failed to launch CrashReportActivity", launchError)
          previousHandler?.uncaughtException(thread, throwable)
        } finally {
          if (terminateOnCrash && !isRobolectricEnvironment()) {
            Process.killProcess(Process.myPid())
            exitProcess(10)
          }
        }
      }
      handlerInstalled = true
    }
  }

  fun formatCrashReport(threadName: String, throwable: Throwable): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    return buildString {
      appendLine("Fecha: $timestamp")
      appendLine("Hilo: $threadName")
      appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
      appendLine("Excepción: ${throwable::class.java.name}: ${throwable.message ?: "Sin mensaje"}")
      appendLine()
      appendLine("--- Stack Trace ---")
      append(throwable.stackTraceToString())
    }
  }

  fun clearCrashCooldown(context: Context) {
    runCatching {
      context.applicationContext
        .getSharedPreferences(PREF_CRASH_GUARD, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_LAST_CRASH_MS)
        .apply()
    }
  }

  private fun isWithinCrashLoopWindow(context: Context): Boolean {
    return runCatching {
      val prefs = context.getSharedPreferences(PREF_CRASH_GUARD, Context.MODE_PRIVATE)
      val lastMs = prefs.getLong(KEY_LAST_CRASH_MS, 0L)
      val now = System.currentTimeMillis()
      lastMs > 0L && (now - lastMs) < CRASH_LOOP_WINDOW_MS
    }.getOrDefault(false)
  }

  private fun markCrashTimestamp(context: Context) {
    runCatching {
      context.getSharedPreferences(PREF_CRASH_GUARD, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_LAST_CRASH_MS, System.currentTimeMillis())
        .apply()
    }
  }

  private fun isRobolectricEnvironment(): Boolean {
    return Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
  }

  fun initOsmdroidSafely(context: Context): Boolean {
    return try {
      OsmSafePlaceFinder.configureOsmdroid(context.applicationContext)
      true
    } catch (t: Throwable) {
      Log.w(TAG, "osmdroid safe configuration skipped: ${t.message}", t)
      false
    }
  }

  fun checkGooglePlayServices(context: Context): GooglePlayServicesStatus {
    return try {
      val appCtx = context.applicationContext
      val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appCtx)
      if (code == ConnectionResult.SUCCESS) {
        GooglePlayServicesStatus(isAvailable = true, statusCode = code, statusMessage = null)
      } else {
        val label = when (code) {
          ConnectionResult.SERVICE_MISSING -> "Google Play Services no está instalado en este dispositivo/emulador"
          ConnectionResult.SERVICE_UPDATING -> "Google Play Services se está actualizando temporalmente"
          ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> "Google Play Services requiere actualización"
          ConnectionResult.SERVICE_DISABLED -> "Google Play Services está deshabilitado"
          ConnectionResult.SERVICE_INVALID -> "La firma o instalación de Google Play Services no es válida"
          else -> "Google Play Services no disponible temporalmente (código $code)"
        }
        Log.w(TAG, label)
        GooglePlayServicesStatus(isAvailable = false, statusCode = code, statusMessage = label)
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Google Play Services check failed: ${t.message}", t)
      GooglePlayServicesStatus(
        isAvailable = false,
        statusCode = -1,
        statusMessage = "Error al verificar Google Play Services: ${t.message ?: "desconocido"}",
      )
    }
  }

  fun tryInitFirebase(
    context: Context,
    databaseId: String,
  ): FirebaseRuntimeState {
    val appCtx = context.applicationContext
    val playStatus = checkGooglePlayServices(appCtx)
    return try {
      val apps = FirebaseApp.getApps(appCtx)
      val firebaseApp = if (apps.isNotEmpty()) {
        apps.first()
      } else {
        FirebaseApp.initializeApp(appCtx)
      }

      if (firebaseApp == null) {
        return FirebaseRuntimeState(
          isAvailable = false,
          playServicesStatus = playStatus,
          failureReason = "google-services.json ausente o sin configurar",
        )
      }

      val appId = firebaseApp.options.applicationId
      val projectId = firebaseApp.options.projectId
      if (appId.isBlank() || projectId.isNullOrBlank() || projectId == "remixed-project-id") {
        return FirebaseRuntimeState(
          isAvailable = false,
          playServicesStatus = playStatus,
          failureReason = "Configuración de Firebase inválida o incompleta",
        )
      }

      val firestore = try {
        if (databaseId.isNotBlank()) {
          FirebaseFirestore.getInstance(firebaseApp, databaseId)
        } else {
          null
        }
      } catch (t: Throwable) {
        Log.w(TAG, "FirebaseFirestore unavailable: ${t.message}", t)
        null
      }

      if (firestore == null) {
        FirebaseRuntimeState(
          isAvailable = false,
          firestore = null,
          playServicesStatus = playStatus,
          failureReason = playStatus.statusMessage ?: "Servicios de Firebase no disponibles en este entorno",
        )
      } else {
        FirebaseRuntimeState(
          isAvailable = true,
          firestore = firestore,
          playServicesStatus = playStatus,
          failureReason = playStatus.statusMessage,
        )
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Firebase initialization failed gracefully: ${t.message}", t)
      FirebaseRuntimeState(
        isAvailable = false,
        playServicesStatus = playStatus,
        failureReason = t.message ?: playStatus.statusMessage ?: "Firebase no disponible",
      )
    }
  }

  internal fun resetHandlerForTests() {
    synchronized(this) {
      if (handlerInstalled) {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        handlerInstalled = false
      }
    }
  }
}
