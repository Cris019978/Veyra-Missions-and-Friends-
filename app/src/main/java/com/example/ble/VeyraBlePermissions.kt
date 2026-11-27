package com.example.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/**
 * Utilidad desacoplada para inspeccionar permisos y capacidades BLE según versión de Android:
 * - Android 12+ (API 31+): BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE y BLUETOOTH_CONNECT.
 * - Android 7..11 (API 24..30): ACCESS_FINE_LOCATION + Ubicación del sistema activa.
 *
 * Nunca lanza SecurityException sin capturar ni bloquea el hilo principal.
 */
object VeyraBlePermissions {

  fun requiredRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
      )
    } else {
      arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
      )
    }

  fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

  fun inspectCapabilities(context: Context): VeyraBleCapabilities {
    val pm = context.packageManager
    val hasBleFeature = pm?.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == true
    val bluetoothManager = runCatching {
      context.getSystemService(BluetoothManager::class.java)
    }.getOrNull()
    val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    val requiresLegacyLocation = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    val hasFineLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
      hasFineLocation
    }

    val hasAdvertisePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
      true
    }

    val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      true
    }

    val isBtEnabled = try {
      adapter?.isEnabled == true
    } catch (_: SecurityException) {
      false
    } catch (_: Throwable) {
      false
    }

    val supportsAdvertising = try {
      adapter != null &&
        isBtEnabled &&
        adapter.isMultipleAdvertisementSupported &&
        adapter.bluetoothLeAdvertiser != null
    } catch (_: SecurityException) {
      false
    } catch (_: Throwable) {
      false
    }

    val isLocationEnabled = if (requiresLegacyLocation) {
      try {
        val lm = context.getSystemService(LocationManager::class.java)
        lm != null && LocationManagerCompat.isLocationEnabled(lm)
      } catch (_: Throwable) {
        false
      }
    } else {
      true
    }

    return VeyraBleCapabilities(
      hasBleHardware = hasBleFeature,
      isBluetoothSupported = adapter != null,
      isBluetoothEnabled = isBtEnabled,
      supportsAdvertising = supportsAdvertising,
      hasScanPermission = hasScanPermission,
      hasAdvertisePermission = hasAdvertisePermission,
      hasConnectPermission = hasConnectPermission,
      requiresLocationServicesForBle = requiresLegacyLocation,
      isLocationServiceEnabled = isLocationEnabled,
    )
  }

  fun resolveHardwareStatus(capabilities: VeyraBleCapabilities): BleHardwareStatus = when {
    !capabilities.hasBleHardware || !capabilities.isBluetoothSupported -> BleHardwareStatus.UNSUPPORTED
    !capabilities.hasScanPermission -> BleHardwareStatus.PERMISSIONS_REQUIRED
    !capabilities.isBluetoothEnabled -> BleHardwareStatus.BLUETOOTH_OFF
    capabilities.requiresLocationServicesForBle && !capabilities.isLocationServiceEnabled ->
      BleHardwareStatus.LOCATION_REQUIRED_LEGACY
    capabilities.supportsAdvertising && capabilities.hasAdvertisePermission ->
      BleHardwareStatus.READY_FULL
    else -> BleHardwareStatus.SCAN_ONLY
  }

  fun buildEnableBluetoothIntent(): Intent =
    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

  fun buildBluetoothSettingsIntent(): Intent =
    Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

  fun buildLocationSettingsIntent(): Intent =
    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
}
