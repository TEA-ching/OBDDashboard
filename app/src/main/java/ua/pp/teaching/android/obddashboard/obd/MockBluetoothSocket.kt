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
                    "ATZ", "AT Z" -> "ELM327 v1.5\r\n" // Reset - returns version
                    "ATE0", "AT E0" -> "OK\r\n" // Extended responses off
                    "ATL0", "AT L0" -> "OK\r\n" // Line feeds off
                    "ATS0", "AT S0" -> "OK\r\n" // Printing spaces off
                    "ATH0", "AT H0" -> "OK\r\n" // Headers off
                    "AT SP0", "AT SP 0" -> "OK\r\n" // Set protocol to auto
                    "AT ST19", "AT ST 19" -> "OK\r\n" // Set timeout (0x19 = 25 decimal)

                    // OBD-II PIDs
                    "01 00" -> "4100BE3EA813\r\n" // Supported PIDs 01-20
                    "01 0C" -> "410C1AF8\r\n" // RPM: ((26*256)+248)/4 = 6904/4 = 1726 RPM
                    "01 0D" -> "410D50\r\n" // Speed: 80 km/h
                    "01 05" -> "41056B\r\n" // Coolant Temp: 67°C (formula: A-40, so A=107 ->
                    // 107-40=67)
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
}
