# MadEye Camera Controller Android

This repository now contains both the new Flutter controller app and the legacy Java projects it is replacing or referencing.

## Projects

### `fortress_camera_controller`

The Flutter app lives at the repo root.

Useful commands:

```bash
flutter analyze
flutter test
flutter run
```

### `desktop_poc`

Legacy Java Swing desktop proof of concept for camera viewing and command/control.

Run it with:

```bash
cd desktop_poc
./run_camera_viewer.sh
```

### `tablet_kiosk`

Legacy native Android Java kiosk app and protocol reference.

Open in Android Studio or inspect the Java source under:

```text
tablet_kiosk/app/src/main/java/com/fortress/kiosk/
```

## Why both are here

The Flutter app is the active replacement target.

The Java projects remain in the repository as working references for:

- camera event parsing
- command protocol behavior
- device settings flows
- user, database, and firmware control screens
