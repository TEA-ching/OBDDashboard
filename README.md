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

## Building

```bash
./gradlew assembleDebug
```

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