package com.kinghy2302.emg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kinghy2302.emg.ui.EmgApp
import com.kinghy2302.emg.ui.theme.EmgTheme
import com.kinghy2302.emg.usb.UsbSerialController

class MainActivity : ComponentActivity() {
    private val usbController by lazy { UsbSerialController(applicationContext) }

    private val viewModel: EmgViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EmgViewModel(usbController) as T
            }
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED,
                -> viewModel.onUsbAttachDetach()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
        setContent {
            val state by viewModel.state.collectAsState()
            EmgTheme {
                EmgApp(
                    state = state,
                    onRefresh = viewModel::refreshDevices,
                    onSelectDevice = viewModel::selectDevice,
                    onDemoChanged = viewModel::setDemoMode,
                    onStart = viewModel::start,
                    onStop = viewModel::stop,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDevices()
    }

    override fun onDestroy() {
        unregisterReceiver(usbReceiver)
        super.onDestroy()
    }
}
