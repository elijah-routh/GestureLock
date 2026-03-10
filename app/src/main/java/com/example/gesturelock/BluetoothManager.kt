package com.example.gesturelock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.jvm.javaClass
import kotlin.run
import kotlin.text.toByteArray

/**
 * Minimal Bluetooth Classic (SPP) manager for ESP32 BluetoothSerial.
 *
 * Usage:
 *  val bt = remember { BluetoothManager(context) }
 *  bt.connect("AA:BB:CC:DD:EE:FF")
 *  bt.send("1\n")
 *  bt.disconnect()
 */
class BluetoothManager(private val context: Context) {

    // Standard SPP UUID (works with ESP32 BluetoothSerial)
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null

    private val scope = CoroutineScope(Dispatchers.IO)
    private var connectJob: Job? = null

    private val _status: MutableStateFlow<Status> = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status

    sealed class Status {
        data object IDLE : Status()
        data object CONNECTING : Status()
        data class CONNECTED(val deviceName: String?, val address: String) : Status()
        data class ERROR(val message: String) : Status()
    }

    fun isBluetoothSupported(): Boolean = adapter != null

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Android 12+ requires runtime permission for connect/scan.
     * This checks BLUETOOTH_CONNECT (enough for connect/write).
     */
    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Connect to a device by MAC address, e.g. "AA:BB:CC:DD:EE:FF"
     * NOTE: Device should be paired already in phone Bluetooth settings.
     */
    fun connect(address: String) {
        connectJob?.cancel()

        connectJob = scope.launch {

            val btAdapter = adapter ?: run {
                _status.value = Status.ERROR("Bluetooth not supported on this device")
                return@launch
            }

            if (!btAdapter.isEnabled) {
                _status.value = Status.ERROR("Bluetooth is OFF. Turn it on first.")
                return@launch
            }

            // HARD GUARD — everything Bluetooth goes inside this
            if (!hasConnectPermission()) {
                _status.value = Status.ERROR("Missing BLUETOOTH_CONNECT permission")
                return@launch
            }

            try {
                _status.value = Status.CONNECTING

                val device = btAdapter.getRemoteDevice(address)

                btAdapter.cancelDiscovery()

                val s = device.createRfcommSocketToServiceRecord(sppUuid)

                safeClose()

                socket = s
                s.connect()

                out = s.outputStream

                _status.value = Status.CONNECTED(device.name, device.address)

            } catch (e: Exception) {
                safeClose()
                _status.value = Status.ERROR(
                    "Connect failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * Send data to ESP32. Include '\n' if your ESP32 code expects newline.
     */
    fun send(text: String) {
        scope.launch {

            if (!hasConnectPermission()) {
                _status.value = Status.ERROR("Missing BLUETOOTH_CONNECT permission")
                return@launch
            }

            try {
                val output = out ?: run {
                    _status.value = Status.ERROR("Not connected")
                    return@launch
                }

                output.write(text.toByteArray())
                output.flush()

            } catch (e: Exception) {
                _status.value = Status.ERROR(
                    "Send failed: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        safeClose()
        _status.value = Status.IDLE
    }

    private fun safeClose() {
        try { out?.close() } catch (_: IOException) {}
        out = null
        try { socket?.close() } catch (_: IOException) {}
        socket = null
    }
}