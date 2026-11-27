package com.example.ble

import androidx.compose.runtime.Immutable

/**
 * Zonas tácticas de proximidad derivadas del RSSI suavizado por filtro exponencial (EMA).
 */
enum class BleProximityZone(
  val label: String,
  val accentHex: Long,
  val minRssiInclusive: Float,
  val estimatedRangeLabel: String,
) {
  INMEDIATO(
    label = "Inmediato",
    accentHex = 0xFF34D399, // Verde esmeralda Veyra
    minRssiInclusive = -58f,
    estimatedRangeLabel = "< 2 m",
  ),
  CERCA(
    label = "Cerca",
    accentHex = 0xFFFB923C, // Naranja táctico Veyra
    minRssiInclusive = -72f,
    estimatedRangeLabel = "2 – 7 m",
  ),
  EN_ALCANCE(
    label = "En alcance",
    accentHex = 0xFF38BDF8, // Azul radar
    minRssiInclusive = -88f,
    estimatedRangeLabel = "7 – 25 m",
  ),
  FUERA_DE_RANGO(
    label = "Fuera de rango",
    accentHex = 0xFF94A3B8,
    minRssiInclusive = -125f,
    estimatedRangeLabel = "> 25 m",
  ),
}

/**
 * Perfil de escaneo BLE por ráfagas/ventanas para evitar drenar batería.
 */
enum class VeyraBleScanProfile(
  val label: String,
  val activeWindowMs: Long,
  val pauseIntervalMs: Long,
) {
  PAUSED(
    label = "Pausado",
    activeWindowMs = 0L,
    pauseIntervalMs = 0L,
  ),
  IDLE_LOW_POWER(
    label = "Low Power (reposo)",
    activeWindowMs = 3_500L,
    pauseIntervalMs = 11_000L,
  ),
  ACTIVE_RADAR_BALANCED(
    label = "Balanced (radar activo)",
    activeWindowMs = 5_500L,
    pauseIntervalMs = 4_500L,
  ),
}

/**
 * Estado operativo del hardware y permisos BLE sin lanzar SecurityException.
 */
enum class BleHardwareStatus(val label: String) {
  READY_FULL("Escaneo y anuncio BLE listos"),
  SCAN_ONLY("Solo escaneo BLE (chipset sin anuncio)"),
  BLUETOOTH_OFF("Bluetooth apagado"),
  PERMISSIONS_REQUIRED("Permisos Bluetooth requeridos"),
  LOCATION_REQUIRED_LEGACY("Activa Ubicación (Android <= 11)"),
  UNSUPPORTED("Hardware BLE no disponible"),
}

@Immutable
data class VeyraBleCapabilities(
  val hasBleHardware: Boolean = false,
  val isBluetoothSupported: Boolean = false,
  val isBluetoothEnabled: Boolean = false,
  val supportsAdvertising: Boolean = false,
  val hasScanPermission: Boolean = false,
  val hasAdvertisePermission: Boolean = false,
  val hasConnectPermission: Boolean = false,
  val requiresLocationServicesForBle: Boolean = false,
  val isLocationServiceEnabled: Boolean = true,
)

/**
 * Señal procesada y suavizada de un jugador o baliza cercana de Veyra por BLE.
 *
 * TODO [Conexión futura - Misiones e Interacciones Locales]:
 *  Usar `shortPlayerId`, `zone == INMEDIATO/CERCA` y `seekingHelp` para disparar
 *  misiones cooperativas presenciales o pulsos de ayuda locales sin servidor.
 * TODO [Conexión futura - Hitos por Nivel]:
 *  Leer `playerLevel` para mostrar insignias de rango/prestigio o desbloquear
 *  bonos cuando coincidan jugadores de nivel alto cerca.
 */
@Immutable
data class BlePeerSignal(
  val peerKey: String,
  val shortPlayerId: Int,
  val playerLevel: Int,
  val seekingHelp: Boolean,
  val hasActiveMission: Boolean,
  val rawRssi: Int,
  val smoothedRssi: Float,
  val estimatedDistanceMeters: Float,
  val zone: BleProximityZone,
  val bearingHintDegrees: Float,
  val lastSeenMillis: Long,
  val packetCount: Int,
  val isSimulated: Boolean = false,
  val displayLabel: String,
)

/**
 * Snapshot inmutable emitido con throttling (cada ~1.25 s) hacia Jetpack Compose y Canvas.
 */
@Immutable
data class VeyraBleUiSnapshot(
  val peers: List<BlePeerSignal> = emptyList(),
  val closestZone: BleProximityZone = BleProximityZone.FUERA_DE_RANGO,
  val scanProfile: VeyraBleScanProfile = VeyraBleScanProfile.PAUSED,
  val isBurstCurrentlyScanning: Boolean = false,
  val isAdvertisingActive: Boolean = false,
  val hardwareStatus: BleHardwareStatus = BleHardwareStatus.UNSUPPORTED,
  val capabilities: VeyraBleCapabilities = VeyraBleCapabilities(),
  val expiredCleanedCount: Int = 0,
  val lastSnapshotUpdatedMillis: Long = 0L,
  val lastStatusNote: String = "Módulo BLE en espera",
) {
  val activePeersCount: Int
    get() = peers.count { it.zone != BleProximityZone.FUERA_DE_RANGO }
}
