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

package ua.pp.teaching.android.obddashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ua.pp.teaching.android.obddashboard.data.OdometerManager
import ua.pp.teaching.android.obddashboard.data.TankVolumeManager
import ua.pp.teaching.android.obddashboard.databinding.ActivitySettingsBinding

/** Settings activity for initial configuration of odometer and tank volume */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var odometerManager: OdometerManager
    private lateinit var tankVolumeManager: TankVolumeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        odometerManager = OdometerManager(this)
        tankVolumeManager = TankVolumeManager(this)

        setupUI()
    }

    private fun setupUI() {
        // Set default values
        binding.odometerInput.setText("0")
        binding.tankVolumeInput.setText("50")

        binding.useDefaultsButton.setOnClickListener { useDefaults() }

        binding.saveSettingsButton.setOnClickListener { saveSettings() }
    }

    private fun useDefaults() {
        // Initialize with default values
        odometerManager.initializeOdometer(0f, 0f)
        tankVolumeManager.initializeTankVolume(50f)

        android.util.Log.d("SettingsActivity", "Using defaults: Odometer=0, Tank=50")

        // Mark as configured
        markAsConfigured()

        // Start main activity
        startMainActivity()
    }

    private fun saveSettings() {
        val odometerValue = binding.odometerInput.text.toString().toFloatOrNull()
        val tankVolumeValue = binding.tankVolumeInput.text.toString().toIntOrNull()

        when {
            odometerValue == null || odometerValue < 0 -> {
                showError("Please enter a valid odometer reading")
                return
            }
            tankVolumeValue == null || tankVolumeValue <= 0 -> {
                showError("Please enter a valid tank volume")
                return
            }
        }

        // Save the values
        odometerManager.initializeOdometer(odometerValue, 0f)
        tankVolumeManager.initializeTankVolume(tankVolumeValue.toFloat())

        android.util.Log.d(
                "SettingsActivity",
                "Saving custom values: Odometer=$odometerValue, Tank=$tankVolumeValue"
        )

        // Mark as configured
        markAsConfigured()

        // Start main activity
        startMainActivity()
    }

    private fun markAsConfigured() {
        val prefs = getSharedPreferences("app_config", MODE_PRIVATE)
        prefs.edit().putBoolean("first_run_completed", true).apply()
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
