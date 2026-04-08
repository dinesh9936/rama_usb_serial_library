package com.rama.usblibrary.util

import com.rama.usblibrary.driver.Cp210xDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield


class UsbSerialReader(private val driver: Cp210xDriver) {

    private val _dataFlow = MutableSharedFlow<ByteArray>()
    val dataFlow = _dataFlow.asSharedFlow()

    private var isReading = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startReading() {
        isReading = true
        scope.launch {
            val buffer = ByteArray(1024)
            while (isActive && isReading) {
                // Read from the driver
                val len = driver.read(buffer, 100) // 100ms timeout
                if (len > 0) {
                    val receivedData = buffer.copyOf(len)
                    _dataFlow.emit(receivedData)
                }
                // Small delay to prevent CPU spiking if no data is present
                yield()
            }
        }
    }

    fun stopReading() {
        isReading = false
        scope.cancel()
    }
}