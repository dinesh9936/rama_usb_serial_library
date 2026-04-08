package com.rama.usblibrary

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.rama.usblibrary.common.SerialConfig
import com.rama.usblibrary.driver.Cp210xDriver
import com.rama.usblibrary.driver.ProbeTable
import com.rama.usblibrary.manager.UsbPermissionManager
import com.rama.usblibrary.util.UsbSerialReader
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull


class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val probeTable = ProbeTable()

    private var driver: Cp210xDriver? = null
    private var reader: UsbSerialReader? = null

    private val ACTION_USB_PERMISSION = "com.example.usbserial.USB_PERMISSION"

    // ⭐ Useful: State tracking
    var isConnected: Boolean = false
        private set

    // Callback for the UI to know when the device was pulled out
    var onDeviceDetached: (() -> Unit)? = null

    fun findSupportedDevice(): UsbDevice? {
        return usbManager.deviceList.values.find { probeTable.isSupported(it) }
    }

    /**
     * ⭐ NEW: Receiver to detect physical unplugging
     */
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED == intent.action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                // Check if the detached device is the one we are currently using
                if (device?.vendorId == driver?.getVendorId() && device?.productId == driver?.getProductId()) {
                    disconnect()
                    onDeviceDetached?.invoke()
                }
            }
        }
    }

    /**
     * ⭐ NEW: Register the listener (Call this in your Activity's onStart or onCreate)
     */
    fun registerDetachReceiver() {
        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(detachReceiver, filter)
    }

    /**
     * ⭐ NEW: Unregister to avoid memory leaks (Call this in onStop or onDestroy)
     */
    fun unregisterDetachReceiver() {
        try {
            context.unregisterReceiver(detachReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }



    /**
     * ⭐ NEW: Get a specific UsbDevice by VID/PID
     */
    fun getDevice(vendorId: Int = 0x10C4, productId: Int = 0xEA60): UsbDevice? {
        return usbManager.deviceList.values.find {
            it.vendorId == vendorId && it.productId == productId
        }
    }

    /**
     * ⭐ NEW: Check if the app already has permission for a device
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * ⭐ NEW: Manually request permission with a callback
     */
    fun requestPermission(device: UsbDevice, onResult: (Boolean) -> Unit) {
        if (hasPermission(device)) {
            onResult(true)
            return
        }

        // Register temporary receiver for the dialog result
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    onResult(granted)
                    context.unregisterReceiver(this)
                }
            }
        }

        val intent = Intent(ACTION_USB_PERMISSION)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * ⭐ Useful: Get the data stream directly from the manager
     */
    val dataStream: SharedFlow<ByteArray>?
        get() = reader?.dataFlow

    /**
     * Connects to the device and handles all setup internally
     */
    fun connect(
        config: SerialConfig,
        onError: (String) -> Unit = {},
        onConnected: () -> Unit
    ) {
        val device = findSupportedDevice()

        if (device == null) {
            onError("No supported CP210x device found.")
            return
        }

        UsbPermissionManager(context).requestPermission(device) { granted ->
            if (granted) {
                try {

                    val connection = usbManager.openDevice(device)
                    // Use ProbeTable to create the driver
                    driver = probeTable.getDriver(device, connection!!)

                    driver?.apply {
                        open()
                        setParameters(config.baudRate, config.dataBits, config.stopBits, config.parity)
                    }

                    reader = UsbSerialReader(driver!!).apply {
                        startReading()
                    }

                    isConnected = true
                    onConnected()
                } catch (e: Exception) {
                    onError(e.message ?: "Unknown Connection Error")
                }
            } else {
                onError("USB Permission denied by user")
            }
        }
    }


    /**
     * Executes a list of commands sequentially.
     * Each command waits for the previous one to finish before starting.
     */
    suspend fun executeBatchCommands(
        commands: List<String>,
        delayBetween: Long = 100,
        timeoutPerCommand: Long = 2000
    ): Map<String, String?> {
        val results = mutableMapOf<String, String?>()

        commands.forEach { cmd ->
            val response = writeAndAwaitResponse(cmd, timeoutPerCommand)
            results[cmd] = response

            // Small delay to let the hardware "breathe"
            kotlinx.coroutines.delay(delayBetween)
        }

        return results
    }



    suspend fun writeAndAwaitResponse(command: String, timeout: Long = 2000): String? {
        if (!isConnected || dataStream == null) return null

        // 1. Clear any old data if necessary (optional)

        // 2. Send the command
        write(command)

        // 3. Wait for the next emission from the dataStream flow
        return withTimeoutOrNull(timeout) {
            dataStream?.first()?.let { String(it) }
        }
    }

    /**
     * ⭐ Useful: Send string data directly
     */
    fun write(data: String, timeout: Int = 1000) {
        write(data.toByteArray(), timeout)
    }

    /**
     * ⭐ Useful: Send raw byte data
     */
    fun write(data: ByteArray, timeout: Int = 1000) {
        if (!isConnected) throw Exception("Cannot write: Not connected")
        driver?.write(data, timeout)
    }

    /**
     * ⭐ Useful: Update baudrate or parity on the fly without reconnecting
     */
    fun updateSettings(config: SerialConfig) {
        if (isConnected) {
            driver?.setParameters(config.baudRate, config.dataBits, config.stopBits, config.parity)
        }
    }

    /**
     * ⭐ Useful: Clear all resources
     */
    fun disconnect() {
        try {
            reader?.stopReading()
            driver?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            reader = null
            driver = null
            isConnected = false
        }
    }

    /**
     * ⭐ Useful: List all available USB devices for debugging
     */
    fun getAvailableDevices(): List<String> {
        return usbManager.deviceList.values.map {
            "Device: ${it.deviceName} [VID: ${it.vendorId} PID: ${it.productId}]"
        }
    }


}

