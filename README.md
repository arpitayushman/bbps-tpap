# BBPS TPAP App

Third Party App Provider (TPAP) application that encrypts statements from the backend and launches the callable UI app to display them.

## Features

- Calls the backend `/v1/statement/encrypt` endpoint
- Receives encrypted payload from backend
- Launches the callable UI app (`org.npci.bbps.callableui`) with encrypted data
- Displays device information and status

## Setup

1. **Build the app:**
   ```bash
   cd tpap-app
   ./gradlew build
   ```

2. **Install on device/emulator:**
   ```bash
   ./gradlew installDebug
   ```

## Usage

1. Ensure the backend is running on `http://localhost:8111` (or `http://10.0.2.2:8111` from emulator)
2. Ensure the callable UI app (`org.npci.bbps.callableui`) is installed
3. Open the TPAP app
4. Click "View More Details" button
5. The app will:
   - Call the backend encrypt endpoint
   - Receive encrypted payload
   - Launch the callable UI app with the encrypted data
   - The callable UI app will decrypt and display the statement

## Configuration

- **Backend URL**: Currently set to `http://10.0.2.2:8111` (for emulator)
- **Consumer ID**: `C12345` (hardcoded)
- **Statement ID**: `STMT123` (hardcoded)
- **Device ID**: Automatically retrieved from Android Settings

## Project Structure

```
tpap-app/
├── app/
│   └── src/main/
│       ├── java/org/npci/bbps/tpap/
│       │   ├── model/          # Data models
│       │   ├── network/        # Backend API client
│       │   └── ui/             # UI components
│       ├── res/                # Resources
│       └── AndroidManifest.xml
└── build.gradle.kts
```

## Dependencies

- Jetpack Compose for UI
- Kotlinx Serialization for JSON
- OkHttp for network requests
