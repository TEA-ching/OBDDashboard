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

package ua.pp.teaching.android.obddashboard.data

/** Data class representing OBD vehicle data */
data class VehicleData(
        val rpm: Float = 0f, // Engine RPM (0C)
        val speed: Float = 0f, // Vehicle speed km/h (0D)
        val coolantTemperature: Float = 0f, // Coolant temperature °C (05)
        val distanceSinceReset: Float = 0f, // Distance since DTC cleared (31)
        val timestamp: Long = System.currentTimeMillis()
)

/** Data class for odometer management */
data class OdometerData(
        val totalOdometerBase: Float = 0f, // Base odometer value when app was first used
        val lastDistanceSinceReset: Float = 0f, // Last recorded distance since reset
        val isInitialized: Boolean = false // Whether initial odometer was set
) {
    /**
     * Calculates the total odometer reading
     * @param currentDistanceSinceReset Current distance since DTC reset
     * @return Total odometer reading
     */
    fun calculateTotalOdometer(currentDistanceSinceReset: Float): Float {
        return if (isInitialized) {
            totalOdometerBase + currentDistanceSinceReset
        } else {
            0f
        }
    }

    /**
     * Detects if a reset occurred (distance decreased)
     * @param currentDistanceSinceReset Current distance since reset
     * @return True if reset was detected
     */
    fun isResetDetected(currentDistanceSinceReset: Float): Boolean {
        return isInitialized && currentDistanceSinceReset < lastDistanceSinceReset
    }
}

/** Data class for tank volume management */
data class TankVolumeData(
        val volume: Float = 50f, // Tank volume in liters
        val isInitialized: Boolean = false // Whether tank volume was set
)
