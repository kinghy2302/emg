package com.kinghy2302.emg.dsp

import kotlin.math.min

/**
 * SciPy-compatible cascaded second-order-section filtfilt (`sosfiltfilt`).
 *
 * Matches `scipy.signal.sosfiltfilt` defaults: odd padding, padlen = 3 * ntaps,
 * DF-II transposed `sosfilt`, and `sosfilt_zi` initial conditions.
 */
object SosFiltFilt {
    fun apply(sos: Array<DoubleArray>, x: DoubleArray): DoubleArray {
        require(x.isNotEmpty()) { "signal must not be empty" }
        require(sos.isNotEmpty()) { "sos must not be empty" }

        val nSections = sos.size
        var ntaps = 2 * nSections + 1
        val b2Zero = sos.count { it[2] == 0.0 }
        val a2Zero = sos.count { it[5] == 0.0 }
        ntaps -= min(b2Zero, a2Zero)
        val edge = ntaps * 3
        require(x.size > edge) { "signal too short for filtfilt padlen=$edge" }

        val ext = oddExtend(x, edge)
        val zi = sosFiltZi(sos)
        val x0 = ext.first()
        var y = sosFilt(sos, ext, scaleZi(zi, x0))
        val y0 = y.last()
        y = y.reversedArray()
        y = sosFilt(sos, y, scaleZi(zi, y0))
        y = y.reversedArray()
        return y.copyOfRange(edge, y.size - edge)
    }

    internal fun oddExtend(x: DoubleArray, n: Int): DoubleArray {
        if (n < 1) return x.copyOf()
        require(n <= x.size - 1) { "extension length $n is too big for ${x.size} samples" }
        val out = DoubleArray(x.size + 2 * n)
        val leftEnd = x[0]
        val rightEnd = x[x.lastIndex]
        for (i in 0 until n) {
            out[i] = 2.0 * leftEnd - x[n - i]
        }
        x.copyInto(out, destinationOffset = n)
        for (i in 0 until n) {
            out[n + x.size + i] = 2.0 * rightEnd - x[x.lastIndex - 1 - i]
        }
        return out
    }

    internal fun lfilterZi(b: DoubleArray, a: DoubleArray): DoubleArray {
        val a0 = a[0]
        val a1 = a[1] / a0
        val a2 = a[2] / a0
        val b0 = b[0] / a0
        val b1 = b[1] / a0
        val b2 = b[2] / a0
        val b0zi = b1 - a1 * b0
        val b1zi = b2 - a2 * b0
        val denom = 1.0 + a1 + a2
        val zi0 = (b0zi + b1zi) / denom
        val zi1 = b1zi - a2 * zi0
        return doubleArrayOf(zi0, zi1)
    }

    internal fun sosFiltZi(sos: Array<DoubleArray>): Array<DoubleArray> {
        val zi = Array(sos.size) { DoubleArray(2) }
        var scale = 1.0
        for (i in sos.indices) {
            val b = doubleArrayOf(sos[i][0], sos[i][1], sos[i][2])
            val a = doubleArrayOf(sos[i][3], sos[i][4], sos[i][5])
            val sectionZi = lfilterZi(b, a)
            zi[i][0] = scale * sectionZi[0]
            zi[i][1] = scale * sectionZi[1]
            scale *= (b[0] + b[1] + b[2]) / (a[0] + a[1] + a[2])
        }
        return zi
    }

    internal fun sosFilt(
        sos: Array<DoubleArray>,
        x: DoubleArray,
        zi: Array<DoubleArray>,
    ): DoubleArray {
        val y = x.copyOf()
        for (s in sos.indices) {
            val a0 = sos[s][3]
            val b0 = sos[s][0] / a0
            val b1 = sos[s][1] / a0
            val b2 = sos[s][2] / a0
            val a1 = sos[s][4] / a0
            val a2 = sos[s][5] / a0
            var z0 = zi[s][0]
            var z1 = zi[s][1]
            for (i in y.indices) {
                val xin = y[i]
                val yout = b0 * xin + z0
                z0 = b1 * xin - a1 * yout + z1
                z1 = b2 * xin - a2 * yout
                y[i] = yout
            }
        }
        return y
    }

    private fun scaleZi(zi: Array<DoubleArray>, scale: Double): Array<DoubleArray> {
        return Array(zi.size) { i ->
            doubleArrayOf(zi[i][0] * scale, zi[i][1] * scale)
        }
    }
}
