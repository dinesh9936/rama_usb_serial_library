package com.rama.usblibrary.util

import android.util.Log
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

                val len = driver.read(buffer, 200)

                if (len > 0) {
                    val data = buffer.copyOf(len)
                    _dataFlow.emit(data)
                }
            }
        }
    }

    fun stopReading() {
        isReading = false
        scope.cancel()
    }
}