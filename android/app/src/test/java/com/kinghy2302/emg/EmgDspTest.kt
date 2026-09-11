package com.kinghy2302.emg

import com.kinghy2302.emg.demo.DemoSignal
import com.kinghy2302.emg.dsp.EmgConstants
import com.kinghy2302.emg.dsp.EmgDsp
import com.kinghy2302.emg.dsp.Fft
import com.kinghy2302.emg.protocol.EmgPacketParser
import com.kinghy2302.emg.protocol.EmgStreamAssembler
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class EmgDspTest {
    private val golden: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden_sine100.json")
            ?: error("missing golden_sine100.json")
        JSONObject(stream.bufferedReader().readText())
    }

    @Test
    fun packetMidscaleIsZeroMillivolts() {
        val packet = buildPacket(8388608)
        val result = EmgPacketParser.parse(packet)
        assertTrue(result.checksumOk)
        assertNotNull(result.millivolts)
        assertEquals(0.0, result.millivolts!!, 1e-12)
    }

    @Test
    fun packetChecksumFailureStillReturnsVoltage() {
        val good = buildPacket(8_488_608)
        val bad = good.copyOf()
        bad[bad.lastIndex] = (bad.last().toInt() xor 0x01).toByte()
        val result = EmgPacketParser.parse(bad)
        assertFalse(result.checksumOk)
        assertNotNull(result.millivolts)
        val expected = (8_488_608 - (1 shl 23)) * (1000.0 * 0.023) / (1 shl 23)
        assertEquals(expected, result.millivolts!!, 1e-12)
    }

    @Test
    fun packetRejectsNonAsciiPayload() {
        val packet = byteArrayOf(0x24, 0x20, 0x00, 0x01)
        val result = EmgPacketParser.parse(packet)
        assertNull(result.millivolts)
    }

    @Test
    fun streamAssemblerStripsCrLf() {
        val assembler = EmgStreamAssembler()
        val packet = buildPacket(8388608)
        val framed = packet + byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        val lines = assembler.feed(framed)
        assertEquals(1, lines.size)
        assertTrue(lines[0].contentEquals(packet))
        assertEquals(0.0, EmgPacketParser.parse(lines[0]).millivolts!!, 1e-12)
    }

    @Test
    fun fftImpulseIsOnes() {
        val x = DoubleArray(8) { if (it == 0) 1.0 else 0.0 }
        val (re, im) = Fft.fftReal(x)
        for (i in 0 until 8) {
            assertEquals("re[$i]", 1.0, re[i], 1e-12)
            assertEquals("im[$i]", 0.0, im[i], 1e-12)
        }
    }

    @Test
    fun bandpassThenNotchMatchesScipyFirstSamples() {
        val input = DemoSignal.window(0L)
        val afterBp = EmgDsp.bandpass(input)
        val afterNt = EmgDsp.notch(afterBp)
        val bpExpected = jsonArray("after_bandpass_first20")
        val ntExpected = jsonArray("after_notch_first20")
        for (i in 0 until 20) {
            assertEquals("bandpass[$i]", bpExpected[i], afterBp[i], 1e-9)
            assertEquals("notch[$i]", ntExpected[i], afterNt[i], 1e-9)
        }
        val rms = sqrt(afterNt.map { it * it }.average())
        assertEquals(golden.getDouble("after_notch_rms"), rms, 1e-9)
    }

    @Test
    fun processWindowDemoSineMatchesDesktopMetrics() {
        val result = EmgDsp.processWindow(DemoSignal.window(0L))
        assertEquals(100.0, result.peakFreqHz, 0.51)
        assertEquals(39.99968151112159, result.peakUv, 1e-6)
        assertEquals(94.72887038965067, result.peakToPeakUv, 1e-6)
        assertEquals(28.297813042137253, result.rmsUv, 1e-6)
        assertEquals(0.0, result.timeX.first(), 1e-12)
        assertEquals(0.1, result.timeX.last() + 1.0 / EmgConstants.FS, 1e-9)
        assertEquals(600.0, result.freqX.last(), 1e-9)
        val idx100 = result.freqX.indexOfFirst { abs(it - 100.0) < 1e-9 }
        val idx50 = result.freqX.indexOfFirst { abs(it - 50.0) < 1e-9 }
        assertTrue(result.freqYUv[idx100] > 30.0)
        assertTrue(result.freqYUv[idx50] < 1.0)
    }

    @Test
    fun timeAxisIsFirstHundredMilliseconds() {
        assertEquals(391, EmgConstants.TIME_VIEW_SAMPLES)
        val result = EmgDsp.processWindow(DemoSignal.window(0L))
        assertEquals(EmgConstants.TIME_VIEW_SAMPLES, result.timeX.size)
        assertEquals(EmgConstants.TIME_VIEW_SAMPLES, result.timeYUv.size)
    }

    private fun jsonArray(key: String): DoubleArray {
        val arr = golden.getJSONArray(key)
        return DoubleArray(arr.length()) { arr.getDouble(it) }
    }

    private fun buildPacket(adc: Int): ByteArray {
        val payload = "$$adc".toByteArray(Charsets.US_ASCII)
        val checksum = payload.fold(0) { acc, b -> acc + (b.toInt() and 0xFF) } and 0xFF
        return payload + checksum.toByte()
    }
}
