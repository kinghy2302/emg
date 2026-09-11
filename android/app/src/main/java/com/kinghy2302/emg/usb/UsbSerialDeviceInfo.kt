package com.kinghy2302.emg.usb

data class UsbSerialDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val driverClass: String,
    val deviceId: Int,
) {
    val displayName: String
        get() = "%s  VID=%04X PID=%04X".format(deviceName, vendorId, productId)
}
