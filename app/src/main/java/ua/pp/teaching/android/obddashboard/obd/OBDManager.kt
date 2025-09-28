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

package ua.pp.teaching.android.obddashboard.obd

import android.util.Log
import java.io.IOException
import kotlinx.coroutines.*
import ua.pp.teaching.android.obd.commands.OBDCommand
import ua.pp.teaching.android.obd.enums.ObdModes
import ua.pp.teaching.android.obd.statics.ObdInitSequence
import ua.pp.teaching.android.obd.statics.PIDUtils

/** Manages OBD-II communication using the AndroidOBD library */
class OBDManager {

    companion object {
        private const val TAG = "OBDManager"
        private const val UPDATE_INTERVAL_MS = 500L // Update every 500ms
    }

    private var isConnected = false
    private var updateJob: Job? = null
    private var obdSocket: OBDAdapterSocket? = null
    private var onDataReceived: ((VehicleDataRaw) -> Unit)? = null
    private var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    private var tankVolume: Float = 50f // Default tank volume in liters

    /** Raw vehicle data from OBD */
    data class VehicleDataRaw(
            val rpm: Float,
            val speed: Float,
            val coolantTemperature: Float,
            val distanceSinceReset: Float,
            val fuelLevel: Float,
            val isValid: Boolean = true
    )

    /** Sets the callback for receiving vehicle data */
    fun setOnDataReceived(callback: (VehicleDataRaw) -> Unit) {
        onDataReceived = callback
    }

    /** Sets the callback for connection status changes */
    fun setOnConnectionStatusChanged(callback: (Boolean) -> Unit) {
        onConnectionStatusChanged = callback
    }

    /** Sets the tank volume for fuel level calculations */
    fun setTankVolume(volume: Float) {
        tankVolume = volume
    }

    /**
     * Connects to the OBD-II adapter
     * @param socket The socket to use for communication (Bluetooth or Mock)
     */
    fun connect(socket: OBDAdapterSocket?): Boolean {
        return try {
            obdSocket = socket
            if (socket == null || !socket.isConnected) {
                Log.e(TAG, "Socket not available or not connected")
                isConnected = false
                onConnectionStatusChanged?.invoke(false)
                return false
            }

            Log.d(TAG, "Attempting to connect to OBD adapter...")

            // Initialize OBD adapter using AndroidOBD library's official sequence
            val initSuccess = ObdInitSequence.run(socket.inputStream, socket.outputStream)
            if (!initSuccess) {
                Log.e(TAG, "Failed to initialize OBD adapter")
                isConnected = false
                onConnectionStatusChanged?.invoke(false)
                return false
            }

            // Connection established, start data updates
            isConnected = true
            onConnectionStatusChanged?.invoke(true)
            startDataUpdates()

            Log.d(TAG, "OBD connection established")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to OBD adapter", e)
            isConnected = false
            onConnectionStatusChanged?.invoke(false)
            false
        }
    }

    /** Disconnects from the OBD-II adapter */
    fun disconnect() {
        try {
            stopDataUpdates()
            // Close OBD connection
            isConnected = false
            onConnectionStatusChanged?.invoke(false)
            Log.d(TAG, "OBD connection closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from OBD adapter", e)
        }
    }

    /** Starts periodic data updates from OBD */
    private fun startDataUpdates() {
        updateJob =
                CoroutineScope(Dispatchers.IO).launch {
                    while (isActive && isConnected) {
                        try {
                            val data = readVehicleData()
                            if (data.isValid) {
                                withContext(Dispatchers.Main) { onDataReceived?.invoke(data) }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error reading OBD data", e)
                            // Continue trying to read data
                        }

                        delay(UPDATE_INTERVAL_MS)
                    }
                }
    }

    /** Stops periodic data updates */
    private fun stopDataUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    /** Reads vehicle data from OBD-II */
    private suspend fun readVehicleData(): VehicleDataRaw {
        return withContext(Dispatchers.IO) {
            try {
                val socket = obdSocket
                if (socket == null || !socket.isConnected) {
                    Log.e(TAG, "Socket not available")
                    return@withContext VehicleDataRaw(0f, 0f, 0f, 0f, 0f, false)
                }

                val inputStream = socket.inputStream
                val outputStream = socket.outputStream

                // Read RPM (PID 0C)
                val rpmPid = PIDUtils.getPid(ObdModes.MODE_01, "0C")
                val rpm =
                        if (rpmPid != null) {
                            val rpmCommand = OBDCommand(rpmPid)
                            rpmCommand.run(inputStream, outputStream)
                            Log.d(
                                    TAG,
                                    "RPM data: ${rpmPid.data}, calculated: ${rpmPid.calculatedResult}"
                            )
                            rpmPid.calculatedResult
                        } else 0f

                // Read Vehicle Speed (PID 0D)
                val speedPid = PIDUtils.getPid(ObdModes.MODE_01, "0D")
                val speed =
                        if (speedPid != null) {
                            val speedCommand = OBDCommand(speedPid)
                            speedCommand.run(inputStream, outputStream)
                            Log.d(
                                    TAG,
                                    "Speed data: ${speedPid.data}, calculated: ${speedPid.calculatedResult}"
                            )
                            speedPid.calculatedResult
                        } else 0f

                // Read Coolant Temperature (PID 05)
                val temperaturePid = PIDUtils.getPid(ObdModes.MODE_01, "05")
                val temperature =
                        if (temperaturePid != null) {
                            val temperatureCommand = OBDCommand(temperaturePid)
                            temperatureCommand.run(inputStream, outputStream)
                            Log.d(
                                    TAG,
                                    "Temp data: ${temperaturePid.data}, calculated: ${temperaturePid.calculatedResult}"
                            )
                            temperaturePid.calculatedResult
                        } else 0f

                // Read Distance since DTCs cleared (PID 31)
                val distancePid = PIDUtils.getPid(ObdModes.MODE_01, "31")
                val distanceSinceReset =
                        if (distancePid != null) {
                            val distanceCommand = OBDCommand(distancePid)
                            distanceCommand.run(inputStream, outputStream)
                            Log.d(
                                    TAG,
                                    "Distance data: ${distancePid.data}, calculated: ${distancePid.calculatedResult}"
                            )
                            distancePid.calculatedResult
                        } else 0f

                // Read Fuel Level (PID 2F)
                val fuelLevelPid = PIDUtils.getPid(ObdModes.MODE_01, "2F")
                val fuelLevel =
                        if (fuelLevelPid != null) {
                            val fuelLevelCommand = OBDCommand(fuelLevelPid)
                            fuelLevelCommand.run(inputStream, outputStream)
                            Log.d(
                                    TAG,
                                    "Fuel Level data: ${fuelLevelPid.data}, calculated: ${fuelLevelPid.calculatedResult}"
                            )
                            fuelLevelPid.calculatedResult * tankVolume * 0.01f
                        } else 0f

                Log.d(
                        TAG,
                        "OBD Data - RPM: $rpm, Speed: $speed, Temp: $temperature, Distance: $distanceSinceReset, Fuel Level: $fuelLevel"
                )

                VehicleDataRaw(
                        rpm = rpm,
                        speed = speed,
                        coolantTemperature = temperature,
                        distanceSinceReset = distanceSinceReset,
                        fuelLevel = fuelLevel,
                        isValid = true
                )
            } catch (e: IOException) {
                Log.e(TAG, "IO Error reading OBD data", e)
                VehicleDataRaw(0f, 0f, 0f, 0f, 0f, false)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error reading OBD data", e)
                VehicleDataRaw(0f, 0f, 0f, 0f, 0f, false)
            }
        }
    }

    /** Checks if OBD adapter is connected */
    fun isConnected(): Boolean = isConnected

    /** Simulates OBD data for testing purposes Remove this method in production */
    fun startSimulation() {
        isConnected = true
        onConnectionStatusChanged?.invoke(true)

        updateJob =
                CoroutineScope(Dispatchers.IO).launch {
                    var simulatedRpm = 800f
                    var simulatedSpeed = 0f
                    var simulatedDistance = 15420f

                    while (isActive) {
                        // Simulate changing values
                        simulatedRpm = (800f + (Math.random() * 3000f)).toFloat()
                        simulatedSpeed = (Math.random() * 120f).toFloat()
                        simulatedDistance += 0.01f

                        val data =
                                VehicleDataRaw(
                                        rpm = simulatedRpm,
                                        speed = simulatedSpeed,
                                        coolantTemperature = 85f + (Math.random() * 10f).toFloat(),
                                        distanceSinceReset = simulatedDistance,
                                        fuelLevel =
                                                (75f + (Math.random() * 25f).toFloat()) *
                                                        tankVolume,
                                        isValid = true
                                )

                        withContext(Dispatchers.Main) { onDataReceived?.invoke(data) }

                        delay(UPDATE_INTERVAL_MS)
                    }
                }
    }
}
