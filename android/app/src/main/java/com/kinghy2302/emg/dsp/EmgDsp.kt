package com.kinghy2302.emg.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class AnalysisResult(
    val timeX: DoubleArray,
    val timeYUv: DoubleArray,
    val freqX: DoubleArray,
    val freqYUv: DoubleArray,
    val peakFreqHz: Double,
    val peakUv: Double,
    val peakToPeakUv: Double,
    val rmsUv: Double,
)

object EmgDsp {
    fun bandpass(data: DoubleArray): DoubleArray =
        SosFiltFilt.apply(SosCoefficients.BANDPASS, data)

    fun notch(data: DoubleArray): DoubleArray =
        SosFiltFilt.apply(SosCoefficients.NOTCH, data)

    fun hanning(n: Int): DoubleArray {
        if (n == 1) return doubleArrayOf(1.0)
        return DoubleArray(n) { i ->
            0.5 - 0.5 * cos(2.0 * PI * i / (n - 1).toDouble())
        }
    }

    fun computeFftClean(data: DoubleArray, fs: Int = EmgConstants.FS): Pair<DoubleArray, DoubleArray> {
        val n = data.size
        val mean = data.average()
        val window = hanning(n)
        val windowed = DoubleArray(n) { i -> (data[i] - mean) * window[i] }
        val (re, im) = Fft.fftReal(windowed)
        val scale = 2.0 / window.sum()
        val freqs = ArrayList<Double>(n / 2 + 1)
        val mag = ArrayList<Double>(n / 2 + 1)
        for (k in 0 until n) {
            val freq = fftFreq(k, n, fs)
            if (freq >= 0.0) {
                freqs.add(freq)
                mag.add(sqrt(re[k] * re[k] + im[k] * im[k]) * scale)
            }
        }
        return freqs.toDoubleArray() to mag.toDoubleArray()
    }

    fun computeRms(data: DoubleArray): Double {
        if (data.isEmpty()) return 0.0
        var acc = 0.0
        for (v in data) acc += v * v
        return sqrt(acc / data.size)
    }

    fun processWindow(rawMv: DoubleArray): AnalysisResult {
        require(rawMv.size == EmgConstants.SAMPLES) {
            "expected ${EmgConstants.SAMPLES} samples, got ${rawMv.size}"
        }
        val filtered = notch(bandpass(rawMv))
        val mean = filtered.average()
        val centeredUv = DoubleArray(filtered.size) { (filtered[it] - mean) * 1000.0 }

        val nView = EmgConstants.TIME_VIEW_SAMPLES
        val timeX = DoubleArray(nView) { it / EmgConstants.FS.toDouble() }
        val timeY = centeredUv.copyOf(nView)

        val (freqs, spec) = computeFftClean(filtered)
        var end = freqs.size
        for (i in freqs.indices) {
            if (freqs[i] > EmgConstants.SPECTRUM_MAX_HZ) {
                end = i
                break
            }
        }
        val freqX = freqs.copyOfRange(0, end)
        val freqY = DoubleArray(end) { spec[it] * 1000.0 }

        var peakIdx = 0
        var peakVal = if (freqY.isNotEmpty()) freqY[0] else 0.0
        for (i in 1 until freqY.size) {
            if (freqY[i] > peakVal) {
                peakVal = freqY[i]
                peakIdx = i
            }
        }
        val peakFreq = if (freqX.isNotEmpty()) freqX[peakIdx] else 0.0
        var minY = centeredUv[0]
        var maxY = centeredUv[0]
        for (v in centeredUv) {
            minY = min(minY, v)
            maxY = max(maxY, v)
        }
        return AnalysisResult(
            timeX = timeX,
            timeYUv = timeY,
            freqX = freqX,
            freqYUv = freqY,
            peakFreqHz = peakFreq,
            peakUv = peakVal,
            peakToPeakUv = maxY - minY,
            rmsUv = computeRms(centeredUv),
        )
    }

    internal fun fftFreq(k: Int, n: Int, fs: Int): Double {
        val half = n / 2
        val bin = if (k < half) k else k - n
        return bin * fs.toDouble() / n.toDouble()
    }

    fun paddedTimeRange(y: DoubleArray): Pair<Double, Double> {
        if (y.isEmpty()) return -1.0 to 1.0
        var minY = y[0]
        var maxY = y[0]
        for (v in y) {
            minY = min(minY, v)
            maxY = max(maxY, v)
        }
        var pad = (maxY - minY) * 0.15
        if (pad <= 1e-12) pad = 1.0
        return (minY - pad) to (maxY + pad)
    }

    fun spectrumYMax(y: DoubleArray): Double {
        var maxY = 0.0
        for (v in y) maxY = max(maxY, abs(v))
        if (maxY <= 1e-12) maxY = 1.0
        return maxY * 1.2
    }
}
