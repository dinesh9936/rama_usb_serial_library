package com.rama.ramausblibrary

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rama.usblibrary.UsbSerialManager
import com.rama.usblibrary.common.SerialConfig

class MainActivity : ComponentActivity() {

    private lateinit var usbManager: UsbSerialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usbManager = UsbSerialManager(this)

        usbManager.onDeviceDetached = {
            runOnUiThread {
                Toast.makeText(this, "USB Disconnected", Toast.LENGTH_SHORT).show()
            }
        }



        setContent {
            UsbScreen(usbManager)
        }
    }

    override fun onStart() {
        super.onStart()
        usbManager.registerDetachReceiver()
    }

    override fun onStop() {
        super.onStop()
        usbManager.unregisterDetachReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        usbManager.disconnect()
    }
}