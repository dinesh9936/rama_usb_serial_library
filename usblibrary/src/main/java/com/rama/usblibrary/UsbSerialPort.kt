package com.rama.usblibrary

interface UsbSerialPort {
    fun open()
    fun close()
    fun write(data: ByteArray, timeout: Int): Int
    fun setParameters(baudRate: Int, dataBits: Int, stopBits: Int, parity: Int)

}