# OBD Dashboard

An Android dashboard application for displaying vehicle OBD-II data in real-time, optimized for 7-inch tablets in landscape mode.

## Disclaimer

This application is for educational purposes. The idea is to report OBD-II data in a user-friendly way. It is a teaching project…

## Features

- **RPM Gauge**: Custom circular gauge with color zones (white: normal, orange: warning, red: danger)
- **Vehicle Speed**: Real-time speed display in km/h
- **Coolant Temperature**: Engine coolant temperature monitoring
- **Smart Odometer**: Intelligent total odometer calculation with DTC reset detection
- **Bluetooth Connectivity**: Connects to ELM327 OBD-II adapters via Bluetooth
- **Landscape UI**: Optimized for tablet dashboard use

## Requirements

- Android 8.0 (API level 26) or higher
- Bluetooth-enabled device
- ELM327 OBD-II Bluetooth adapter
- Vehicle with OBD-II port (1996 or newer)

## OBD-II Data Sources

The application reads the following OBD-II PIDs:

- **RPM**: Mode 01, PID 0C (Engine RPM)
- **Speed**: Mode 01, PID 0D (Vehicle Speed)
- **Coolant Temperature**: Mode 01, PID 05 (Engine Coolant Temperature)
- **Distance Since Reset**: Mode 01, PID 31 (Distance traveled since DTCs cleared)

## Smart Odometer Feature

The application implements intelligent odometer calculation:

1. **Initial Setup**: User enters current vehicle odometer reading
2. **Calculation**: Total odometer = User Base + OBD Distance Since Reset
3. **Reset Detection**: Automatically detects when DTC codes are cleared and adjusts base value
4. **Persistence**: Stores odometer data locally for continuity across app restarts

## Screenshots
<img width="1024" height="600" alt="Screenshot_1759062968" src="https://github.com/user-attachments/assets/17741e9c-f846-41c6-97d8-db64f3830701" />  
<img width="1024" height="600" alt="Screenshot_1758996650" src="https://github.com/user-attachments/assets/e4f01419-44b4-40d8-a40e-4f71983f81e1" />


## Installation

1. Build the project using Gradle
2. Install on Android device/tablet
3. Pair your ELM327 adapter with the device
4. Launch the application
5. Enter initial odometer reading when prompted

## CI/CD

This project uses GitHub Actions for continuous integration and automated releases.

### CI Workflow

The CI workflow runs on every push and pull request to `main` and `develop` branches:
- Builds the project with Gradle
- Runs unit tests
- Validates the APK generation

### Release Workflow

When creating a new release on GitHub:
1. The workflow automatically builds a signed release APK
2. Uploads the APK as a release asset

#### Setup for Releases

To enable automatic APK signing for releases, you need to configure these secrets in your GitHub repository:

1. **SIGNING_KEY_ALIAS**: Your keystore key alias
2. **SIGNING_KEY_PASSWORD**: Password for the key (optional, uses store password if not set)
3. **SIGNING_STORE_PASSWORD**: Password for the keystore

You also need to add your `keystore.jks` file to the repository (preferably in a secure location or use GitHub secrets for the keystore content).

#### Creating a Release

1. Go to the GitHub repository
2. Click on "Releases" in the right sidebar
3. Click "Create a new release"
4. Fill in the tag version (e.g., `v1.0.0`)
5. Add release notes
6. Click "Publish release"

The workflow will automatically:
- Build a signed release APK using your keystore
- Generate a cryptographic attestation for the APK
- Upload the APK as a release asset named `app-release.apk`
- Make it available for download to your users

### APK Verification

For security purposes, you can verify the authenticity and integrity of downloaded APKs using multiple methods:

#### 1. GitHub Attestation Verification

Verify that the APK was built by our official GitHub workflow:

```bash
# Install GitHub CLI if not already installed
# https://cli.github.com/

# Verify the APK attestation
gh attestation verify app-release.apk --owner TEA-ching
```

This will verify:
- The APK was built by the official GitHub Actions workflow
- The exact commit SHA used for the build
- The build environment and timestamp
- The cryptographic integrity of the file

#### 2. APK Signature Verification

Verify the APK was signed with our official certificate:

```bash
# Extract certificate information from the APK
jarsigner -verify -verbose -certs app-release.apk

# Or using apksigner (Android SDK)
apksigner verify --print-certs app-release.apk
```

**Expected Certificate Fingerprint (SHA-256):**
```
14:BA:42:E4:30:C4:45:48:3E:60:A5:8D:F6:CB:6D:A8:30:A3:0C:A0:55:78:FD:A3:32:71:D2:99:95:FC:38:09
```

⚠️ **Security Warning**: Only install APKs that match this exact certificate fingerprint. Any APK with a different fingerprint should be considered potentially malicious.

#### 3. Hash Verification

You can also verify file integrity using checksums (provided in release notes):

```bash
# Generate SHA-256 hash of the downloaded APK
sha256sum app-release.apk

# Compare with the hash provided in the GitHub release
```

## Building

### Local Development Build
```bash
./gradlew assembleDebug
```

### Local Release Build
To build a signed release APK locally:

1. Ensure your `keystore.jks` file is in the project root
2. Set the required environment variables:
   ```bash
   export SIGNING_KEY_ALIAS=your_key_alias
   export SIGNING_STORE_PASSWORD=your_store_password
   export SIGNING_KEY_PASSWORD=your_key_password  # optional, uses store password if not set
   ```
3. Run the build script:
   ```bash
   ./build-release.sh
   ```

The signed APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

## Dependencies

- AndroidOBD Library: `ua.pp.teaching.android:obd:1.8.1a`
- Android Architecture Components
- Kotlin Coroutines

## Permissions

The app requires the following permissions:
- Bluetooth access
- Bluetooth admin
- Location access (required for Bluetooth on Android 6+)
- Bluetooth connect/scan (Android 12+)

## Development

The project follows Android best practices:
- MVVM architecture pattern
- Kotlin coroutines for async operations
- Custom View for RPM gauge
- SharedPreferences for data persistence
- Material Design components

### MockBluetoothSocket for Development

When developing on Android emulators or devices without Bluetooth capability, the application automatically falls back to a `MockBluetoothSocket` that simulates realistic OBD-II adapter behavior.

#### Features of MockBluetoothSocket

**ELM327 Command Simulation:**
- Responds to all standard ELM327 AT commands (ATZ, ATE0, ATL0, ATS0, ATH0, AT SP0, AT ST19)
- Simulates proper initialization sequence required by the AndroidOBD library
- Returns appropriate responses ("OK", version strings) to ensure successful connection

**Realistic Vehicle Data Simulation:**

1. **Engine RPM (PID 01 0C)**:
   - Simulates engine idle around 867 RPM (realistic idle speed)
   - Natural fluctuations using sine wave (±15 RPM) + random variation (±5 RPM)
   - Constrained between 850-890 RPM for realistic idle behavior

2. **Coolant Temperature (PID 01 05)**:
   - Cold start simulation: begins at 19°C (ambient temperature)
   - Progressive warmup over 5 minutes to 67°C (normal operating temperature)
   - **Thermostat simulation with hysteresis**:
     - Thermostat opens at 85°C allowing coolant circulation
     - Thermostat closes at 82°C (3°C hysteresis prevents oscillation)
     - Temperature oscillates ±2°C around target when thermostat is open
   - Realistic engine warmup curve matching real vehicle behavior

3. **Vehicle Speed (PID 01 0D)**: Returns 0 km/h (stationary)

4. **Other PIDs**: 
   - Supported PIDs (01 00): Returns capability mask
   - Distance since DTC reset (01 31): Fixed 100 km
   - Fuel level (01 2F): Fixed 75%

**Usage:**
```kotlin
// Automatically used when real Bluetooth is unavailable
val mockSocket = MockBluetoothSocket()
val obdManager = OBDManager(mockSocket)
```

This mock implementation allows developers to:
- Test the application on emulators without Bluetooth hardware
- Verify UI behavior with realistic, changing data
- Debug OBD communication logic without physical hardware
- Demonstrate the application with convincing simulated vehicle data

The mock follows the same interface as real Bluetooth sockets, making it a seamless drop-in replacement for development and testing purposes.

## License

This project is developed for OBD-II vehicle monitoring purposes.

## Disclaimer

This application is intended for informational purposes. Always follow traffic laws and prioritize road safety while driving.
