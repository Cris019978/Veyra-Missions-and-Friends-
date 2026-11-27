package com.example.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orquestador BLE desacoplado de la UI y 100% offline-first para Veyra:
 * - Escaneo por ráfagas/ventanas (activo unos segundos, pausado otro intervalo).
 * - ScanFilter exclusivo por VEYRA_SERVICE_UUID para ignorar dispositivos ajenos en hardware.
 * - Modo adaptable: SCAN_MODE_LOW_POWER en reposo y SCAN_MODE_BALANCED con el radar/mapa activo.
 * - Anuncio (Advertiser) ligero no conectable con fallback elegante a SCAN_ONLY.
 * - Publicación con throttling (~1.25 s) hacia StateFlow para proteger el Canvas de Jetpack Compose.
 */
class VeyraBleManager(
  context: Context,
  private val processor: VeyraBleSignalProcessor = VeyraBleSignalProcessor(),
  private val throttleIntervalMs: Long = 1_250L,
) {

  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val _snapshot = MutableStateFlow(VeyraBleUiSnapshot())
  val snapshot: StateFlow<VeyraBleUiSnapshot> = _snapshot.asStateFlow()

  private val stateLock = Any()
  private var isForeground: Boolean = false
  private var isRadarOrMapActive: Boolean = false
  private var wantBroadcastPresence: Boolean = true
  private var localUserId: String = "explorador"
  private var localPlayerLevel: Int = 1
  private var localSeekingHelp: Boolean = false
  private var localHasActiveMission: Boolean = false
  private var localShortPlayerId: Int = VeyraBleProtocol.shortIdFromUser(localUserId)

  private var currentProfile: VeyraBleScanProfile = VeyraBleScanProfile.PAUSED
  private var isBurstScanningNow: Boolean = false
  private var isAdvertisingNow: Boolean = false
  private var currentCapabilities: VeyraBleCapabilities = VeyraBleCapabilities()
  private var currentHardwareStatus: BleHardwareStatus = BleHardwareStatus.UNSUPPORTED
  private var currentStatusNote: String = "Inicializando radar BLE"
  private var hasPendingChanges: Boolean = false

  private var burstScanJob: Job? = null
  private var throttleJob: Job? = null
  private var activeScanner: BluetoothLeScanner? = null
  private var activeAdvertiser: BluetoothLeAdvertiser? = null
  private var activeAdvertiseCallback: AdvertiseCallback? = null
  private var isReceiverRegistered: Boolean = false

  private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      handleScanResult(result)
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>) {
      for (res in results) {
        handleScanResult(res)
      }
    }

    override fun onScanFailed(errorCode: Int) {
      synchronized(stateLock) {
        isBurstScanningNow = false
        currentStatusNote = "Escaneo pausado (código $errorCode)"
        hasPendingChanges = true
      }
    }
  }

  private val systemStateReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
      val action = intent?.action ?: return
      if (action == BluetoothAdapter.ACTION_STATE_CHANGED || action == LocationManager.MODE_CHANGED_ACTION) {
        refreshAndApply()
      }
    }
  }

  init {
    registerSystemReceiver()
    startThrottledPublisherLoop()
    refreshAndApply()
  }

  fun updateRuntimeState(
    foreground: Boolean,
    radarOrMapActive: Boolean,
    broadcastPresence: Boolean,
    userId: String,
    playerLevel: Int,
    seekingHelp: Boolean,
    hasActiveMission: Boolean,
  ) {
    val identityChanged: Boolean
    synchronized(stateLock) {
      val newShortId = VeyraBleProtocol.shortIdFromUser(userId)
      identityChanged = (newShortId != localShortPlayerId) ||
        (playerLevel != localPlayerLevel) ||
        (seekingHelp != localSeekingHelp) ||
        (hasActiveMission != localHasActiveMission) ||
        (broadcastPresence != wantBroadcastPresence) ||
        (foreground != isForeground)

      isForeground = foreground
      isRadarOrMapActive = radarOrMapActive
      wantBroadcastPresence = broadcastPresence
      localUserId = userId
      localShortPlayerId = newShortId
      localPlayerLevel = playerLevel.coerceAtLeast(1)
      localSeekingHelp = seekingHelp
      localHasActiveMission = hasActiveMission
    }
    refreshAndApply(restartAdvertising = identityChanged)
  }

  fun setForeground(foreground: Boolean) {
    val changed = synchronized(stateLock) {
      val c = isForeground != foreground
      isForeground = foreground
      c
    }
    if (changed) {
      refreshAndApply(restartAdvertising = true)
    }
  }

  fun refreshAndApply(restartAdvertising: Boolean = false) {
    scope.launch {
      val caps = VeyraBlePermissions.inspectCapabilities(appContext)
      val hwStatus = VeyraBlePermissions.resolveHardwareStatus(caps)

      val targetProfile: VeyraBleScanProfile
      val shouldAdvertise: Boolean
      val canScan = caps.hasBleHardware &&
        caps.isBluetoothEnabled &&
        caps.hasScanPermission &&
        (!caps.requiresLocationServicesForBle || caps.isLocationServiceEnabled)

      synchronized(stateLock) {
        currentCapabilities = caps
        currentHardwareStatus = hwStatus
        targetProfile = when {
          !isForeground || !canScan -> VeyraBleScanProfile.PAUSED
          isRadarOrMapActive -> VeyraBleScanProfile.ACTIVE_RADAR_BALANCED
          else -> VeyraBleScanProfile.IDLE_LOW_POWER
        }
        shouldAdvertise = isForeground &&
          wantBroadcastPresence &&
          caps.isBluetoothEnabled &&
          caps.supportsAdvertising &&
          caps.hasAdvertisePermission

        currentStatusNote = when (hwStatus) {
          BleHardwareStatus.UNSUPPORTED -> "Dispositivo sin hardware BLE"
          BleHardwareStatus.PERMISSIONS_REQUIRED -> "Concede permisos Bluetooth para detectar señales cercanas"
          BleHardwareStatus.BLUETOOTH_OFF -> "Activa Bluetooth para escanear señales Veyra"
          BleHardwareStatus.LOCATION_REQUIRED_LEGACY -> "Activa Ubicación del sistema (requerido en Android <= 11)"
          BleHardwareStatus.SCAN_ONLY -> "Modo solo escaneo activo (sin anuncio BLE en este equipo)"
          BleHardwareStatus.READY_FULL -> if (isRadarOrMapActive) {
            "Radar BLE equilibrado por ráfagas (${targetProfile.activeWindowMs / 1000}s on / ${targetProfile.pauseIntervalMs / 1000}s pausa)"
          } else {
            "BLE en reposo Low Power por ráfagas"
          }
        }
      }

      reconfigureBurstScanner(targetProfile)
      reconfigureAdvertising(shouldAdvertise, forceRestart = restartAdvertising)
      publishSnapshotNow()
    }
  }

  fun injectDebugSignal(
    zone: BleProximityZone,
    simulateExpired: Boolean = false,
    seekingHelp: Boolean = false,
  ) {
    val level = when (zone) {
      BleProximityZone.INMEDIATO -> 12
      BleProximityZone.CERCA -> 7
      BleProximityZone.EN_ALCANCE -> 4
      BleProximityZone.FUERA_DE_RANGO -> 2
    }
    processor.injectSimulatedSignal(
      zone = zone,
      level = level,
      seekingHelp = seekingHelp,
      simulateExpired = simulateExpired,
    )
    synchronized(stateLock) {
      currentStatusNote = if (simulateExpired) {
        "Señal BLE simulada vencida — limpiada por TTL"
      } else {
        "Señal BLE simulada (${zone.label}) inyectada"
      }
      hasPendingChanges = true
    }
    publishSnapshotNow()
  }

  fun clearDebugSignals() {
    processor.clearSimulatedOnly()
    synchronized(stateLock) {
      currentStatusNote = "Señales BLE simuladas eliminadas"
      hasPendingChanges = true
    }
    publishSnapshotNow()
  }

  fun release() {
    stopBurstScanInternal()
    stopAdvertisingInternal()
    burstScanJob?.cancel()
    throttleJob?.cancel()
    unregisterSystemReceiver()
    scope.cancel()
  }

  private fun handleScanResult(result: ScanResult) {
    val serviceData = result.scanRecord?.getServiceData(VeyraBleProtocol.VEYRA_SERVICE_UUID) ?: return
    val decoded = VeyraBleProtocol.decodeServiceData(serviceData) ?: return

    val ownId = synchronized(stateLock) { localShortPlayerId }
    if (decoded.shortPlayerId == ownId) return

    processor.onPacketReceived(
      packet = decoded,
      rawRssi = result.rssi,
    )
    synchronized(stateLock) {
      hasPendingChanges = true
    }
  }

  private fun reconfigureBurstScanner(targetProfile: VeyraBleScanProfile) {
    val needsRestart = synchronized(stateLock) {
      val changed = currentProfile != targetProfile
      currentProfile = targetProfile
      changed || (targetProfile != VeyraBleScanProfile.PAUSED && burstScanJob?.isActive != true)
    }
    if (!needsRestart) return

    burstScanJob?.cancel()
    stopBurstScanInternal()

    if (targetProfile == VeyraBleScanProfile.PAUSED) {
      synchronized(stateLock) {
        isBurstScanningNow = false
      }
      return
    }

    burstScanJob = scope.launch {
      val scanMode = if (targetProfile == VeyraBleScanProfile.ACTIVE_RADAR_BALANCED) {
        ScanSettings.SCAN_MODE_BALANCED
      } else {
        ScanSettings.SCAN_MODE_LOW_POWER
      }

      while (isActive) {
        val started = startSingleScanBurst(scanMode)
        synchronized(stateLock) {
          isBurstScanningNow = started
          hasPendingChanges = true
        }
        publishSnapshotNow()

        if (!started) {
          // Espera segura antes de reintentar si el adaptador está ocupado o sin permiso
          delay(5_000L)
          continue
        }

        delay(targetProfile.activeWindowMs)
        stopBurstScanInternal()
        synchronized(stateLock) {
          isBurstScanningNow = false
          hasPendingChanges = true
        }
        publishSnapshotNow()

        delay(targetProfile.pauseIntervalMs)
      }
    }
  }

  private fun startSingleScanBurst(scanMode: Int): Boolean {
    return try {
      val manager = appContext.getSystemService(BluetoothManager::class.java) ?: return false
      val adapter = manager.adapter ?: return false
      if (!adapter.isEnabled) return false
      val scanner = adapter.bluetoothLeScanner ?: return false
      activeScanner = scanner

      val settings = ScanSettings.Builder()
        .setScanMode(scanMode)
        .setReportDelay(0L)
        .build()

      scanner.startScan(VeyraBleProtocol.buildScanFilters(), settings, scanCallback)
      true
    } catch (_: SecurityException) {
      synchronized(stateLock) {
        currentHardwareStatus = BleHardwareStatus.PERMISSIONS_REQUIRED
        currentStatusNote = "Permiso BLUETOOTH_SCAN / Ubicación requerido"
      }
      false
    } catch (_: Throwable) {
      false
    }
  }

  private fun stopBurstScanInternal() {
    val scanner = activeScanner ?: return
    try {
      scanner.stopScan(scanCallback)
    } catch (_: SecurityException) {
      // Ignorado de forma segura si el usuario revocó el permiso en caliente
    } catch (_: Throwable) {
      // Ignorado si Bluetooth se apagó durante la ventana
    } finally {
      activeScanner = null
    }
  }

  private fun reconfigureAdvertising(shouldAdvertise: Boolean, forceRestart: Boolean) {
    if (!shouldAdvertise) {
      stopAdvertisingInternal()
      return
    }
    if (isAdvertisingNow && !forceRestart) return
    stopAdvertisingInternal()
    startAdvertisingInternal(includeServiceData = true)
  }

  private fun startAdvertisingInternal(includeServiceData: Boolean) {
    try {
      val manager = appContext.getSystemService(BluetoothManager::class.java) ?: return
      val adapter = manager.adapter ?: return
      if (!adapter.isEnabled || !adapter.isMultipleAdvertisementSupported) return
      val advertiser = adapter.bluetoothLeAdvertiser ?: return

      val payload = synchronized(stateLock) {
        VeyraBleProtocol.encodeServiceData(
          shortPlayerId = localShortPlayerId,
          playerLevel = localPlayerLevel,
          seekingHelp = localSeekingHelp,
          hasActiveMission = localHasActiveMission,
        )
      }

      val settings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
        .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
        .setConnectable(false)
        .setTimeout(0)
        .build()

      // Primary packet: solo ServiceUuid (21 bytes <= límite BLE de 31 bytes)
      val primaryData = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addServiceUuid(VeyraBleProtocol.VEYRA_SERVICE_UUID)
        .build()

      // ScanResponse: ServiceData compacto de 8 bytes (26 bytes <= 31 bytes)
      val scanResponse = if (includeServiceData) {
        AdvertiseData.Builder()
          .setIncludeDeviceName(false)
          .setIncludeTxPowerLevel(false)
          .addServiceData(VeyraBleProtocol.VEYRA_SERVICE_UUID, payload)
          .build()
      } else {
        null
      }

      val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
          synchronized(stateLock) {
            isAdvertisingNow = true
            hasPendingChanges = true
          }
          publishSnapshotNow()
        }

        override fun onStartFailure(errorCode: Int) {
          if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE && includeServiceData) {
            // Fallback elegante en chipsets estrictos: anunciar al menos el UUID
            startAdvertisingInternal(includeServiceData = false)
            return
          }
          synchronized(stateLock) {
            isAdvertisingNow = false
            currentStatusNote = "Anuncio BLE no disponible ($errorCode) • Solo escaneo"
            hasPendingChanges = true
          }
          publishSnapshotNow()
        }
      }

      activeAdvertiser = advertiser
      activeAdvertiseCallback = callback
      if (scanResponse != null) {
        advertiser.startAdvertising(settings, primaryData, scanResponse, callback)
      } else {
        advertiser.startAdvertising(settings, primaryData, callback)
      }
    } catch (_: SecurityException) {
      synchronized(stateLock) {
        isAdvertisingNow = false
      }
    } catch (_: Throwable) {
      synchronized(stateLock) {
        isAdvertisingNow = false
      }
    }
  }

  private fun stopAdvertisingInternal() {
    val advertiser = activeAdvertiser
    val callback = activeAdvertiseCallback
    if (advertiser != null && callback != null) {
      try {
        advertiser.stopAdvertising(callback)
      } catch (_: SecurityException) {
        // Ignorado de forma segura
      } catch (_: Throwable) {
        // Ignorado si Bluetooth se apagó
      }
    }
    activeAdvertiser = null
    activeAdvertiseCallback = null
    synchronized(stateLock) {
      isAdvertisingNow = false
    }
  }

  private fun startThrottledPublisherLoop() {
    throttleJob?.cancel()
    throttleJob = scope.launch {
      while (isActive) {
        delay(throttleIntervalMs)
        val removed = processor.sweepExpired()
        val shouldEmit = synchronized(stateLock) {
          val emit = hasPendingChanges || removed > 0
          hasPendingChanges = false
          emit
        }
        if (shouldEmit) {
          publishSnapshotNow()
        }
      }
    }
  }

  private fun publishSnapshotNow() {
    val (profile, scanning, advertising, hwStatus, caps, note) = synchronized(stateLock) {
      hasPendingChanges = false
      SnapshotMeta(
        profile = currentProfile,
        scanning = isBurstScanningNow,
        advertising = isAdvertisingNow,
        hwStatus = currentHardwareStatus,
        caps = currentCapabilities,
        note = currentStatusNote,
      )
    }
    _snapshot.value = processor.buildSnapshot(
      scanProfile = profile,
      isBurstCurrentlyScanning = scanning,
      isAdvertisingActive = advertising,
      hardwareStatus = hwStatus,
      capabilities = caps,
      statusNote = note,
    )
  }

  private fun registerSystemReceiver() {
    if (isReceiverRegistered) return
    val filter = IntentFilter().apply {
      addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
      addAction(LocationManager.MODE_CHANGED_ACTION)
    }
    runCatching {
      ContextCompat.registerReceiver(
        appContext,
        systemStateReceiver,
        filter,
        ContextCompat.RECEIVER_NOT_EXPORTED,
      )
      isReceiverRegistered = true
    }
  }

  private fun unregisterSystemReceiver() {
    if (!isReceiverRegistered) return
    runCatching {
      appContext.unregisterReceiver(systemStateReceiver)
    }
    isReceiverRegistered = false
  }

  private data class SnapshotMeta(
    val profile: VeyraBleScanProfile,
    val scanning: Boolean,
    val advertising: Boolean,
    val hwStatus: BleHardwareStatus,
    val caps: VeyraBleCapabilities,
    val note: String,
  )
}
