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
- Upload the APK as a release asset named `OBD-Dashboard-v1.0.0.apk`
- Make it available for download to your users

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

## License

This project is developed for OBD-II vehicle monitoring purposes.

## Disclaimer

This application is intended for informational purposes. Always follow traffic laws and prioritize road safety while driving.