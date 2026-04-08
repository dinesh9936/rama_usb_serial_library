package com.rama.usblibrary.common


data class SerialConfig(
    val baudRate: Int = 115200,
    val dataBits: Int = 8,
    val stopBits: Int = 1, // 1: 1 stop bit, 2: 2 stop bits
    val parity: Int = 0  ,  // 0: None, 1: Odd, 2: Even
)