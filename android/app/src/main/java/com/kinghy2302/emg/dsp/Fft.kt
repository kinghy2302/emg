package com.kinghy2302.emg.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unnormalized forward DFT matching `numpy.fft.fft` for real (or complex) input.
 * Power-of-two lengths use radix-2 FFT; other lengths use Bluestein's algorithm.
 */
object Fft {
    fun fftReal(x: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val re = x.copyOf()
        val im = DoubleArray(x.size)
        fftInPlace(re, im)
        return re to im
    }

    internal fun fftInPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(im.size == n)
        if (n <= 1) return
        if (n and (n - 1) == 0) {
            fftRadix2InPlace(re, im)
        } else {
            bluestein(re, im)
        }
    }

    internal fun fftRadix2InPlace(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]
                re[i] = re[j]
                re[j] = tr
                val ti = im[i]
                im[i] = im[j]
                im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenRe = cos(ang)
            val wlenIm = sin(ang)
            var i = 0
            while (i < n) {
                var wRe = 1.0
                var wIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + half] * wRe - im[i + k + half] * wIm
                    val vi = re[i + k + half] * wIm + im[i + k + half] * wRe
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + half] = ur - vr
                    im[i + k + half] = ui - vi
                    val nwr = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nwr
                }
                i += len
            }
            len *= 2
        }
    }

    private fun bluestein(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        val m = nextPow2(2 * n - 1)
        val chirpRe = DoubleArray(n)
        val chirpIm = DoubleArray(n)
        for (i in 0 until n) {
            val angle = PI * i.toDouble() * i.toDouble() / n.toDouble()
            chirpRe[i] = cos(angle)
            chirpIm[i] = sin(angle)
        }

        val aRe = DoubleArray(m)
        val aIm = DoubleArray(m)
        for (i in 0 until n) {
            val xr = re[i]
            val xi = im[i]
            aRe[i] = xr * chirpRe[i] + xi * chirpIm[i]
            aIm[i] = xi * chirpRe[i] - xr * chirpIm[i]
        }

        val bRe = DoubleArray(m)
        val bIm = DoubleArray(m)
        bRe[0] = chirpRe[0]
        bIm[0] = chirpIm[0]
        for (i in 1 until n) {
            bRe[i] = chirpRe[i]
            bIm[i] = chirpIm[i]
            bRe[m - i] = chirpRe[i]
            bIm[m - i] = chirpIm[i]
        }

        fftRadix2InPlace(aRe, aIm)
        fftRadix2InPlace(bRe, bIm)
        for (i in 0 until m) {
            val pr = aRe[i] * bRe[i] - aIm[i] * bIm[i]
            val pi = aRe[i] * bIm[i] + aIm[i] * bRe[i]
            aRe[i] = pr
            aIm[i] = pi
        }
        for (i in 0 until m) {
            aIm[i] = -aIm[i]
        }
        fftRadix2InPlace(aRe, aIm)
        val inv = 1.0 / m
        for (i in 0 until m) {
            aRe[i] *= inv
            aIm[i] = -aIm[i] * inv
        }

        for (i in 0 until n) {
            val yr = aRe[i]
            val yi = aIm[i]
            re[i] = yr * chirpRe[i] + yi * chirpIm[i]
            im[i] = yi * chirpRe[i] - yr * chirpIm[i]
        }
    }

    private fun nextPow2(n: Int): Int {
        var v = 1
        while (v < n) {
            v = v shl 1
        }
        return v
    }
}
