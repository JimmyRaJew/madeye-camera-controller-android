# Fortress Tablet Kiosk

Native Android scaffold for the Ethernet-connected face-recognition kiosk replacement.

Current scope:
- Java Android app
- full-screen kiosk status screen
- configurable host, command port, and event port
- simulated event pipeline for `idle`, `detected`, `granted`, and `denied`

Planned next integration step:
- replace `SimulatedEventController` with a real Ethernet event listener based on the existing Qt `event_viewer` protocol
- add command/control screens based on the existing Qt `command_controller`

Open this folder in Android Studio:
- `tablet_kiosk/`

Notes:
- The project should use Gradle `8.10.2`. If Android Studio upgrades it to a Gradle `9.x` preview, change it back in `gradle/wrapper/gradle-wrapper.properties`.
