package com.example.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeyraBleModuleTest {

  @Test
  fun protocol_encodeDecodeRoundTrip_preservesIdentityAndFlags() {
    val shortId = VeyraBleProtocol.shortIdFromUser("veyra_ranger_77")
    val encoded = VeyraBleProtocol.encodeServiceData(
      shortPlayerId = shortId,
      playerLevel = 28,
      seekingHelp = true,
      hasActiveMission = true,
    )

    assertEquals(VeyraBleProtocol.PAYLOAD_SIZE_BYTES, encoded.size)

    val decoded = VeyraBleProtocol.decodeServiceData(encoded)
    assertNotNull(decoded)
    assertEquals(shortId, decoded!!.shortPlayerId)
    assertEquals(28, decoded.playerLevel)
    assertTrue(decoded.seekingHelp)
    assertTrue(decoded.hasActiveMission)
    assertEquals(1, decoded.protocolVersion)
  }

  @Test
  fun protocol_rejectsForeignOrCorruptedPayloads() {
    assertNull(VeyraBleProtocol.decodeServiceData(null))
    assertNull(VeyraBleProtocol.decodeServiceData(byteArrayOf(0x56, 0x10, 0x01)))
    assertNull(
      VeyraBleProtocol.decodeServiceData(
        byteArrayOf(0x11, 0x10, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05), // magic incorrecto
      ),
    )
    assertNull(
      VeyraBleProtocol.decodeServiceData(
        byteArrayOf(0x56, 0x20, 0x00, 0x00, 0x00, 0x01, 0x00, 0x05), // versión no soportada
      ),
    )
  }

  @Test
  fun signalProcessor_emaSmoothsRssiAndClassifiesZones() {
    var fakeTime = 1_000L
    val processor = VeyraBleSignalProcessor(
      emaAlpha = 0.30f,
      staleTimeoutMs = 15_000L,
      clockMillis = { fakeTime },
    )
    val packet = VeyraBleProtocol.DecodedVeyraPacket(
      shortPlayerId = 0x12345678,
      playerLevel = 9,
      seekingHelp = false,
      hasActiveMission = true,
      protocolVersion = 1,
    )

    processor.onPacketReceived(packet, rawRssi = -66) // Cerca
    fakeTime += 500L
    processor.onPacketReceived(packet, rawRssi = -92) // Salto brusco lejano

    val snapshot = processor.buildSnapshot(
      scanProfile = VeyraBleScanProfile.ACTIVE_RADAR_BALANCED,
      isBurstCurrentlyScanning = true,
      isAdvertisingActive = true,
      hardwareStatus = BleHardwareStatus.READY_FULL,
      capabilities = VeyraBleCapabilities(),
      statusNote = "ok",
    )

    assertEquals(1, snapshot.peers.size)
    val peer = snapshot.peers.first()
    // Con EMA 0.30: 0.30*(-92) + 0.70*(-66) = -73.8f -> aún amortiguado respecto a -92
    assertTrue("El RSSI suavizado debe amortiguar el salto brusco", peer.smoothedRssi > -80f)
    assertEquals(BleProximityZone.INMEDIATO, processor.classifyRssiZone(-50f))
    assertEquals(BleProximityZone.CERCA, processor.classifyRssiZone(-65f))
    assertEquals(BleProximityZone.EN_ALCANCE, processor.classifyRssiZone(-80f))
    assertEquals(BleProximityZone.FUERA_DE_RANGO, processor.classifyRssiZone(-98f))
  }

  @Test
  fun signalProcessor_expiresInactiveSignalsAndSupportsDebugSimulation() {
    var fakeTime = 10_000L
    val processor = VeyraBleSignalProcessor(
      staleTimeoutMs = 10_000L,
      clockMillis = { fakeTime },
    )

    // 1. Señal real activa
    processor.onPacketReceived(
      packet = VeyraBleProtocol.DecodedVeyraPacket(0x00ABCDEF, 5, false, false, 1),
      rawRssi = -54,
    )
    // 2. Señal simulada activa
    processor.injectSimulatedSignal(BleProximityZone.CERCA, simulateExpired = false)
    // 3. Señal simulada caducada
    processor.injectSimulatedSignal(BleProximityZone.EN_ALCANCE, simulateExpired = true)

    val snapshot1 = processor.buildSnapshot(
      scanProfile = VeyraBleScanProfile.IDLE_LOW_POWER,
      isBurstCurrentlyScanning = false,
      isAdvertisingActive = false,
      hardwareStatus = BleHardwareStatus.SCAN_ONLY,
      capabilities = VeyraBleCapabilities(),
      statusNote = "test",
    )
    assertEquals(2, snapshot1.peers.size)
    assertEquals(1, snapshot1.expiredCleanedCount)
    assertEquals(BleProximityZone.INMEDIATO, snapshot1.closestZone)

    // Limpiar solo simuladas mantiene la señal real
    processor.clearSimulatedOnly()
    val snapshot2 = processor.buildSnapshot(
      scanProfile = VeyraBleScanProfile.IDLE_LOW_POWER,
      isBurstCurrentlyScanning = false,
      isAdvertisingActive = false,
      hardwareStatus = BleHardwareStatus.SCAN_ONLY,
      capabilities = VeyraBleCapabilities(),
      statusNote = "test",
    )
    assertEquals(1, snapshot2.peers.size)
    assertFalse(snapshot2.peers.first().isSimulated)

    // Avanzar reloj más allá del TTL para verificar caducidad de señal real
    fakeTime += 11_000L
    val snapshot3 = processor.buildSnapshot(
      scanProfile = VeyraBleScanProfile.IDLE_LOW_POWER,
      isBurstCurrentlyScanning = false,
      isAdvertisingActive = false,
      hardwareStatus = BleHardwareStatus.SCAN_ONLY,
      capabilities = VeyraBleCapabilities(),
      statusNote = "test",
    )
    assertTrue(snapshot3.peers.isEmpty())
    assertEquals(2, snapshot3.expiredCleanedCount)
  }
}
