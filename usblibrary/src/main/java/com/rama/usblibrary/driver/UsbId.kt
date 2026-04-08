package com.rama.usblibrary.driver

object UsbId {
    // Silicon Labs Vendor ID
    const val VENDOR_SILABS = 0x10C4

    // CP210x Product IDs
    const val DEVICE_CP2101 = 0xEA60 // Standard (CP2102, CP2102N, CP2104)
    const val DEVICE_CP2105 = 0xEA70 // Dual UART
    const val DEVICE_CP2108 = 0xEA71 // Quad UART
    const val DEVICE_CP2110 = 0xEA80 // HID-to-UART (rare)

    // Check if a device is a supported CP210x
    fun isCp210x(vendorId: Int, productId: Int): Boolean {
        return vendorId == VENDOR_SILABS && (
                productId == DEVICE_CP2101 ||
                        productId == DEVICE_CP2105 ||
                        productId == DEVICE_CP2108
                )
    }
}
