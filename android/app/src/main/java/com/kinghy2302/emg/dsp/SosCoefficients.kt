package com.kinghy2302.emg.dsp

/**
 * SOS coefficients generated with SciPy [butter(..., output="sos")]
 * to match the desktop tool exactly.
 *
 * Bandpass: order 5, 15–570 Hz, fs=3910
 * Bandstop (notch): order 2, 48–52 Hz, fs=3910
 */
internal object SosCoefficients {
    val BANDPASS: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.0055361535517904435, 0.011072307103580887, 0.0055361535517904435, 1.0, -0.77732518423647179, 0.23645036528853081),
        doubleArrayOf(1.0, 2.0, 1.0, 1.0, -0.99164993153768832, 0.62108776789340059),
        doubleArrayOf(1.0, 0.0, -1.0, 1.0, -1.3371556005387, 0.35313928915236004),
        doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.9606421643356102, 0.96124936155709773),
        doubleArrayOf(1.0, -2.0, 1.0, 1.0, -1.9852156851567411, 0.9857975184180271)
    )

    val NOTCH: Array<DoubleArray> = arrayOf(
        doubleArrayOf(0.99546516471852353, -1.9845175679810774, 0.99546516471852353, 1.0, -1.9885361850452019, 0.99533737190151328),
        doubleArrayOf(1.0, -1.9935580252496501, 1.0, 1.0, -1.9895189095878474, 0.99559297404926383)
    )
}
