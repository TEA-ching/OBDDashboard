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

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.text.DecimalFormat
import kotlinx.coroutines.launch
import ua.pp.teaching.android.obddashboard.bluetooth.BluetoothManager
import ua.pp.teaching.android.obddashboard.data.OdometerManager
import ua.pp.teaching.android.obddashboard.data.TankVolumeManager
import ua.pp.teaching.android.obddashboard.databinding.ActivityMainBinding
import ua.pp.teaching.android.obddashboard.obd.OBDManager

/**
 * Main activity for OBD Dashboard application Displays vehicle data in landscape mode optimized for
 * 7-inch tablets
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var obdManager: OBDManager
    private lateinit var odometerManager: OdometerManager
    private lateinit var tankVolumeManager: TankVolumeManager

    private val decimalFormat = DecimalFormat("#.#")
    private val integerFormat = DecimalFormat("#")

    // Permission request launcher
    private val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    permissions ->
                val allGranted = permissions.values.all { it }
                if (allGranted) {
                    initializeBluetooth()
                } else {
                    showPermissionDeniedDialog()
                }
            }

    // Bluetooth enable request launcher
    private val enableBluetoothLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    connectToOBD()
                } else {
                    updateConnectionStatus(false, "Bluetooth not enabled")
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if this is the first run
        if (isFirstRun()) {
            startSettingsActivity()
            return
        }

        // Keep screen on and hide system UI for dashboard
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeManagers()
        loadSavedValues()
        setupUI()
        checkPermissions()
    }

    /** Initialize manager classes */
    private fun initializeManagers() {
        bluetoothManager = BluetoothManager(this)
        obdManager = OBDManager()
        odometerManager = OdometerManager(this)
        tankVolumeManager = TankVolumeManager(this)

        // Set up callbacks
        bluetoothManager.setOnConnectionStatusChanged { isConnected, message ->
            updateConnectionStatus(isConnected, message)
            if (isConnected) {
                startOBDCommunication()
            }
        }

        obdManager.setOnDataReceived { rawData -> processVehicleData(rawData) }
    }

    /** Check if this is the first run */
    private fun isFirstRun(): Boolean {
        val prefs = getSharedPreferences("app_config", MODE_PRIVATE)
        return !prefs.getBoolean("first_run_completed", false)
    }

    /** Start settings activity */
    private fun startSettingsActivity() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }

    /** Load saved configuration values */
    private fun loadSavedValues() {
        // Load tank volume and set it in OBD manager
        val tankData = tankVolumeManager.loadTankVolume()
        val odometerData = odometerManager.loadOdometerData()

        android.util.Log.d(
                "MainActivity",
                "Loading saved values: Tank=${tankData.volume} (initialized=${tankData.isInitialized}), Odometer=${odometerData.totalOdometerBase} (initialized=${odometerData.isInitialized})"
        )

        obdManager.setTankVolume(tankData.volume)
    }

    /** Set up initial UI state */
    private fun setupUI() {
        updateConnectionStatus(false, "Disconnected")

        // Initialize displays with zero values
        updateRPM(0f)
        updateSpeed(0f)
        updateTemperature(0f)
        updateFuel(0f)

        // Load and display saved odometer if initialized
        val odometerData = odometerManager.loadOdometerData()
        if (odometerData.isInitialized) {
            updateOdometer(odometerData.totalOdometerBase)
        } else {
            updateOdometer(0f)
        }
    }

    /** Check and request necessary permissions */
    private fun checkPermissions() {
        val missingPermissions = bluetoothManager.getMissingPermissions()

        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeBluetooth()
        }
    }

    /** Initialize Bluetooth and check if it's supported/enabled */
    private fun initializeBluetooth() {
        if (!bluetoothManager.isBluetoothSupported() || !bluetoothManager.isBluetoothEnabled()) {
            // Bluetooth not available, use mock for testing
            updateConnectionStatus(true, "Mock OBD (No Bluetooth)")
            startOBDCommunication()
            return
        }

        if (!bluetoothManager.isBluetoothEnabled()) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            connectToOBD()
        }
    }

    /** Connect to OBD adapter via Bluetooth */
    private fun connectToOBD() {
        if (bluetoothManager.getSelectedDevice() == null) {
            showDeviceSelectionDialog()
        } else {
            lifecycleScope.launch { bluetoothManager.connectToELM327() }
        }
    }

    /** Start OBD communication after Bluetooth connection is established */
    private fun startOBDCommunication() {
        lifecycleScope.launch {
            val socket = bluetoothManager.getSocket()
            obdManager.connect(socket)
        }
    }

    /** Process raw vehicle data from OBD */
    private fun processVehicleData(rawData: OBDManager.VehicleDataRaw) {
        if (!rawData.isValid) return

        // Update UI with raw data
        updateRPM(rawData.rpm)
        updateSpeed(rawData.speed)
        updateTemperature(rawData.coolantTemperature)
        updateFuel(rawData.fuelLevel)

        // Handle odometer calculation
        handleOdometerData(rawData.distanceSinceReset)
    }

    /** Handle odometer data with reset detection */
    private fun handleOdometerData(distanceSinceReset: Float) {
        val currentOdometerData = odometerManager.loadOdometerData()

        if (!currentOdometerData.isInitialized) {
            // Show dialog to get initial odometer reading
            showInitialOdometerDialog(distanceSinceReset)
        } else {
            // Update odometer data
            val updatedOdometerData = odometerManager.updateOdometerData(distanceSinceReset)
            val totalOdometer = updatedOdometerData.calculateTotalOdometer(distanceSinceReset)
            updateOdometer(totalOdometer)
        }
    }

    /** Show dialog to select Bluetooth device */
    private fun showDeviceSelectionDialog() {
        val pairedDevices = bluetoothManager.getPairedDevices()
        if (pairedDevices.isNullOrEmpty()) {
            showError("No paired Bluetooth devices found")
            return
        }

        val deviceNames = pairedDevices.map { it.name ?: it.address }.toTypedArray()
        val devices = pairedDevices.toList()

        AlertDialog.Builder(this)
                .setTitle("Select OBD Device")
                .setItems(deviceNames) { _, which ->
                    val selectedDevice = devices[which]
                    bluetoothManager.setSelectedDevice(selectedDevice)
                    lifecycleScope.launch { bluetoothManager.connectToELM327() }
                }
                .setNegativeButton("Cancel", null)
                .show()
    }

    /** Show dialog to set initial odometer base value */
    private fun showOdometerBaseDialog() {
        val input = EditText(this)
        input.inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Enter current odometer reading (km)"

        AlertDialog.Builder(this)
                .setTitle("Set Odometer Base")
                .setMessage(
                        "Enter your vehicle's current odometer reading to calibrate the dashboard."
                )
                .setView(input)
                .setPositiveButton("Set") { _, _ ->
                    val baseValue = input.text.toString().toFloatOrNull()
                    if (baseValue != null && baseValue > 0) {
                        odometerManager.initializeOdometer(
                                baseValue,
                                0f
                        ) // Assume 0 distance at initialization
                        updateOdometer(baseValue)
                    } else {
                        showError("Please enter a valid odometer reading")
                        showOdometerBaseDialog() // Retry
                    }
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
    }

    /** Show dialog to set initial odometer when first data is received */
    private fun showInitialOdometerDialog(distanceSinceReset: Float) {
        val input = EditText(this)
        input.inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Enter current odometer reading (km)"

        AlertDialog.Builder(this)
                .setTitle("Initialize Odometer")
                .setMessage(
                        "OBD reports ${decimalFormat.format(distanceSinceReset)} km since reset. Enter your vehicle's current odometer reading."
                )
                .setView(input)
                .setPositiveButton("Set") { _, _ ->
                    val currentOdometer = input.text.toString().toFloatOrNull()
                    if (currentOdometer != null && currentOdometer > 0) {
                        odometerManager.initializeOdometer(currentOdometer, distanceSinceReset)
                        updateOdometer(currentOdometer)
                    } else {
                        showError("Please enter a valid odometer reading")
                        showInitialOdometerDialog(distanceSinceReset) // Retry
                    }
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
    }

    /** Show dialog to set tank volume */
    private fun showTankVolumeDialog() {
        val input = EditText(this)
        input.inputType =
                android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        input.hint = "Enter tank volume (liters)"

        AlertDialog.Builder(this)
                .setTitle("Set Tank Volume")
                .setMessage(
                        "Enter your vehicle's fuel tank capacity in liters for accurate fuel level display."
                )
                .setView(input)
                .setPositiveButton("Set") { _, _ ->
                    val volume = input.text.toString().toFloatOrNull()
                    if (volume != null && volume > 0) {
                        tankVolumeManager.initializeTankVolume(volume)
                        obdManager.setTankVolume(volume)
                    } else {
                        showError("Please enter a valid tank volume")
                        showTankVolumeDialog() // Retry
                    }
                }
                .setNegativeButton("Use Default (50L)") { _, _ ->
                    tankVolumeManager.initializeTankVolume(50f)
                    obdManager.setTankVolume(50f)
                }
                .setNegativeButton("Cancel", null)
                .setCancelable(false)
                .show()
    }

    /** Update RPM gauge */
    private fun updateRPM(rpm: Float) {
        binding.rpmGauge.setRpm(rpm)
    }

    /** Update speed display */
    private fun updateSpeed(speed: Float) {
        binding.speedValue.text = integerFormat.format(speed)
    }

    /** Update temperature display */
    private fun updateTemperature(temperature: Float) {
        binding.temperatureValue.text = integerFormat.format(temperature)
    }

    /** Update odometer display */
    private fun updateOdometer(odometer: Float) {
        binding.odometerValue.text = integerFormat.format(odometer)
    }

    /** Update fuel level display */
    private fun updateFuel(fuel: Float) {
        binding.fuelValue.text = integerFormat.format(fuel)
    }

    /** Update connection status display */
    private fun updateConnectionStatus(isConnected: Boolean, message: String?) {
        val statusText =
                when {
                    isConnected -> getString(R.string.connected)
                    message != null -> message
                    else -> getString(R.string.disconnected)
                }

        val textColorRes = if (isConnected) R.color.text_primary else R.color.gauge_danger
        val badgeBackground =
                if (isConnected) {
                    R.drawable.bg_status_badge_connected
                } else {
                    R.drawable.bg_status_badge_disconnected
                }
    }

    /** Show error message to user */
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    /** Show dialog when permissions are denied */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("Bluetooth permissions are required to connect to OBD adapter")
                .setPositiveButton("Retry") { _, _ -> checkPermissions() }
                .setNegativeButton("Exit") { _, _ -> finish() }
                .setCancelable(false)
                .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::obdManager.isInitialized) {
            obdManager.disconnect()
        }
        if (::bluetoothManager.isInitialized) {
            bluetoothManager.disconnect()
        }
    }

    override fun onPause() {
        super.onPause()
        // Keep running in background for dashboard use
    }
}
