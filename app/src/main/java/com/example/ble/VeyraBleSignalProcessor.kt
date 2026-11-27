package com.example.ble

import kotlin.math.abs
import kotlin.math.pow

/**
 * Procesador puro en Kotlin (sin dependencias Android, 100% testeable en JVM) para:
 * - Suavizado exponencial (EMA) del RSSI y mitigación de saltos erráticos.
 * - Clasificación en zonas tácticas claras (Inmediato, Cerca, En alcance, Fuera de rango).
 * - Expiración automática (TTL) de señales inactivas.
 * - Inyección de señales BLE simuladas para pruebas con un solo dispositivo.
 */
class VeyraBleSignalProcessor(
  private val emaAlpha: Float = 0.32f,
  private val staleTimeoutMs: Long = 16_000L,
  private val txPowerAt1m: Float = -59f,
  private val pathLossExponent: Float = 2.4f,
  private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {

  private data class MutablePeerRecord(
    val peerKey: String,
    val shortPlayerId: Int,
    var playerLevel: Int,
    var seekingHelp: Boolean,
    var hasActiveMission: Boolean,
    var rawRssi: Int,
    var smoothedRssi: Float,
    var lastSeenMillis: Long,
    var packetCount: Int,
    var isSimulated: Boolean,
    var customLabel: String?,
  )

  private val lock = Any()
  private val activePeers = LinkedHashMap<String, MutablePeerRecord>()
  private var expiredCleanedTotal = 0
  private var debugCounter = 0

  fun onPacketReceived(
    packet: VeyraBleProtocol.DecodedVeyraPacket,
    rawRssi: Int,
    customLabel: String? = null,
    isSimulated: Boolean = false,
  ) {
    val now = clockMillis()
    val clampedRssi = rawRssi.coerceIn(-115, -20)
    val peerKey = if (isSimulated && customLabel != null) {
      "sim_${packet.shortPlayerId}"
    } else {
      "ble_${VeyraBleProtocol.formatShortTag(packet.shortPlayerId)}"
    }

    synchronized(lock) {
      val existing = activePeers[peerKey]
      if (existing == null) {
        activePeers[peerKey] = MutablePeerRecord(
          peerKey = peerKey,
          shortPlayerId = packet.shortPlayerId,
          playerLevel = packet.playerLevel,
          seekingHelp = packet.seekingHelp,
          hasActiveMission = packet.hasActiveMission,
          rawRssi = clampedRssi,
          smoothedRssi = clampedRssi.toFloat(),
          lastSeenMillis = now,
          packetCount = 1,
          isSimulated = isSimulated,
          customLabel = customLabel,
        )
      } else {
        existing.rawRssi = clampedRssi
        existing.smoothedRssi = emaAlpha * clampedRssi + (1f - emaAlpha) * existing.smoothedRssi
        existing.playerLevel = packet.playerLevel
        existing.seekingHelp = packet.seekingHelp
        existing.hasActiveMission = packet.hasActiveMission
        existing.lastSeenMillis = now
        existing.packetCount += 1
        existing.isSimulated = existing.isSimulated || isSimulated
        if (customLabel != null) {
          existing.customLabel = customLabel
        }
      }
    }
  }

  /**
   * Inyecta una señal BLE simulada para pruebas de campo con un único teléfono.
   * Si [simulateExpired] es true, marca el último visto anterior al TTL para validar su caducidad.
   */
  fun injectSimulatedSignal(
    zone: BleProximityZone,
    level: Int = 6,
    seekingHelp: Boolean = false,
    simulateExpired: Boolean = false,
  ): BlePeerSignal {
    val idx = ++debugCounter
    val targetRssi = when (zone) {
      BleProximityZone.INMEDIATO -> -48
      BleProximityZone.CERCA -> -66
      BleProximityZone.EN_ALCANCE -> -82
      BleProximityZone.FUERA_DE_RANGO -> -96
    }
    val shortId = 0x56450000 or (idx and 0xFFFF)
    val now = clockMillis()
    val timestamp = if (simulateExpired) now - staleTimeoutMs - 1_500L else now
    val label = when {
      simulateExpired -> "Simulado caducado #$idx"
      else -> "Eco ${zone.label} #$idx"
    }

    val peerKey = "sim_${VeyraBleProtocol.formatShortTag(shortId)}"
    synchronized(lock) {
      activePeers[peerKey] = MutablePeerRecord(
        peerKey = peerKey,
        shortPlayerId = shortId,
        playerLevel = level.coerceIn(1, 999),
        seekingHelp = seekingHelp,
        hasActiveMission = true,
        rawRssi = targetRssi,
        smoothedRssi = targetRssi.toFloat(),
        lastSeenMillis = timestamp,
        packetCount = 1,
        isSimulated = true,
        customLabel = label,
      )
      return toImmutableSignal(activePeers.getValue(peerKey))
    }
  }

  /**
   * Limpia señales inactivas que hayan superado [staleTimeoutMs] y contabiliza las caducadas.
   */
  fun sweepExpired(): Int {
    val now = clockMillis()
    var removed = 0
    synchronized(lock) {
      val iter = activePeers.entries.iterator()
      while (iter.hasNext()) {
        val entry = iter.next()
        if (now - entry.value.lastSeenMillis >= staleTimeoutMs) {
          iter.remove()
          removed++
        }
      }
      expiredCleanedTotal += removed
    }
    return removed
  }

  fun clearSimulatedOnly() {
    synchronized(lock) {
      val iter = activePeers.entries.iterator()
      while (iter.hasNext()) {
        if (iter.next().value.isSimulated) {
          iter.remove()
        }
      }
    }
  }

  fun clearAll() {
    synchronized(lock) {
      activePeers.clear()
    }
  }

  fun buildSnapshot(
    scanProfile: VeyraBleScanProfile,
    isBurstCurrentlyScanning: Boolean,
    isAdvertisingActive: Boolean,
    hardwareStatus: BleHardwareStatus,
    capabilities: VeyraBleCapabilities,
    statusNote: String,
  ): VeyraBleUiSnapshot {
    val now = clockMillis()
    synchronized(lock) {
      val iter = activePeers.entries.iterator()
      while (iter.hasNext()) {
        val entry = iter.next()
        if (now - entry.value.lastSeenMillis >= staleTimeoutMs) {
          iter.remove()
          expiredCleanedTotal++
        }
      }

      val sortedPeers = activePeers.values
        .map { toImmutableSignal(it) }
        .sortedByDescending { it.smoothedRssi }
        .take(12)

      val closest = sortedPeers.firstOrNull()?.zone ?: BleProximityZone.FUERA_DE_RANGO

      return VeyraBleUiSnapshot(
        peers = sortedPeers,
        closestZone = closest,
        scanProfile = scanProfile,
        isBurstCurrentlyScanning = isBurstCurrentlyScanning,
        isAdvertisingActive = isAdvertisingActive,
        hardwareStatus = hardwareStatus,
        capabilities = capabilities,
        expiredCleanedCount = expiredCleanedTotal,
        lastSnapshotUpdatedMillis = now,
        lastStatusNote = statusNote,
      )
    }
  }

  fun classifyRssiZone(smoothedRssi: Float): BleProximityZone = when {
    smoothedRssi >= BleProximityZone.INMEDIATO.minRssiInclusive -> BleProximityZone.INMEDIATO
    smoothedRssi >= BleProximityZone.CERCA.minRssiInclusive -> BleProximityZone.CERCA
    smoothedRssi >= BleProximityZone.EN_ALCANCE.minRssiInclusive -> BleProximityZone.EN_ALCANCE
    else -> BleProximityZone.FUERA_DE_RANGO
  }

  fun estimateDistanceMeters(smoothedRssi: Float): Float {
    val ratio = (txPowerAt1m - smoothedRssi) / (10f * pathLossExponent)
    return 10.0.pow(ratio.toDouble()).toFloat().coerceIn(0.4f, 60f)
  }

  private fun toImmutableSignal(rec: MutablePeerRecord): BlePeerSignal {
    val zone = classifyRssiZone(rec.smoothedRssi)
    val estMeters = estimateDistanceMeters(rec.smoothedRssi)
    val bearingHint = (abs(rec.shortPlayerId) % 360).toFloat()
    val label = rec.customLabel ?: "Veyra #${VeyraBleProtocol.formatShortTag(rec.shortPlayerId)}"
    return BlePeerSignal(
      peerKey = rec.peerKey,
      shortPlayerId = rec.shortPlayerId,
      playerLevel = rec.playerLevel,
      seekingHelp = rec.seekingHelp,
      hasActiveMission = rec.hasActiveMission,
      rawRssi = rec.rawRssi,
      smoothedRssi = rec.smoothedRssi,
      estimatedDistanceMeters = estMeters,
      zone = zone,
      bearingHintDegrees = bearingHint,
      lastSeenMillis = rec.lastSeenMillis,
      packetCount = rec.packetCount,
      isSimulated = rec.isSimulated,
      displayLabel = label,
    )
  }
}
