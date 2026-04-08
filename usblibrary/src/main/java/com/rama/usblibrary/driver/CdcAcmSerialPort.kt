package com.rama.usblibrary.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import com.rama.usblibrary.UsbSerialPort

class CdcAcmSerialPort(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection
) : UsbSerialPort {

    private var dataInterface: UsbInterface? = null
    private var readEndpoint: UsbEndpoint? = null
    private var writeEndpoint: UsbEndpoint? = null

    override fun open() {
        // Usually, interface 1 is data for CDC
        dataInterface = device.getInterface(1)
        connection.claimInterface(dataInterface, true)

        // Find endpoints
        for (i in 0 until dataInterface!!.endpointCount) {
            val ep = dataInterface!!.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) readEndpoint = ep
                else writeEndpoint = ep
            }
        }
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
        val lineProperty = byteArrayOf(
            (baudRate and 0xFF).toByte(),
            (baudRate shr 8 and 0xFF).toByte(),
            (baudRate shr 16 and 0xFF).toByte(),
            (baudRate shr 24 and 0xFF).toByte(),
            stopBits.toByte(),
            parity.toByte(),
            dataBits.toByte()
        )
        // SET_LINE_CODING command for CDC
        connection.controlTransfer(0x21, 0x20, 0, 0, lineProperty, lineProperty.size, 5000)
    }

    override fun write(data: ByteArray, timeout: Int) {
        connection.bulkTransfer(writeEndpoint, data, data.size, timeout)
    }

    override fun close() {
        connection.releaseInterface(dataInterface)
        connection.close()
    }

    fun getReadEndpoint() = readEndpoint
    fun getConnection() = connection
}