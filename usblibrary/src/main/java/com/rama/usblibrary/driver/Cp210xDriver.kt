package com.rama.usblibrary.driver

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import com.rama.usblibrary.UsbSerialPort

class Cp210xDriver(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection
) : UsbSerialPort {

    companion object {
        private const val REQTYPE_HOST_TO_DEVICE = 0x41
        private const val CP210X_IFC_ENABLE = 0x00
        private const val CP210X_SET_LINE_CTL = 0x03
        private const val CP210X_SET_BAUDRATE = 0x1E
        private const val CP210X_MHS_CONTROL = 0x12 // Modem Handshake

        private const val UART_ENABLE = 0x0001
        private const val UART_DISABLE = 0x0000
        private const val CONTROL_DTR = 0x0001
        private const val CONTROL_RTS = 0x0002
    }

    private var mInterface: UsbInterface? = null
    private var mReadEndpoint: UsbEndpoint? = null
    private var mWriteEndpoint: UsbEndpoint? = null

    override fun open() {
        mInterface = device.getInterface(0)
        connection.claimInterface(mInterface, true)

        for (i in 0 until mInterface!!.endpointCount) {
            val ep = mInterface!!.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (ep.direction == UsbConstants.USB_DIR_IN) mReadEndpoint = ep
                else mWriteEndpoint = ep
            }
        }

        if (mReadEndpoint == null || mWriteEndpoint == null) {
            throw IllegalStateException("Endpoints not found")
        }

        // Enable UART
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, CP210X_IFC_ENABLE, UART_ENABLE, 0, null, 0, 1000)

        // Enable DTR + RTS
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, CP210X_MHS_CONTROL, CONTROL_DTR or CONTROL_RTS, 0, null, 0, 1000)

        // 🔥 CRITICAL FIXES
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, 0x07, 0x000F, 0, null, 0, 1000) // PURGE
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, 0x13, 0, 0, null, 0, 1000) // FLOW OFF
    }

    override fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int) {
        // Set Baud Rate
        val baudData = byteArrayOf(
            (baudRate and 0xFF).toByte(),
            (baudRate shr 8 and 0xFF).toByte(),
            (baudRate shr 16 and 0xFF).toByte(),
            (baudRate shr 24 and 0xFF).toByte()
        )
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, CP210X_SET_BAUDRATE, 0, 0, baudData, 4, 1000)

        // Set Line Control (Data bits | Stop bits | Parity)
        // Most CP210x chips use: dataBits in high byte, parity/stop in low byte
        val config = (dataBits shl 8) or (parity shl 4) or stopBits
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, CP210X_SET_LINE_CTL, config, 0, null, 0, 1000)
    }



    override fun write(data: ByteArray, timeout: Int): Int { // Add : Int
        return writeRaw(data, timeout)
    }

    private fun writeRaw(data: ByteArray, timeout: Int): Int {
        val result = connection.bulkTransfer(mWriteEndpoint, data, data.size, timeout)
        Log.d("USB_LIB", "bulkTransfer out: $result")
        return result
    }

    fun read(buffer: ByteArray, timeout: Int): Int {
        return connection.bulkTransfer(mReadEndpoint, buffer, buffer.size, timeout)
    }

    override fun close() {
        connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, CP210X_IFC_ENABLE, UART_DISABLE, 0, null, 0, 1000)
        connection.releaseInterface(mInterface)
        connection.close()
    }

    fun getVendorId() = device.vendorId
    fun getProductId() = device.productId

}
