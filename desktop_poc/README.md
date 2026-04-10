# Fortress Camera Controller

Java Swing desktop app for the camera proof of concept.

## Requirements

- macOS, Windows, or Linux
- Java JDK 17 or later

Check Java:

```bash
java -version
javac -version
```

## Files

- `src/com/fortress/poc/CameraViewerApp.java`
- `src/com/fortress/poc/CommandClient.java`
- `src/com/fortress/poc/AppLog.java`
- `run_camera_viewer.sh`

## What It Does

- listens for `MADEYE_EVENT` frames on port `7777`
- shows the camera feed at `480 x 640`
- changes the border color based on camera event state
- sends command packets to the camera on port `7778`
- provides modal dialogs for version, video, face, network, communication, users, database, and firmware actions

## Network Setup

The computer running the app must be reachable by the camera.

Default camera settings used by the app:

- Camera host: `192.168.1.111`
- Command port: `7778`
- Event listen port: `7777`

The camera must be configured to send events to the computer IP shown in the app.

## Run

From the app folder:

```bash
./run_camera_viewer.sh
```

Optional event port override:

```bash
./run_camera_viewer.sh 7777
```

The script compiles the Java source and launches the app.

## First Use

1. Start the app.
2. Confirm the camera host and ports at the top of the window.
3. Make sure the camera is configured to send events to this computer on port `7777`.
4. Use `Version`, `Video`, `Face`, `Network`, or `Communication` to read or update settings.
5. Use `Add User`, `Delete User`, or `List All Users` to manage enrolments.

## Notes

- `Version` loads automatically when the dialog opens.
- `List All Users` loads automatically when the dialog opens.
- `Add User` and `Delete User` show a confirmation dialog on success.
- If the camera returns a command failure status, the app shows that status in the error message.
