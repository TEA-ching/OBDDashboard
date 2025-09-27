/*
MIT License

Copyright (c) 2025 Ronan Le Meillat - TEAChing

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package ua.pp.teaching.android.obddashboard.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.util.*
import kotlinx.coroutines.*
import ua.pp.teaching.android.obddashboard.obd.MockBluetoothSocket
import ua.pp.teaching.android.obddashboard.obd.OBDAdapterSocket
import ua.pp.teaching.android.obddashboard.obd.RealBluetoothSocket

/** Manages Bluetooth connection to ELM327 OBD adapter */
class BluetoothManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothManager"
        private val OBD_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectionJob: Job? = null
    private var selectedDevice: BluetoothDevice? = null

    init {
        loadSelectedDevice()
    }

    private var onConnectionStatusChanged: ((Boolean, String?) -> Unit)? = null

    /**
     * Sets callback for connection status changes
     * @param callback Function called with (isConnected, errorMessage)
     */
    fun setOnConnectionStatusChanged(callback: (Boolean, String?) -> Unit) {
        onConnectionStatusChanged = callback
    }

    /** Checks if Bluetooth is supported on this device */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    /** Checks if Bluetooth is enabled */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Gets list of paired Bluetooth devices
     * @return Set of paired BluetoothDevice objects
     */
    fun getPairedDevices(): Set<BluetoothDevice>? {
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return null
        }

        return try {
            bluetoothAdapter?.bondedDevices
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting paired devices", e)
            null
        }
    }

    /**
     * Finds ELM327 device in paired devices
     * @return BluetoothDevice if found, null otherwise
     */
    fun findELM327Device(): BluetoothDevice? {
        val pairedDevices = getPairedDevices() ?: return null

        return pairedDevices.find { device ->
            try {
                val deviceName = device.name?.uppercase() ?: ""
                deviceName.contains("ELM") ||
                        deviceName.contains("OBD") ||
                        deviceName.contains("327")
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception checking device name", e)
                false
            }
        }
    }

    /**
     * Connects to specified Bluetooth device
     * @param device BluetoothDevice to connect to
     */
    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            onConnectionStatusChanged?.invoke(false, "Missing Bluetooth permissions")
            return
        }

        connectionJob =
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        withContext(Dispatchers.Main) {
                            onConnectionStatusChanged?.invoke(false, "Connecting...")
                        }

                        Log.d(TAG, "Attempting to connect to ${device.name}")

                        // Create socket
                        bluetoothSocket = device.createRfcommSocketToServiceRecord(OBD_UUID)

                        // Cancel discovery to improve connection speed
                        bluetoothAdapter?.cancelDiscovery()

                        // Connect to device
                        bluetoothSocket?.connect()

                        Log.d(TAG, "Connected to ${device.name}")

                        withContext(Dispatchers.Main) {
                            onConnectionStatusChanged?.invoke(true, null)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Connection failed", e)
                        cleanup()
                        withContext(Dispatchers.Main) {
                            onConnectionStatusChanged?.invoke(
                                    false,
                                    "Connection failed: ${e.message}"
                            )
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception during connection", e)
                        cleanup()
                        withContext(Dispatchers.Main) {
                            onConnectionStatusChanged?.invoke(false, "Permission denied")
                        }
                    }
                }
    }

    /** Connect to selected ELM327 device or find one */
    fun connectToELM327() {
        val device = selectedDevice ?: findELM327Device()
        if (device != null) {
            connectToDevice(device)
        } else {
            onConnectionStatusChanged?.invoke(false, "No ELM327 device found")
        }
    }

    /** Disconnects from current Bluetooth device */
    fun disconnect() {
        connectionJob?.cancel()
        cleanup()
        onConnectionStatusChanged?.invoke(false, "Disconnected")
    }

    /** Cleans up Bluetooth resources */
    private fun cleanup() {
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket", e)
        }
        bluetoothSocket = null
    }

    /** Get the currently selected device */
    fun getSelectedDevice(): BluetoothDevice? = selectedDevice

    /** Set the selected device and save it */
    fun setSelectedDevice(device: BluetoothDevice) {
        selectedDevice = device
        saveSelectedDevice(device)
    }

    /** Load saved device from preferences */
    private fun loadSelectedDevice() {
        val prefs = context.getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE)
        val deviceAddress = prefs.getString("selected_device_address", null)
        if (deviceAddress != null) {
            try {
                selectedDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid saved device address", e)
            }
        }
    }

    /** Save selected device to preferences */
    private fun saveSelectedDevice(device: BluetoothDevice) {
        val prefs = context.getSharedPreferences("bluetooth_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("selected_device_address", device.address).apply()
    }

    /** Gets the current socket for OBD communication (real or mock) */
    fun getSocket(): OBDAdapterSocket? {
        return if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            RealBluetoothSocket(bluetoothSocket!!)
        } else {
            // Return mock socket when Bluetooth not available
            MockBluetoothSocket()
        }
    }

    /** Checks if connected to a Bluetooth device */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    /** Checks if app has required Bluetooth permissions */
    private fun hasBluetoothPermissions(): Boolean {
        val hasBluetoothPermission =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                        PackageManager.PERMISSION_GRANTED

        val hasBluetoothAdminPermission =
                ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) ==
                        PackageManager.PERMISSION_GRANTED

        val hasLocationPermission =
                ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        // For Android 12+
        val hasBluetoothConnectPermission =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

        return hasBluetoothPermission &&
                hasBluetoothAdminPermission &&
                hasLocationPermission &&
                hasBluetoothConnectPermission
    }

    /** Gets list of required permissions that are missing */
    fun getMissingPermissions(): List<String> {
        val missingPermissions = mutableListOf<String>()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.BLUETOOTH)
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) !=
                            PackageManager.PERMISSION_GRANTED
            ) {
                missingPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        return missingPermissions
    }
}
