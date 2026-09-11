# 肌电信号采集与频谱分析

Engineering **EMG serial acquisition and spectrum visualization** tool. This is **not** a medical device and is **not** intended for diagnosis, treatment, or clinical decision-making.

Desktop original (PyQt5 + pyserial + SciPy) lives in [`reference/emg_spectrum.py`](reference/emg_spectrum.py). The Android app in [`android/`](android/) is a protocol- and DSP-compatible port.

## Desktop (reference)

Python 3 with `PyQt5`, `pyserial`, `numpy`, `scipy`, `matplotlib`:

```bash
python reference/emg_spectrum.py
```

## Android app

Kotlin + Jetpack Compose. USB serial uses [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android). Filters and FFT are implemented in Kotlin (no Python on device) to match SciPy `butter` + `sosfiltfilt` and `numpy.fft`.

### Features

1. Refresh / select a USB serial device (Android USB host / OTG)
2. Start / stop acquisition at **115200 8N1**, no flow control; input buffers are purged on open when the driver allows it
3. Parse `$` + ASCII ADC + checksum packets; buffer **1 second** at **FS = 3910 Hz**
4. Bandpass (Butterworth SOS order 5, 15–570 Hz) + notch (Butterworth SOS order 2, 48–52 Hz), both `sosfiltfilt`-equivalent
5. FFT with Hann window, DC removed, amplitude scale `2 / sum(window) * |X|`
6. Live time plot (first **100 ms**) and spectrum (**0–600 Hz**)
7. Metrics: **主频 / 峰值 / 峰峰值 / RMS值**
8. **演示模式**: 100 Hz sine, ~80 µVpp, so the UI works without hardware

### Build

Requirements: **JDK 17+**, **Android SDK** (compile SDK 35), network access for Gradle/JitPack.

```bash
# Linux/macOS example
export ANDROID_HOME="$HOME/android-sdk"   # or your SDK path
cd android
./gradlew assembleDebug testDebugUnitTest
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk`

Android Studio: open the `android/` directory as a Gradle project.

### USB OTG

- Phone/tablet must support **USB host**. Use an **OTG adapter**.
- Grant the USB permission dialog when connecting a UART (CH340, CP210x, FTDI, CDC ACM, etc.).
- USB host is optional (`android.hardware.usb.host` is not required) so **demo mode** runs on emulators and devices without OTG.

### Protocol (same as desktop)

| Item | Value |
| --- | --- |
| Baud | 115200 8N1, no software/hardware flow control |
| Line | `serial.readline()` style, `\\r` stripped, `\\n` terminated |
| Start | byte `0x24` (`$`) |
| Payload | ASCII decimal ADC integer |
| Checksum | `sum(packet[:-1]) & 0xFF` vs last byte |
| Checksum fail | logged (`校验失败！computed,actual`); sample is **still used** |
| Conversion | `voltage_mv = (adc - 2^23) * (1000 * 0.023) / 2^23` |

### DSP constants (same as desktop)

| Constant | Value |
| --- | --- |
| `FS` | 3910 Hz |
| `SAMPLES` | 3910 (process each full second) |
| `TIME_VIEW_S` | 0.1 s |
| Bandpass | Butterworth SOS order 5, 15–570 Hz, `sosfiltfilt` |
| Notch | Butterworth SOS order 2, 48–52 Hz, `sosfiltfilt` |
| FFT | mean removed, `numpy.hanning`, positive frequencies, scale `2/sum(window)` |
| Time display | `(filtered - mean) * 1000` µV, x 0–0.1 s, y auto ±15% padding |
| Spectrum display | `spectrum * 1000` µV, x 0–600 Hz, y 0–max×1.2 |

SOS coefficients are generated with SciPy `butter(..., output="sos")` so the Android filters match the desktop design, not a re-tuned approximation.

## Layout

```
reference/emg_spectrum.py   # original desktop program
android/                    # Gradle app (open this folder in Android Studio)
```
