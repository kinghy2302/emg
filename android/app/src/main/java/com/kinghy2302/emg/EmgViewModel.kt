package com.kinghy2302.emg

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kinghy2302.emg.demo.DemoSignal
import com.kinghy2302.emg.dsp.AnalysisResult
import com.kinghy2302.emg.dsp.EmgConstants
import com.kinghy2302.emg.dsp.EmgDsp
import com.kinghy2302.emg.usb.UsbSerialController
import com.kinghy2302.emg.usb.UsbSerialDeviceInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EmgUiState(
    val devices: List<UsbSerialDeviceInfo> = emptyList(),
    val selectedDeviceId: Int? = null,
    val demoMode: Boolean = true,
    val running: Boolean = false,
    val status: String = "就绪（演示模式可在无硬件时运行）",
    val checksumWarning: String? = null,
    val analysis: AnalysisResult? = null,
)

class EmgViewModel(
    private val usb: UsbSerialController,
) : ViewModel() {
    private val _state = MutableStateFlow(EmgUiState())
    val state: StateFlow<EmgUiState> = _state

    private var acquireJob: Job? = null
    private var demoIndex = 0L

    init {
        refreshDevices()
    }

    fun refreshDevices() {
        val devices = try {
            usb.listDevices()
        } catch (e: Exception) {
            Log.w(TAG, "listDevices", e)
            emptyList()
        }
        _state.update { current ->
            val selected = when {
                current.selectedDeviceId != null && devices.any { it.deviceId == current.selectedDeviceId } ->
                    current.selectedDeviceId
                devices.isNotEmpty() -> devices.first().deviceId
                else -> null
            }
            current.copy(
                devices = devices,
                selectedDeviceId = selected,
                status = if (current.running) {
                    current.status
                } else if (devices.isEmpty()) {
                    "未发现 USB 串口设备，可使用演示模式"
                } else {
                    "发现 ${devices.size} 个 USB 设备"
                },
            )
        }
    }

    fun selectDevice(deviceId: Int) {
        _state.update { it.copy(selectedDeviceId = deviceId) }
    }

    fun setDemoMode(enabled: Boolean) {
        if (_state.value.running) return
        _state.update {
            it.copy(
                demoMode = enabled,
                status = if (enabled) "演示模式：100 Hz 正弦，约 80 µVpp" else "将使用 USB 串口 115200 8N1",
            )
        }
    }

    fun onUsbAttachDetach() {
        refreshDevices()
    }

    fun start() {
        if (_state.value.running) return
        val demo = _state.value.demoMode
        if (!demo) {
            val id = _state.value.selectedDeviceId
            val info = _state.value.devices.firstOrNull { it.deviceId == id }
            if (info == null) {
                _state.update { it.copy(status = "请先刷新并选择 USB 串口设备，或开启演示模式") }
                return
            }
        }
        _state.update {
            it.copy(
                running = true,
                checksumWarning = null,
                status = if (demo) "演示采集中…" else "正在打开串口…",
            )
        }
        acquireJob = viewModelScope.launch {
            try {
                if (!demo) {
                    val info = _state.value.devices.first { it.deviceId == _state.value.selectedDeviceId }
                    usb.open(info)
                    _state.update { it.copy(status = "采集中（115200 8N1）") }
                }
                demoIndex = 0L
                while (isActive && _state.value.running) {
                    val raw = if (demo) {
                        val window = DemoSignal.window(demoIndex)
                        demoIndex += EmgConstants.SAMPLES
                        window
                    } else {
                        usb.collectSamples(EmgConstants.SAMPLES) { msg ->
                            Log.w(TAG, msg)
                            _state.update { it.copy(checksumWarning = msg) }
                        }
                    }
                    val result = withContext(Dispatchers.Default) {
                        EmgDsp.processWindow(raw)
                    }
                    _state.update {
                        it.copy(
                            analysis = result,
                            status = if (demo) {
                                "演示采集中 · 主频约 ${EmgConstants.DEMO_SINE_HZ.toInt()} Hz"
                            } else {
                                "采集中（115200 8N1）"
                            },
                        )
                    }
                    if (demo) delay(1_000)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "acquisition failed", e)
                if (_state.value.running) {
                    _state.update {
                        it.copy(
                            running = false,
                            status = "采集停止：${e.message ?: e.javaClass.simpleName}",
                        )
                    }
                }
            } finally {
                usb.close()
                _state.update { it.copy(running = false) }
            }
        }
    }

    fun stop() {
        _state.update { it.copy(running = false, status = "已停止") }
        acquireJob?.cancel()
        acquireJob = null
        usb.close()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "EmgViewModel"

        fun factory(usb: UsbSerialController): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return EmgViewModel(usb) as T
                }
            }
    }
}
