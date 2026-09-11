package com.kinghy2302.emg.protocol

import com.kinghy2302.emg.dsp.EmgConstants

data class PacketParseResult(
    val millivolts: Double?,
    val checksumOk: Boolean,
    val error: String? = null,
)

object EmgPacketParser {
    fun parse(packet: ByteArray): PacketParseResult {
        if (packet.size < 4) {
            return PacketParseResult(null, checksumOk = false, error = "packet too short")
        }
        if (packet[0] != EmgConstants.PACKET_START) {
            return PacketParseResult(null, checksumOk = false, error = "missing \$ start")
        }
        var sum = 0
        for (i in 0 until packet.size - 1) {
            sum += packet[i].toInt() and 0xFF
        }
        val checksum = sum and 0xFF
        val last = packet[packet.size - 1].toInt() and 0xFF
        val checksumOk = checksum == last
        val dataBytes = packet.copyOfRange(1, packet.size - 1)
        val ascii = try {
            String(dataBytes, Charsets.US_ASCII)
        } catch (e: Exception) {
            return PacketParseResult(null, checksumOk, "解析错误: ${e.message}")
        }
        val adcVal = ascii.toIntOrNull()
            ?: return PacketParseResult(null, checksumOk, "解析错误: '$ascii'")
        val voltageMv = adcToMillivolts(adcVal)
        return PacketParseResult(voltageMv, checksumOk)
    }

    fun adcToMillivolts(adcVal: Int): Double {
        val mid = EmgConstants.ADC_MID.toDouble()
        return (adcVal - mid) * (1000.0 * EmgConstants.ADC_FULL_SCALE_V) / mid
    }

    fun checksumFailureMessage(computed: Int, actual: Int): String =
        "校验失败！$computed,$actual"
}

/**
 * Line-oriented packet assembler matching desktop `serial.readline()`.
 * Newline (`\n`) terminates a packet; `\r` is stripped.
 */
class EmgStreamAssembler(private val maxPacketBytes: Int = 256) {
    private val buffer = ArrayList<Byte>(64)

    fun feed(data: ByteArray, length: Int = data.size): List<ByteArray> {
        val packets = ArrayList<ByteArray>()
        for (i in 0 until length) {
            val b = data[i]
            if (b == '\n'.code.toByte()) {
                if (buffer.isNotEmpty() && buffer.last() == '\r'.code.toByte()) {
                    buffer.removeAt(buffer.lastIndex)
                }
                if (buffer.isNotEmpty()) {
                    packets.add(buffer.toByteArray())
                }
                buffer.clear()
            } else {
                if (buffer.size < maxPacketBytes) {
                    buffer.add(b)
                } else {
                    buffer.clear()
                }
            }
        }
        return packets
    }

    fun reset() {
        buffer.clear()
    }
}
