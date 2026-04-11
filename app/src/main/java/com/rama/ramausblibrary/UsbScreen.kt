package com.rama.ramausblibrary

import android.content.ContentValues.TAG
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rama.usblibrary.UsbSerialManager
import com.rama.usblibrary.common.SerialConfig

@Composable
fun UsbScreen(usbManager: UsbSerialManager) {

    var deviceStatus by remember { mutableStateOf("Not checked") }
    var connectionStatus by remember { mutableStateOf("Not connected") }
    var writeStatus by remember { mutableStateOf("No command sent") }

    var hexInput by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(listOf<String>()) }

    val buffer = mutableListOf<Byte>()

    LaunchedEffect(usbManager) {
        usbManager.dataStream.collect { data ->

            buffer.addAll(data.toList())

            if (buffer.size >= 131) {
                val packet = buffer.take(131).toByteArray()

                val hex = packet.joinToString(" ") {
                    "%02X".format(it)
                }

                logs = (logs + "RX: $hex").takeLast(50)

                buffer.clear()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        // 🔹 DEVICE SECTION
        Text("USB Serial Console", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            val device = usbManager.findSupportedDevice()
            deviceStatus = if (device != null) "Device Found ✅" else "No Device ❌"
        }) {
            Text("Scan Device")
        }

        Text("Device: $deviceStatus")

        Spacer(modifier = Modifier.height(8.dp))

        // 🔹 CONNECTION
        Button(onClick = {
            usbManager.connect(
                config = SerialConfig(9600, 8, 1, 0),
                onError = {
                    connectionStatus = "Error: $it"
                },
                onConnected = {
                    connectionStatus = "Connected ✅"
                }
            )
        }) {
            Text("Connect")
        }

        Text("Connection: $connectionStatus")

        Spacer(modifier = Modifier.height(12.dp))

        // 🔹 INPUT
        OutlinedTextField(
            value = hexInput,
            onValueChange = {
                hexInput = it.uppercase().filter { c -> c in "0123456789ABCDEF" }
            },
            label = { Text("HEX Command") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                try {
                    val bytes = hexStringToByteArray(hexInput)
                    usbManager.write(bytes)

                    logs = (logs + "TX: $hexInput").takeLast(50)
                    writeStatus = "Sent"
                } catch (e: Exception) {
                    writeStatus = "Invalid HEX"
                }
            }
        ) {
            Text("Send Command")
        }

        Text("Status: $writeStatus")

        Spacer(modifier = Modifier.height(12.dp))

        // 🔹 LOGS SECTION
        Text("Logs", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(6.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(logs.size) { index ->
                val log = logs[index]

                Text(
                    text = log,
                    color = if (log.startsWith("TX")) {
                        androidx.compose.ui.graphics.Color.Blue
                    } else {
                        androidx.compose.ui.graphics.Color(0xFF2E7D32) // green
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

fun hexStringToByteArray(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "Hex string must have even length" }

    return ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}