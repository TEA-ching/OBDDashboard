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
import java.io.InputStream
import java.io.OutputStream

/** Mock socket for testing OBD communication without real Bluetooth */
class MockBluetoothSocket : OBDAdapterSocket {
    private val commandBuffer = StringBuilder()
    private var responseGenerated = false
    private var responseBytes: ByteArray? = null
    private var responsePos = 0

    // Variables for realistic RPM simulation (engine idle around 867 RPM)
    private var baseRpm = 867.0
    private var rpmVariation = 0.0
    private var lastRpmTime = System.currentTimeMillis()

    // Variables for realistic temperature simulation
    private val startTime = System.currentTimeMillis()
    private val coldTemp = 19.0 // °C - Cold engine temperature
    private val operatingTemp = 67.0 // °C - Normal operating temperature
    private val warmupTimeMs = 5 * 60 * 1000L // 5 minutes in milliseconds
    private var thermostatOpen = false

    override val inputStream = MockInputStream()
    override val outputStream = MockOutputStream()
    override val isConnected = true

    override fun close() {
        // Nothing to close
    }

    inner class MockOutputStream : OutputStream() {
        override fun write(b: Int) {
            commandBuffer.append(b.toChar())
            responseGenerated = false // Reset response when new data written
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            commandBuffer.append(String(b, off, len))
            responseGenerated = false
        }

        override fun flush() {
            // Generate response when flushed
            if (!responseGenerated) {
                responseBytes = getResponseForCommand(commandBuffer.toString().trim())
                responsePos = 0
                responseGenerated = true
                commandBuffer.clear()
            }
        }
    }

    inner class MockInputStream : InputStream() {
        override fun read(): Int {
            if (responseBytes == null) return -1
            return if (responsePos < responseBytes!!.size) {
                responseBytes!![responsePos++].toInt() and 0xFF
            } else {
                -1
            }
        }

        override fun available(): Int {
            return if (responseBytes != null) responseBytes!!.size - responsePos else 0
        }
    }

    private fun getResponseForCommand(command: String): ByteArray {
        Log.d("MockBluetoothSocket", "Received command: $command")
        val cleanCommand = command.uppercase().trim()
        val response =
                when (cleanCommand) {
                    // ELM327 AT Commands (various formats)
                    "ATD",
                    "AT D" -> "OK\r\n" // Set defaults
                    "ATZ", "AT Z" -> "OK\r\n" // Reset - simplified response
                    "ATE0", "AT E0" -> "OK\r\n" // Extended responses off
                    "ATL0", "AT L0" -> "OK\r\n" // Line feeds off
                    "ATS0", "AT S0" -> "OK\r\n" // Printing spaces off
                    "ATH0", "AT H0" -> "OK\r\n" // Headers off
                    "AT SP0", "AT SP 0" -> "OK\r\n" // Set protocol to auto
                    "AT ST19", "AT ST 19" -> "OK\r\n" // Set timeout (0x19 = 25 decimal)

                    // OBD-II PIDs
                    "01 00" -> "4100BE3EA813\r\n" // Supported PIDs 01-20
                    "01 0C" -> generateRpmResponse() // RPM: Variable around 867 RPM (idle)
                    "01 0D" -> "410D00\r\n" // Speed: 00 km/h
                    "01 05" ->
                            generateTemperatureResponse() // Coolant Temp: Progressive 19°C -> 67°C
                    "01 31" -> "41310064\r\n" // Distance: 100 km
                    "01 2F" ->
                            "412FBF\r\n" // Fuel Level: 75% (formula: (100/255)*A, so A=191 -> ~75%)
                    else -> {
                        Log.w("MockBluetoothSocket", "Unknown command: $command")
                        "NO DATA\r\n"
                    }
                }
        Log.d("MockBluetoothSocket", "Sending response: $response")
        return response.toByteArray()
    }

    /**
     * Generates a realistic RPM response simulating engine idle around 867 RPM with natural
     * fluctuations between 850-890 RPM
     */
    private fun generateRpmResponse(): String {
        val currentTime = System.currentTimeMillis()
        lastRpmTime = currentTime

        // Simulate natural engine idle fluctuations
        // Use sine wave for smooth variation + small random component
        val timeInSeconds = currentTime / 1000.0
        val sineVariation = kotlin.math.sin(timeInSeconds * 0.5) * 15.0 // ±15 RPM sine wave
        val randomVariation = (Math.random() - 0.5) * 10.0 // ±5 RPM random

        rpmVariation = sineVariation + randomVariation
        val currentRpm = (baseRpm + rpmVariation).coerceIn(850.0, 890.0)

        // Convert RPM to OBD-II format: RPM = ((A*256)+B)/4
        // So (A*256)+B = RPM * 4
        val rpmValue = (currentRpm * 4).toInt()
        val byteA = (rpmValue shr 8) and 0xFF
        val byteB = rpmValue and 0xFF

        val response =
                "410C${byteA.toString(16).uppercase().padStart(2, '0')}${byteB.toString(16).uppercase().padStart(2, '0')}\r\n"
        Log.d("MockBluetoothSocket", "Generated RPM: ${currentRpm.toInt()} RPM -> $response")
        return response
    }

    /**
     * Generates a realistic temperature response simulating engine warmup from 19°C (cold) to 67°C
     * (operating) over 5 minutes with thermostat hysteresis
     */
    private fun generateTemperatureResponse(): String {
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - startTime
        val warmupProgress = (elapsedTime.toDouble() / warmupTimeMs).coerceIn(0.0, 1.0)

        var currentTemp: Double

        if (!thermostatOpen) {
            // Thermostat closed: slow linear warmup to ~85°C (thermostat opening temp)
            val thermostatOpenTemp = 85.0
            currentTemp = coldTemp + (thermostatOpenTemp - coldTemp) * warmupProgress

            // Open thermostat when reaching opening temperature
            if (currentTemp >= thermostatOpenTemp) {
                thermostatOpen = true
                Log.d("MockBluetoothSocket", "Thermostat opened at ${currentTemp.toInt()}°C")
            }
        } else {
            // Thermostat open: quick drop to operating temperature with small oscillations
            val targetTemp = operatingTemp
            val oscillation = kotlin.math.sin(currentTime / 10000.0) * 2.0 // ±2°C oscillation
            currentTemp = targetTemp + oscillation

            // Hysteresis: close thermostat if temp drops below 82°C
            if (currentTemp < 82.0) {
                thermostatOpen = false
                Log.d("MockBluetoothSocket", "Thermostat closed at ${currentTemp.toInt()}°C")
            }
        }

        // Convert temperature to OBD-II format: Temp = A - 40
        // So A = Temp + 40
        val tempValue = (currentTemp + 40).toInt().coerceIn(0, 255)
        val response = "4105${tempValue.toString(16).uppercase().padStart(2, '0')}\r\n"

        Log.d(
                "MockBluetoothSocket",
                "Generated Temp: ${currentTemp.toInt()}°C (thermostat: ${if (thermostatOpen) "open" else "closed"}) -> $response"
        )
        return response
    }
}
