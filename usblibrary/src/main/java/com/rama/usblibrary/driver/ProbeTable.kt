package com.rama.usblibrary.driver

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection

class ProbeTable {

    // A map of VID to a list of supported PIDs
    private val supportedDevices = mutableMapOf<Int, MutableSet<Int>>()

    init {
        // Initialize with default Silicon Labs CP210x IDs
        addProduct(UsbId.VENDOR_SILABS, UsbId.DEVICE_CP2101)
        addProduct(UsbId.VENDOR_SILABS, UsbId.DEVICE_CP2105)
        addProduct(UsbId.VENDOR_SILABS, UsbId.DEVICE_CP2108)
    }

    /**
     * Allows users of your library to add custom VID/PIDs
     * (e.g., if they have a white-labeled CP210x chip)
     */
    fun addProduct(vendorId: Int, productId: Int) {
        val pids = supportedDevices.getOrPut(vendorId) { mutableSetOf() }
        pids.add(productId)
    }

    /**
     * Checks if the connected UsbDevice is supported by this library
     */
    fun isSupported(device: UsbDevice): Boolean {
        return supportedDevices[device.vendorId]?.contains(device.productId) ?: false
    }

    /**
     * Factory method: Returns the correct driver for the device
     */
    fun getDriver(device: UsbDevice, connection: UsbDeviceConnection): Cp210xDriver? {
        return if (isSupported(device)) {
            Cp210xDriver(device, connection)
        } else {
            null
        }
    }
}