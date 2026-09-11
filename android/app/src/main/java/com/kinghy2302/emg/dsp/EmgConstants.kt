package com.kinghy2302.emg.dsp

object EmgConstants {
    const val FS: Int = 3910
    const val SAMPLES: Int = FS
    const val TIME_VIEW_S: Double = 0.1
    const val TIME_VIEW_SAMPLES: Int = 391
    const val SPECTRUM_MAX_HZ: Double = 600.0
    const val BAUD_RATE: Int = 115200
    const val ADC_MID: Int = 1 shl 23
    const val ADC_FULL_SCALE_V: Double = 0.023
    const val PACKET_START: Byte = 0x24
    const val DEMO_SINE_HZ: Double = 100.0
    const val DEMO_SINE_UV_PP: Double = 80.0
}
