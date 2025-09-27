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

/** Manages persistent storage of tank volume data */
class TankVolumeManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "tank_volume_prefs"
        private const val KEY_VOLUME = "tank_volume"
        private const val KEY_IS_INITIALIZED = "is_initialized"
    }

    /**
     * Loads tank volume data from SharedPreferences
     * @return TankVolumeData instance
     */
    fun loadTankVolume(): TankVolumeData {
        return TankVolumeData(
                volume = sharedPreferences.getFloat(KEY_VOLUME, 50f), // Default 50L
                isInitialized = sharedPreferences.getBoolean(KEY_IS_INITIALIZED, false)
        )
    }

    /**
     * Saves tank volume data to SharedPreferences
     * @param data TankVolumeData to save
     */
    fun saveTankVolume(data: TankVolumeData) {
        sharedPreferences.edit().apply {
            putFloat(KEY_VOLUME, data.volume)
            putBoolean(KEY_IS_INITIALIZED, data.isInitialized)
            apply()
        }
    }

    /**
     * Initializes tank volume with user-provided value
     * @param volume Tank volume in liters
     */
    fun initializeTankVolume(volume: Float) {
        val data = TankVolumeData(volume = volume, isInitialized = true)
        saveTankVolume(data)
    }

    /** Clears all stored tank volume data */
    fun clearTankVolume() {
        sharedPreferences.edit().clear().apply()
    }
}
