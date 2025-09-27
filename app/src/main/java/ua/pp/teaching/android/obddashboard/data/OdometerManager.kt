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

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages persistent storage of odometer data
 */
class OdometerManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "odometer_prefs"
        private const val KEY_TOTAL_ODOMETER_BASE = "total_odometer_base"
        private const val KEY_LAST_DISTANCE_SINCE_RESET = "last_distance_since_reset"
        private const val KEY_IS_INITIALIZED = "is_initialized"
    }
    
    /**
     * Loads odometer data from SharedPreferences
     * @return OdometerData instance
     */
    fun loadOdometerData(): OdometerData {
        return OdometerData(
            totalOdometerBase = sharedPreferences.getFloat(KEY_TOTAL_ODOMETER_BASE, 0f),
            lastDistanceSinceReset = sharedPreferences.getFloat(KEY_LAST_DISTANCE_SINCE_RESET, 0f),
            isInitialized = sharedPreferences.getBoolean(KEY_IS_INITIALIZED, false)
        )
    }
    
    /**
     * Saves odometer data to SharedPreferences
     * @param data OdometerData to save
     */
    fun saveOdometerData(data: OdometerData) {
        sharedPreferences.edit().apply {
            putFloat(KEY_TOTAL_ODOMETER_BASE, data.totalOdometerBase)
            putFloat(KEY_LAST_DISTANCE_SINCE_RESET, data.lastDistanceSinceReset)
            putBoolean(KEY_IS_INITIALIZED, data.isInitialized)
            apply()
        }
    }
    
    /**
     * Initializes odometer with user-provided base value
     * @param userOdometer User's actual odometer reading
     * @param currentDistanceSinceReset Current OBD distance since reset
     */
    fun initializeOdometer(userOdometer: Float, currentDistanceSinceReset: Float) {
        val calculatedBase = userOdometer - currentDistanceSinceReset
        val data = OdometerData(
            totalOdometerBase = calculatedBase,
            lastDistanceSinceReset = currentDistanceSinceReset,
            isInitialized = true
        )
        saveOdometerData(data)
    }
    
    /**
     * Updates odometer data when new OBD reading is available
     * @param currentDistanceSinceReset Current distance since reset from OBD
     * @return Updated OdometerData
     */
    fun updateOdometerData(currentDistanceSinceReset: Float): OdometerData {
        val currentData = loadOdometerData()
        
        if (!currentData.isInitialized) {
            // Return unchanged data if not initialized
            return currentData
        }
        
        // Check for reset detection
        if (currentData.isResetDetected(currentDistanceSinceReset)) {
            // Reset detected - recalculate base using last known total and current reset value
            val lastTotalOdometer = currentData.calculateTotalOdometer(currentData.lastDistanceSinceReset)
            val newBase = lastTotalOdometer - currentDistanceSinceReset
            
            val updatedData = OdometerData(
                totalOdometerBase = newBase,
                lastDistanceSinceReset = currentDistanceSinceReset,
                isInitialized = true
            )
            
            saveOdometerData(updatedData)
            return updatedData
        } else {
            // Normal update - just save the new distance
            val updatedData = currentData.copy(lastDistanceSinceReset = currentDistanceSinceReset)
            saveOdometerData(updatedData)
            return updatedData
        }
    }
    
    /**
     * Clears all stored odometer data
     */
    fun clearOdometerData() {
        sharedPreferences.edit().clear().apply()
    }
}