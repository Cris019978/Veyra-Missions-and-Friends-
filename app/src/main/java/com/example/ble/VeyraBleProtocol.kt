package com.example.ble

import android.bluetooth.le.ScanFilter
import android.os.ParcelUuid
import java.util.UUID

/**
 * Protocolo compacto BLE exclusivo para Veyra: Missions and Friends.
 * - UUID propio de 128 bits para filtrar a nivel de controlador hardware (`ScanFilter`)
 *   y descartar al instante cualquier dispositivo Bluetooth ajeno.
 * - Service Data de 8 bytes (cabe holgadamente en ScanResponse <= 31 bytes sin exponer PII).
 */
object VeyraBleProtocol {

  val VEYRA_SERVICE_UUID: ParcelUuid = ParcelUuid(
    UUID.fromString("7e5a0001-8c3b-4d72-9a10-564559524101"),
  )

  private const val MAGIC_BYTE: Byte = 0x56 // 'V'
  private const val PROTOCOL_VERSION: Int = 1
  const val PAYLOAD_SIZE_BYTES: Int = 8

  private const val FLAG_SEEKING_HELP: Int = 0x01
  private const val FLAG_ACTIVE_MISSION: Int = 0x02

  data class DecodedVeyraPacket(
    val shortPlayerId: Int,
    val playerLevel: Int,
    val seekingHelp: Boolean,
    val hasActiveMission: Boolean,
    val protocolVersion: Int,
  )

  /**
   * Hash determinista FNV-1a de 32 bits para representar al jugador sin transmitir emails ni nombres.
   */
  fun shortIdFromUser(userId: String): Int {
    val clean = userId.trim().ifEmpty { "veyra_explorer" }
    var hash = -0x7ee3623b // 0x811c9dc5
    for (ch in clean) {
      hash = hash xor ch.code
      hash *= 0x01000193
    }
    return if (hash == 0) 0x56595201 else hash
  }

  /**
   * Codifica exactamente 8 bytes compactos:
   * [0] Magic 'V' (0x56)
   * [1] Versión (bits altos 4..7) + Flags (bits 0..3)
   * [2..5] shortPlayerId (big-endian Int32)
   * [6..7] playerLevel (big-endian UInt16, 1..65535)
   */
  fun encodeServiceData(
    shortPlayerId: Int,
    playerLevel: Int,
    seekingHelp: Boolean,
    hasActiveMission: Boolean,
  ): ByteArray {
    val clampedLevel = playerLevel.coerceIn(1, 65_535)
    var flags = (PROTOCOL_VERSION and 0x0F) shl 4
    if (seekingHelp) flags = flags or FLAG_SEEKING_HELP
    if (hasActiveMission) flags = flags or FLAG_ACTIVE_MISSION

    return byteArrayOf(
      MAGIC_BYTE,
      flags.toByte(),
      ((shortPlayerId ushr 24) and 0xFF).toByte(),
      ((shortPlayerId ushr 16) and 0xFF).toByte(),
      ((shortPlayerId ushr 8) and 0xFF).toByte(),
      (shortPlayerId and 0xFF).toByte(),
      ((clampedLevel ushr 8) and 0xFF).toByte(),
      (clampedLevel and 0xFF).toByte(),
    )
  }

  /**
   * Decodifica y valida estrictamente el paquete Veyra; retorna null ante cualquier payload ajeno o corrupto.
   */
  fun decodeServiceData(bytes: ByteArray?): DecodedVeyraPacket? {
    if (bytes == null || bytes.size < PAYLOAD_SIZE_BYTES) return null
    if (bytes[0] != MAGIC_BYTE) return null

    val flagByte = bytes[1].toInt() and 0xFF
    val version = (flagByte ushr 4) and 0x0F
    if (version != PROTOCOL_VERSION) return null

    val seekingHelp = (flagByte and FLAG_SEEKING_HELP) != 0
    val hasActiveMission = (flagByte and FLAG_ACTIVE_MISSION) != 0

    val shortId = ((bytes[2].toInt() and 0xFF) shl 24) or
      ((bytes[3].toInt() and 0xFF) shl 16) or
      ((bytes[4].toInt() and 0xFF) shl 8) or
      (bytes[5].toInt() and 0xFF)

    val level = ((bytes[6].toInt() and 0xFF) shl 8) or
      (bytes[7].toInt() and 0xFF)

    return DecodedVeyraPacket(
      shortPlayerId = shortId,
      playerLevel = level.coerceAtLeast(1),
      seekingHelp = seekingHelp,
      hasActiveMission = hasActiveMission,
      protocolVersion = version,
    )
  }

  /**
   * Filtro hardware estricto por UUID exclusivo de Veyra para despertar solo con paquetes propios.
   */
  fun buildScanFilters(): List<ScanFilter> = listOf(
    ScanFilter.Builder()
      .setServiceUuid(VEYRA_SERVICE_UUID)
      .build(),
  )

  fun formatShortTag(shortPlayerId: Int): String =
    "%04X".format(shortPlayerId and 0xFFFF)
}
