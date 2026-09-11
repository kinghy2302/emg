package com.kinghy2302.emg.demo

import com.kinghy2302.emg.dsp.EmgConstants
import kotlin.math.PI
import kotlin.math.sin

object DemoSignal {
    const val LABEL = "演示信号 (100 Hz)"

    fun sampleMv(index: Long): Double {
        val t = index.toDouble() / EmgConstants.FS
        val amplitudeMv = EmgConstants.DEMO_SINE_UV_PP / 2.0 / 1000.0
        return amplitudeMv * sin(2.0 * PI * EmgConstants.DEMO_SINE_HZ * t)
    }

    fun window(startIndex: Long, count: Int = EmgConstants.SAMPLES): DoubleArray {
        return DoubleArray(count) { i -> sampleMv(startIndex + i) }
    }
}
