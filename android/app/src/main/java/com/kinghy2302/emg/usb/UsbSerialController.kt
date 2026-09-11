package com.kinghy2302.emg.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.kinghy2302.emg.dsp.EmgConstants
import com.kinghy2302.emg.protocol.EmgPacketParser
import com.kinghy2302.emg.protocol.EmgStreamAssembler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

class UsbSerialController(private val context: Context) {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val assembler = EmgStreamAssembler()

    @Volatile
    private var port: UsbSerialPort? = null

    fun listDevices(): List<UsbSerialDeviceInfo> {
        return findDrivers().map { driver ->
            val device = driver.device
            UsbSerialDeviceInfo(
                deviceName = device.deviceName,
                vendorId = device.vendorId,
                productId = device.productId,
                driverClass = driver.javaClass.simpleName,
                deviceId = device.deviceId,
            )
        }
    }

    suspend fun open(info: UsbSerialDeviceInfo) {
        close()
        val driver = findDrivers().firstOrNull {
            it.device.deviceId == info.deviceId &&
                it.device.vendorId == info.vendorId &&
                it.device.productId == info.productId
        } ?: throw IOException("未找到 USB 串口设备")

        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
        }
        val connection = usbManager.openDevice(device)
            ?: throw IOException("无法打开 USB 设备（权限或占用）")
        val serialPort = driver.ports.firstOrNull()
            ?: throw IOException("该 USB 设备没有串口")
        serialPort.open(connection)
        serialPort.setParameters(
            EmgConstants.BAUD_RATE,
            8,
            UsbSerialPort.STOPBITS_1,
            UsbSerialPort.PARITY_NONE,
        )
        try {
            serialPort.dtr = true
            serialPort.rts = true
        } catch (_: Exception) {
            // Some CDC adapters reject DTR/RTS; baud/data bits still apply.
        }
        try {
            serialPort.purgeHwBuffers(true, true)
        } catch (_: Exception) {
            // Not all drivers implement purge; desktop still continues.
        }
        assembler.reset()
        port = serialPort
    }

    fun close() {
        val current = port
        port = null
        if (current != null) {
            try {
                current.close()
            } catch (_: Exception) {
            }
        }
        assembler.reset()
    }

    suspend fun collectSamples(
        needed: Int,
        onChecksumFail: (String) -> Unit,
    ): DoubleArray = withContext(Dispatchers.IO) {
        val serialPort = port ?: throw IOException("串口未打开")
        val samples = ArrayList<Double>(needed)
        val buf = ByteArray(4096)
        while (isActive && samples.size < needed) {
            val n = try {
                serialPort.read(buf, READ_TIMEOUT_MS)
            } catch (e: IOException) {
                throw e
            }
            if (n <= 0) continue
            val packets = assembler.feed(buf, n)
            for (packet in packets) {
                val parsed = EmgPacketParser.parse(packet)
                if (!parsed.checksumOk) {
                    val computed = packet.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
                    val last = packet.last().toInt() and 0xFF
                    onChecksumFail(EmgPacketParser.checksumFailureMessage(computed, last))
                }
                val mv = parsed.millivolts ?: continue
                samples.add(mv)
                if (samples.size >= needed) break
            }
        }
        DoubleArray(needed) { samples[it] }
    }

    private fun findDrivers(): List<UsbSerialDriver> {
        val found = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).toMutableList()
        val claimed = found.map { it.device.deviceId }.toHashSet()
        for (device in usbManager.deviceList.values) {
            if (device.deviceId !in claimed) {
                found.add(CdcAcmSerialDriver(device))
            }
        }
        return found
    }

    private suspend fun requestPermission(device: UsbDevice) {
        val granted = suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {
                    }
                    val extraDevice = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val ok = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) &&
                        extraDevice != null && extraDevice.deviceId == device.deviceId
                    if (cont.isActive) cont.resume(ok)
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            cont.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
            val flags = PendingIntent.FLAG_MUTABLE
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
            usbManager.requestPermission(device, pending)
        }
        if (!granted) {
            delay(50)
            if (!usbManager.hasPermission(device)) {
                throw IOException("未授予 USB 权限")
            }
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.kinghy2302.emg.USB_PERMISSION"
        private const val READ_TIMEOUT_MS = 200
    }
}
