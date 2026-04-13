import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';

import 'src/models/madeye_models.dart';
import 'src/services/madeye_command_client.dart';
import 'src/services/madeye_event_server.dart';
import 'src/services/rndis_probe_service.dart';
import 'src/services/usb_device_service.dart';
import 'src/services/usb_network_service.dart';
import 'src/services/transport_diagnostics_service.dart';

void main() {
  runApp(const FortressCameraControllerApp());
}

void noop() {}

class AppColors {
  static const background = Color(0xFFF5F7FB);
  static const surface = Colors.white;
  static const surfaceAlt = Color(0xFFF1F5F9);
  static const border = Color(0xFFD9E2EC);
  static const text = Color(0xFF122033);
  static const subtext = Color(0xFF5F6F82);
  static const blue = Color(0xFF2F6FED);
  static const blueSoft = Color(0xFFEAF1FF);
  static const teal = Color(0xFF2DB7A3);
  static const amber = Color(0xFFF2A94A);
  static const red = Color(0xFFE56B6F);
}

class FortressCameraControllerApp extends StatelessWidget {
  const FortressCameraControllerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Fortress Camera Controller',
      theme: ThemeData(
        brightness: Brightness.light,
        scaffoldBackgroundColor: AppColors.background,
        colorScheme: const ColorScheme.light(
          primary: AppColors.blue,
          secondary: AppColors.teal,
          surface: AppColors.surface,
          error: AppColors.red,
        ),
        drawerTheme: const DrawerThemeData(
          backgroundColor: AppColors.surface,
          surfaceTintColor: Colors.transparent,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: AppColors.surface,
          foregroundColor: AppColors.text,
          elevation: 0,
          centerTitle: false,
        ),
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            backgroundColor: AppColors.blue,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            textStyle: const TextStyle(fontWeight: FontWeight.w700),
          ),
        ),
        outlinedButtonTheme: OutlinedButtonThemeData(
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.blue,
            side: const BorderSide(color: AppColors.blue),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 16),
            textStyle: const TextStyle(fontWeight: FontWeight.w700),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppColors.surfaceAlt,
          labelStyle: const TextStyle(color: AppColors.subtext),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: AppColors.border),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: AppColors.border),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(16),
            borderSide: const BorderSide(color: AppColors.blue),
          ),
        ),
      ),
      home: const ControllerHomePage(),
    );
  }
}

class MenuSection {
  const MenuSection({
    required this.title,
    required this.subtitle,
    required this.icon,
  });

  final String title;
  final String subtitle;
  final IconData icon;
}

const menuSections = [
  MenuSection(
    title: 'Camera Viewer',
    subtitle: 'Live camera frames and essential camera power controls.',
    icon: Icons.videocam_outlined,
  ),
  MenuSection(
    title: 'Live Events',
    subtitle: 'Event stream status, latest events, and listener controls.',
    icon: Icons.notifications_active_outlined,
  ),
  MenuSection(
    title: 'Video Settings',
    subtitle: 'Resolution, rotation, camera source, and white balance.',
    icon: Icons.tune_rounded,
  ),
  MenuSection(
    title: 'Face Settings',
    subtitle: 'Recognition threshold, liveness, attempts, and face size.',
    icon: Icons.face_retouching_natural,
  ),
  MenuSection(
    title: 'Network Settings',
    subtitle: 'Address, gateway, and subnet configuration.',
    icon: Icons.router_outlined,
  ),
  MenuSection(
    title: 'Communication Settings',
    subtitle: 'Event host and command/event ports.',
    icon: Icons.settings_ethernet_rounded,
  ),
  MenuSection(
    title: 'USB Devices',
    subtitle: 'Show what the tablet can enumerate over USB host mode.',
    icon: Icons.usb_rounded,
  ),
  MenuSection(
    title: 'USB Descriptor',
    subtitle: 'Inspect device interfaces, endpoints, and permissions.',
    icon: Icons.developer_board_rounded,
  ),
  MenuSection(
    title: 'USB Network',
    subtitle: 'Inspect USB network interfaces and probe camera ports.',
    icon: Icons.router_rounded,
  ),
  MenuSection(
    title: 'Transport Diagnostics',
    subtitle: 'Inspect Android network links and probe the camera host.',
    icon: Icons.network_check_rounded,
  ),
  MenuSection(
    title: 'Add User',
    subtitle: 'Enroll a user with a face file.',
    icon: Icons.person_add_alt_1_rounded,
  ),
  MenuSection(
    title: 'Delete User',
    subtitle: 'Delete one user or clear all users from the camera.',
    icon: Icons.person_remove_alt_1_rounded,
  ),
  MenuSection(
    title: 'List Users',
    subtitle: 'View user records already enrolled on the camera.',
    icon: Icons.groups_rounded,
  ),
  MenuSection(
    title: 'Database Tools',
    subtitle: 'Download or upload the device database and checksum.',
    icon: Icons.storage_rounded,
  ),
  MenuSection(
    title: 'Firmware Update',
    subtitle: 'Choose firmware files and send update commands.',
    icon: Icons.system_update_alt_rounded,
  ),
];

class ControllerHomePage extends StatefulWidget {
  const ControllerHomePage({super.key});

  @override
  State<ControllerHomePage> createState() => _ControllerHomePageState();
}

class _ControllerHomePageState extends State<ControllerHomePage> {
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  final MadeyeEventServer _eventServer = MadeyeEventServer();
  final MadeyeCommandClient _commandClient = MadeyeCommandClient();
  final RndisProbeService _rndisProbeService = RndisProbeService();
  final UsbDeviceService _usbDeviceService = UsbDeviceService();
  final UsbNetworkService _usbNetworkService = UsbNetworkService();
  final TransportDiagnosticsService _transportDiagnosticsService =
      TransportDiagnosticsService();
  MenuSection _selectedSection = menuSections.first;
  late MadeyeControllerState _controllerState;
  String _commandStatus = 'Command channel ready';
  DateTime? _lastCommandAt;

  void _selectMenu(MenuSection section) {
    setState(() {
      _selectedSection = section;
    });
    Navigator.of(context).pop();
  }

  void _setCommandStatus(String message) {
    setState(() {
      _commandStatus = message;
      _lastCommandAt = DateTime.now();
    });
  }

  int _parseIntField(Map<String, String> values, String key) {
    return int.parse(values[key]?.trim() ?? '');
  }

  double _parseDoubleField(Map<String, String> values, String key) {
    return double.parse(values[key]?.trim() ?? '');
  }

  Future<Uint8List> _readFileBytes(String path) async {
    return File(path).readAsBytes();
  }

  Future<String> _readFileText(String path) async {
    return File(path).readAsString();
  }

  Future<void> _showSnack(String message) async {
    if (!mounted) {
      return;
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _cameraOn() async {
    try {
      await _commandClient.cameraOn(
        _controllerState.cameraHost,
        _controllerState.commandPort,
      );
      _setCommandStatus('Camera on sent successfully');
      await _showSnack('Camera on command sent');
    } catch (error) {
      _setCommandStatus('Camera on failed');
      await _showSnack('Camera on failed: $error');
    }
  }

  Future<void> _cameraOff() async {
    try {
      await _commandClient.cameraOff(
        _controllerState.cameraHost,
        _controllerState.commandPort,
      );
      _setCommandStatus('Camera off sent successfully');
      await _showSnack('Camera off command sent');
    } catch (error) {
      _setCommandStatus('Camera off failed');
      await _showSnack('Camera off failed: $error');
    }
  }

  @override
  void initState() {
    super.initState();
    _controllerState = _eventServer.currentState;
    _eventServer.stream.listen((state) {
      if (!mounted) {
        return;
      }
      setState(() {
        _controllerState = state;
      });
    });
    _eventServer.start();
  }

  @override
  void dispose() {
    _eventServer.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      key: _scaffoldKey,
      drawerEnableOpenDragGesture: true,
      appBar: AppBar(
        leading: IconButton(
          tooltip: 'Open controls',
          icon: const Icon(Icons.menu_rounded),
          onPressed: () => _scaffoldKey.currentState?.openDrawer(),
        ),
        title: const Text(
          'Fortress Camera Controller',
          style: TextStyle(fontWeight: FontWeight.w700),
        ),
        actions: [
          Padding(
            padding: EdgeInsets.only(right: 16),
            child: Center(
              child: _StatusBadge(
                label: _controllerState.listenerRunning
                    ? 'Listening ${_controllerState.eventPort}'
                    : 'Listener Offline',
                color: _controllerState.listenerRunning
                    ? AppColors.blue
                    : AppColors.red,
              ),
            ),
          ),
        ],
      ),
      drawer: ControlDrawer(
        selectedMenu: _selectedSection,
        onSelect: _selectMenu,
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                _selectedSection.title,
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.w800,
                  color: AppColors.text,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                _selectedSection.subtitle,
                style: const TextStyle(fontSize: 15, color: AppColors.subtext),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  _InfoChip(
                    label: 'Camera ${_controllerState.cameraHost}',
                    color: AppColors.teal,
                  ),
                  _InfoChip(
                    label: 'Event Port ${_controllerState.eventPort}',
                    color: _controllerState.listenerRunning
                        ? AppColors.blue
                        : AppColors.amber,
                  ),
                  _InfoChip(
                    label: 'Command Port ${_controllerState.commandPort}',
                    color: AppColors.red,
                  ),
                ],
              ),
              const SizedBox(height: 12),
              _CommandStatusStrip(
                status: _commandStatus,
                lastCommandAt: _lastCommandAt,
              ),
              const SizedBox(height: 20),
              Expanded(
                child: SingleChildScrollView(
                  child: _SectionContent(
                    section: _selectedSection,
                    controllerState: _controllerState,
                    commandClient: _commandClient,
                    rndisProbeService: _rndisProbeService,
                    usbDeviceService: _usbDeviceService,
                    usbNetworkService: _usbNetworkService,
                    transportDiagnosticsService: _transportDiagnosticsService,
                    onOpenMenu: () => _scaffoldKey.currentState?.openDrawer(),
                    onStartListener: () {
                      _eventServer.start();
                    },
                    onStopListener: () {
                      _eventServer.stop();
                    },
                    onRestartListener: () {
                      _eventServer.restart();
                    },
                    onStateChanged: (update) {
                      setState(() {
                        _controllerState = update(_controllerState);
                      });
                    },
                    onReadFileBytes: _readFileBytes,
                    onReadFileText: _readFileText,
                    onParseIntField: _parseIntField,
                    onParseDoubleField: _parseDoubleField,
                    onCameraOn: _cameraOn,
                    onCameraOff: _cameraOff,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ControlDrawer extends StatelessWidget {
  const ControlDrawer({
    super.key,
    required this.selectedMenu,
    required this.onSelect,
  });

  final MenuSection selectedMenu;
  final ValueChanged<MenuSection> onSelect;

  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 18, 20, 10),
              child: Text(
                'Camera Controls',
                style: TextStyle(
                  fontSize: 26,
                  fontWeight: FontWeight.w800,
                  color: AppColors.text,
                ),
              ),
            ),
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 0, 20, 16),
              child: Text(
                'Choose a controller section.',
                style: TextStyle(fontSize: 14, color: AppColors.subtext),
              ),
            ),
            const Divider(height: 1, color: AppColors.border),
            Expanded(
              child: ListView.builder(
                padding: const EdgeInsets.all(12),
                itemCount: menuSections.length,
                itemBuilder: (context, index) {
                  final item = menuSections[index];
                  final selected = item.title == selectedMenu.title;

                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      leading: Icon(item.icon, color: AppColors.blue),
                      tileColor: selected
                          ? AppColors.blueSoft
                          : AppColors.surface,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                        side: BorderSide(
                          color: selected ? AppColors.blue : AppColors.border,
                        ),
                      ),
                      title: Text(
                        item.title,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          color: AppColors.text,
                        ),
                      ),
                      trailing: const Icon(
                        Icons.chevron_right_rounded,
                        color: AppColors.subtext,
                      ),
                      onTap: () => onSelect(item),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SectionContent extends StatelessWidget {
  const _SectionContent({
    required this.section,
    required this.controllerState,
    required this.commandClient,
    required this.rndisProbeService,
    required this.usbDeviceService,
    required this.usbNetworkService,
    required this.transportDiagnosticsService,
    required this.onOpenMenu,
    required this.onStartListener,
    required this.onStopListener,
    required this.onRestartListener,
    required this.onStateChanged,
    required this.onReadFileBytes,
    required this.onReadFileText,
    required this.onParseIntField,
    required this.onParseDoubleField,
    required this.onCameraOn,
    required this.onCameraOff,
  });

  final MenuSection section;
  final MadeyeControllerState controllerState;
  final MadeyeCommandClient commandClient;
  final RndisProbeService rndisProbeService;
  final UsbDeviceService usbDeviceService;
  final UsbNetworkService usbNetworkService;
  final TransportDiagnosticsService transportDiagnosticsService;
  final VoidCallback onOpenMenu;
  final VoidCallback onStartListener;
  final VoidCallback onStopListener;
  final VoidCallback onRestartListener;
  final void Function(MadeyeControllerState Function(MadeyeControllerState))
  onStateChanged;
  final Future<Uint8List> Function(String path) onReadFileBytes;
  final Future<String> Function(String path) onReadFileText;
  final int Function(Map<String, String>, String) onParseIntField;
  final double Function(Map<String, String>, String) onParseDoubleField;
  final Future<void> Function() onCameraOn;
  final Future<void> Function() onCameraOff;

  @override
  Widget build(BuildContext context) {
    switch (section.title) {
      case 'Camera Viewer':
        return _CameraViewerPanel(
          state: controllerState,
          onOpenMenu: onOpenMenu,
          onCameraOn: onCameraOn,
          onCameraOff: onCameraOff,
        );
      case 'Live Events':
        return _LiveEventsPanel(
          state: controllerState,
          onOpenMenu: onOpenMenu,
          onStartListener: onStartListener,
          onStopListener: onStopListener,
          onRestartListener: onRestartListener,
        );
      case 'Video Settings':
        return _SettingsPanel(
          icon: Icons.tune_rounded,
          title: 'Video Controls',
          fields: const [
            _FieldSpec('Frame Width', '480'),
            _FieldSpec('Frame Height', '640'),
            _FieldSpec('Rotation', '90'),
            _FieldSpec('Camera', '0'),
            _FieldSpec('Balance', '0'),
          ],
          primaryLabel: 'Get',
          secondaryLabel: 'Set',
          onPrimary: (values) async {
            final result = await commandClient.videoGet(
              controllerState.cameraHost,
              controllerState.commandPort,
            );
            return {
              'Frame Width': '${result.width}',
              'Frame Height': '${result.height}',
              'Rotation': '${result.rotation}',
              'Camera': '${result.camera}',
              'Balance': '${result.balance}',
            };
          },
          onSecondary: (values) async {
            await commandClient.videoSet(
              controllerState.cameraHost,
              controllerState.commandPort,
              VideoSettings(
                width: onParseIntField(values, 'Frame Width'),
                height: onParseIntField(values, 'Frame Height'),
                rotation: onParseIntField(values, 'Rotation'),
                camera: onParseIntField(values, 'Camera'),
                balance: onParseIntField(values, 'Balance'),
              ),
            );
            return 'Video settings updated';
          },
        );
      case 'Face Settings':
        return _SettingsPanel(
          icon: Icons.face_retouching_natural,
          title: 'Face Controls',
          fields: const [
            _FieldSpec('Threshold', '0.5500'),
            _FieldSpec('Attempts', '3'),
            _FieldSpec('Liveness', '1'),
            _FieldSpec('Liveness Threshold', '0.7200'),
            _FieldSpec('Face Minimum', '1'),
            _FieldSpec('Face Size', '160'),
          ],
          primaryLabel: 'Get',
          secondaryLabel: 'Set',
          onPrimary: (values) async {
            final result = await commandClient.faceGet(
              controllerState.cameraHost,
              controllerState.commandPort,
            );
            return {
              'Threshold': result.threshold.toStringAsFixed(4),
              'Attempts': '${result.attempts}',
              'Liveness': '${result.liveness}',
              'Liveness Threshold': result.livenessThreshold.toStringAsFixed(4),
              'Face Minimum': '${result.faceMinimum}',
              'Face Size': '${result.faceSize}',
            };
          },
          onSecondary: (values) async {
            await commandClient.faceSet(
              controllerState.cameraHost,
              controllerState.commandPort,
              FaceSettings(
                threshold: onParseDoubleField(values, 'Threshold'),
                attempts: onParseIntField(values, 'Attempts'),
                liveness: onParseIntField(values, 'Liveness'),
                livenessThreshold: onParseDoubleField(
                  values,
                  'Liveness Threshold',
                ),
                faceMinimum: onParseIntField(values, 'Face Minimum'),
                faceSize: onParseIntField(values, 'Face Size'),
              ),
            );
            return 'Face settings updated';
          },
        );
      case 'Network Settings':
        return _SettingsPanel(
          icon: Icons.router_outlined,
          title: 'Network Controls',
          fields: const [
            _FieldSpec('Address', '192.168.1.111'),
            _FieldSpec('Gateway', '192.168.1.1'),
            _FieldSpec('Mask', '255.255.255.0'),
          ],
          primaryLabel: 'Get',
          secondaryLabel: 'Set',
          onPrimary: (values) async {
            final result = await commandClient.networkGet(
              controllerState.cameraHost,
              controllerState.commandPort,
            );
            return {
              'Address': result.address,
              'Gateway': result.gateway,
              'Mask': result.mask,
            };
          },
          onSecondary: (values) async {
            final result = NetworkSettings(
              address: values['Address']?.trim() ?? '',
              gateway: values['Gateway']?.trim() ?? '',
              mask: values['Mask']?.trim() ?? '',
            );
            await commandClient.networkSet(
              controllerState.cameraHost,
              controllerState.commandPort,
              result,
            );
            onStateChanged(
              (state) => state.copyWith(cameraHost: result.address),
            );
            return 'Network settings updated';
          },
        );
      case 'Communication Settings':
        return _SettingsPanel(
          icon: Icons.settings_ethernet_rounded,
          title: 'Communication Controls',
          fields: const [
            _FieldSpec('Event Host', '192.168.1.101'),
            _FieldSpec('Event Port', '7777'),
            _FieldSpec('Command Port', '7778'),
          ],
          primaryLabel: 'Get',
          secondaryLabel: 'Set',
          onPrimary: (values) async {
            final result = await commandClient.commGet(
              controllerState.cameraHost,
              controllerState.commandPort,
            );
            onStateChanged(
              (state) => state.copyWith(
                cameraHost: result.host,
                eventPort: result.eventPort,
                commandPort: result.commandPort,
              ),
            );
            return {
              'Event Host': result.host,
              'Event Port': '${result.eventPort}',
              'Command Port': '${result.commandPort}',
            };
          },
          onSecondary: (values) async {
            final result = CommSettings(
              host: values['Event Host']?.trim() ?? '',
              eventPort: onParseIntField(values, 'Event Port'),
              commandPort: onParseIntField(values, 'Command Port'),
            );
            await commandClient.commSet(
              controllerState.cameraHost,
              controllerState.commandPort,
              result,
            );
            onStateChanged(
              (state) => state.copyWith(
                cameraHost: result.host,
                eventPort: result.eventPort,
                commandPort: result.commandPort,
              ),
            );
            return 'Communication settings updated';
          },
        );
      case 'USB Devices':
        return _UsbDevicesPanel(usbDeviceService: usbDeviceService);
      case 'USB Descriptor':
        return _UsbDescriptorPanel(
          usbDeviceService: usbDeviceService,
          rndisProbeService: rndisProbeService,
        );
      case 'USB Network':
        return _UsbNetworkPanel(
          usbNetworkService: usbNetworkService,
          cameraHost: controllerState.cameraHost,
          eventPort: controllerState.eventPort,
          commandPort: controllerState.commandPort,
        );
      case 'Transport Diagnostics':
        return _TransportDiagnosticsPanel(
          transportDiagnosticsService: transportDiagnosticsService,
          usbNetworkService: usbNetworkService,
          cameraHost: controllerState.cameraHost,
          eventPort: controllerState.eventPort,
          commandPort: controllerState.commandPort,
          onStateChanged: onStateChanged,
        );
      case 'Add User':
        return _ActionFormPanel(
          icon: Icons.person_add_alt_1_rounded,
          title: 'User Enrollment',
          fields: const [
            _FieldSpec('User ID', '1001'),
            _FieldSpec('Face File', '/storage/emulated/0/face.bin'),
          ],
          actions: const ['Choose Face File', 'Add User'],
          onAction: {
            'Choose Face File': (values) async {
              final path = values['Face File']?.trim() ?? '';
              final file = File(path);
              if (!await file.exists()) {
                throw 'Face file not found: $path';
              }
              return 'Face file ready: $path';
            },
            'Add User': (values) async {
              final id = values['User ID']?.trim() ?? '';
              final path = values['Face File']?.trim() ?? '';
              final face = await onReadFileBytes(path);
              await commandClient.userAdd(
                controllerState.cameraHost,
                controllerState.commandPort,
                id,
                face,
              );
              return 'User $id enrolled';
            },
          },
        );
      case 'Delete User':
        return _ActionFormPanel(
          icon: Icons.person_remove_alt_1_rounded,
          title: 'Delete User',
          fields: const [_FieldSpec('User ID', '1001')],
          actions: const ['Delete User', 'Delete All Users'],
          onAction: {
            'Delete User': (values) async {
              final id = values['User ID']?.trim() ?? '';
              await commandClient.userDelete(
                controllerState.cameraHost,
                controllerState.commandPort,
                id,
              );
              return 'User $id deleted';
            },
            'Delete All Users': (values) async {
              await commandClient.userDeleteAll(
                controllerState.cameraHost,
                controllerState.commandPort,
              );
              return 'All users deleted';
            },
          },
        );
      case 'List Users':
        return _ListUsersPanel(
          commandClient: commandClient,
          cameraHost: controllerState.cameraHost,
          commandPort: controllerState.commandPort,
        );
      case 'Database Tools':
        return _ActionFormPanel(
          icon: Icons.storage_rounded,
          title: 'Database Tools',
          fields: const [
            _FieldSpec(
              'Database File',
              '/storage/emulated/0/camera_database.sql',
            ),
            _FieldSpec('MD5 File', '/storage/emulated/0/camera_database.md5'),
          ],
          actions: const ['Download Database', 'Upload Database'],
          onAction: {
            'Download Database': (values) async {
              final result = await commandClient.databaseGet(
                controllerState.cameraHost,
                controllerState.commandPort,
              );
              final databaseFile = File(values['Database File']?.trim() ?? '');
              final md5File = File(values['MD5 File']?.trim() ?? '');
              await databaseFile.parent.create(recursive: true);
              await md5File.parent.create(recursive: true);
              await databaseFile.writeAsBytes(result.database);
              await md5File.writeAsString(result.md5);
              return 'Database downloaded';
            },
            'Upload Database': (values) async {
              final database = await onReadFileBytes(
                values['Database File']?.trim() ?? '',
              );
              final md5 = await onReadFileText(
                values['MD5 File']?.trim() ?? '',
              );
              await commandClient.databaseSet(
                controllerState.cameraHost,
                controllerState.commandPort,
                database,
                md5.trim(),
              );
              return 'Database uploaded';
            },
          },
        );
      case 'Firmware Update':
        return _ActionFormPanel(
          icon: Icons.system_update_alt_rounded,
          title: 'Firmware Update',
          fields: const [
            _FieldSpec(
              'Firmware Zip',
              '/storage/emulated/0/FortressCameraController.zip',
            ),
            _FieldSpec(
              'MD5 File',
              '/storage/emulated/0/FortressCameraController.md5',
            ),
          ],
          actions: const ['Choose Firmware', 'Upload Firmware'],
          onAction: {
            'Choose Firmware': (values) async {
              final path = values['Firmware Zip']?.trim() ?? '';
              final file = File(path);
              if (!await file.exists()) {
                throw 'Firmware file not found: $path';
              }
              final length = await file.length();
              return 'Firmware ready: $length bytes';
            },
            'Upload Firmware': (values) async {
              final firmware = await onReadFileBytes(
                values['Firmware Zip']?.trim() ?? '',
              );
              final md5 = await onReadFileText(
                values['MD5 File']?.trim() ?? '',
              );
              await commandClient.firmwareUpdate(
                controllerState.cameraHost,
                controllerState.commandPort,
                firmware,
                md5.trim(),
              );
              return 'Firmware uploaded';
            },
          },
        );
      default:
        return const SizedBox.shrink();
    }
  }
}

class _CameraViewerPanel extends StatelessWidget {
  const _CameraViewerPanel({
    required this.state,
    required this.onOpenMenu,
    required this.onCameraOn,
    required this.onCameraOff,
  });

  final MadeyeControllerState state;
  final VoidCallback onOpenMenu;
  final Future<void> Function() onCameraOn;
  final Future<void> Function() onCameraOff;

  @override
  Widget build(BuildContext context) {
    final frame = state.lastFrameBytes;

    return Column(
      children: [
        Container(
          height: 520,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: AppColors.border, width: 2),
          ),
          child: Container(
            decoration: BoxDecoration(
              color: AppColors.surfaceAlt,
              borderRadius: BorderRadius.circular(18),
            ),
            child: Stack(
              children: [
                const Positioned(top: 16, left: 16, child: _ViewerTag()),
                if (frame != null)
                  Center(
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(16),
                      child: RotatedBox(
                        quarterTurns: 1,
                        child: Image.memory(
                          frame,
                          fit: BoxFit.contain,
                          gaplessPlayback: true,
                        ),
                      ),
                    ),
                  )
                else
                  Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(
                          Icons.videocam_outlined,
                          size: 92,
                          color: AppColors.blue,
                        ),
                        const SizedBox(height: 18),
                        const Text(
                          'Camera Viewer',
                          style: TextStyle(
                            fontSize: 30,
                            fontWeight: FontWeight.w800,
                            color: AppColors.text,
                          ),
                        ),
                        const SizedBox(height: 12),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 28),
                          child: Text(
                            state.listenerRunning
                                ? 'Listening for MADEYE JPEG frames on port ${state.eventPort}.'
                                : 'The event listener is offline. Open Live Events to start listening.',
                            textAlign: TextAlign.center,
                            style: const TextStyle(
                              fontSize: 16,
                              color: AppColors.subtext,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                Positioned(
                  right: 16,
                  bottom: 16,
                  child: _OverlayPanel(
                    label: state.headline,
                    value: state.detail,
                    accent: _eventColor(state.eventType),
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Wrap(
          spacing: 12,
          runSpacing: 12,
          alignment: WrapAlignment.start,
          children: [
            TextButton.icon(
              onPressed: onOpenMenu,
              icon: const Icon(Icons.menu_open_rounded),
              label: const Text('Open sections'),
            ),
            FilledButton.icon(
              onPressed: () async => onCameraOn(),
              icon: const Icon(Icons.power_settings_new_rounded),
              label: const Text('Camera On'),
            ),
            OutlinedButton.icon(
              onPressed: () async => onCameraOff(),
              icon: const Icon(Icons.power_off_rounded),
              label: const Text('Camera Off'),
            ),
          ],
        ),
      ],
    );
  }
}

class _LiveEventsPanel extends StatelessWidget {
  const _LiveEventsPanel({
    required this.state,
    required this.onOpenMenu,
    required this.onStartListener,
    required this.onStopListener,
    required this.onRestartListener,
  });

  final MadeyeControllerState state;
  final VoidCallback onOpenMenu;
  final VoidCallback onStartListener;
  final VoidCallback onStopListener;
  final VoidCallback onRestartListener;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        _PanelShell(
          title: 'Event Listener',
          icon: Icons.notifications_active_outlined,
          child: Column(
            children: [
              _MetricRow(
                label: 'Listener State',
                value: state.listenerRunning ? 'Listening' : 'Offline',
              ),
              _MetricRow(label: 'Last Event', value: state.headline),
              _MetricRow(label: 'Detail', value: state.detail),
              _MetricRow(label: 'Event Count', value: '${state.eventCount}'),
              _MetricRow(label: 'Last Source', value: state.lastSource),
              _MetricRow(label: 'Listener Status', value: state.listenerStatus),
            ],
          ),
        ),
        const SizedBox(height: 16),
        _ConsolePanel(
          icon: Icons.receipt_long_rounded,
          title: 'Latest Events',
          lines: state.logs
              .map((entry) => entry.formatted)
              .toList(growable: false),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: FilledButton.icon(
                onPressed: state.listenerRunning
                    ? onRestartListener
                    : onStartListener,
                icon: Icon(
                  state.listenerRunning
                      ? Icons.restart_alt_rounded
                      : Icons.play_arrow_rounded,
                ),
                label: Text(
                  state.listenerRunning ? 'Restart Listener' : 'Start Listener',
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: state.listenerRunning ? onStopListener : onOpenMenu,
                icon: Icon(
                  state.listenerRunning
                      ? Icons.stop_circle_outlined
                      : Icons.menu_open_rounded,
                ),
                label: Text(
                  state.listenerRunning ? 'Stop Listener' : 'Open Sections',
                ),
              ),
            ),
          ],
        ),
      ],
    );
  }
}

class _SettingsPanel extends StatefulWidget {
  const _SettingsPanel({
    required this.icon,
    required this.title,
    required this.fields,
    required this.primaryLabel,
    required this.secondaryLabel,
    required this.onPrimary,
    required this.onSecondary,
  });

  final IconData icon;
  final String title;
  final List<_FieldSpec> fields;
  final String primaryLabel;
  final String secondaryLabel;
  final Future<Map<String, String>> Function(Map<String, String>) onPrimary;
  final Future<String?> Function(Map<String, String>) onSecondary;

  @override
  State<_SettingsPanel> createState() => _SettingsPanelState();
}

class _SettingsPanelState extends State<_SettingsPanel> {
  late final List<TextEditingController> _controllers = widget.fields
      .map((field) => TextEditingController(text: field.value))
      .toList(growable: false);
  String? _status;

  @override
  void dispose() {
    for (final controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  Map<String, String> _values() {
    final result = <String, String>{};
    for (var index = 0; index < widget.fields.length; index++) {
      result[widget.fields[index].label] = _controllers[index].text;
    }
    return result;
  }

  Future<void> _handleGet() async {
    try {
      final values = await widget.onPrimary(_values());
      for (var index = 0; index < widget.fields.length; index++) {
        final label = widget.fields[index].label;
        final value = values[label];
        if (value != null) {
          _controllers[index].text = value;
        }
      }
      setState(() {
        _status = 'Settings loaded';
      });
    } catch (error) {
      setState(() {
        _status = 'Get failed: $error';
      });
    }
  }

  Future<void> _handleSet() async {
    try {
      final message = await widget.onSecondary(_values());
      setState(() {
        _status = message ?? 'Settings updated';
      });
    } catch (error) {
      setState(() {
        _status = 'Set failed: $error';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: widget.title,
      icon: widget.icon,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _FieldGrid(fields: widget.fields, controllers: _controllers),
          if (_status != null) ...[
            const SizedBox(height: 14),
            Text(
              _status!,
              style: const TextStyle(color: AppColors.subtext, fontSize: 13),
            ),
          ],
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: FilledButton(
                  onPressed: _handleGet,
                  child: Text(widget.primaryLabel),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton(
                  onPressed: _handleSet,
                  child: Text(widget.secondaryLabel),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _UsbDevicesPanel extends StatefulWidget {
  const _UsbDevicesPanel({required this.usbDeviceService});

  final UsbDeviceService usbDeviceService;

  @override
  State<_UsbDevicesPanel> createState() => _UsbDevicesPanelState();
}

class _UsbDevicesPanelState extends State<_UsbDevicesPanel> {
  List<UsbDeviceInfo> _devices = const [];
  String _status = 'Tap refresh to enumerate USB devices.';
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _status = 'Scanning USB devices...';
    });
    try {
      final devices = await widget.usbDeviceService.listUsbDevices();
      setState(() {
        _devices = devices;
        _status = devices.isEmpty
            ? 'No USB devices enumerated.'
            : 'Found ${devices.length} USB device${devices.length == 1 ? '' : 's'}.';
      });
    } catch (error) {
      setState(() {
        _status = 'USB scan failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: 'USB Devices',
      icon: Icons.usb_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            _status,
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          const SizedBox(height: 14),
          if (_devices.isEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surfaceAlt,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: const Text(
                'No devices have been enumerated yet.',
                style: TextStyle(color: AppColors.text, fontSize: 14),
              ),
            )
          else
            ..._devices.map(
              (device) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceAlt,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        device.productName == '-'
                            ? device.name
                            : device.productName,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                          color: AppColors.text,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        device.summary,
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.subtext,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Class ${device.deviceClass}  Subclass ${device.deviceSubclass}  Protocol ${device.deviceProtocol}',
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.subtext,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Manufacturer: ${device.manufacturerName}',
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.subtext,
                        ),
                      ),
                      Text(
                        'Version: ${device.version}',
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.subtext,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _loading ? null : _refresh,
            icon: const Icon(Icons.refresh_rounded),
            label: Text(_loading ? 'Scanning...' : 'Refresh USB Devices'),
          ),
        ],
      ),
    );
  }
}

class _UsbDescriptorPanel extends StatefulWidget {
  const _UsbDescriptorPanel({
    required this.usbDeviceService,
    required this.rndisProbeService,
  });

  final UsbDeviceService usbDeviceService;
  final RndisProbeService rndisProbeService;

  @override
  State<_UsbDescriptorPanel> createState() => _UsbDescriptorPanelState();
}

class _UsbDescriptorPanelState extends State<_UsbDescriptorPanel> {
  List<UsbDeviceInfo> _devices = const [];
  String _status = 'Tap refresh to inspect USB descriptors.';
  bool _loading = false;
  String? _selectedDeviceName;
  String _probeStatus =
      'Tap Probe RNDIS on the camera device to test the USB transport.';
  String _probeSummary = 'No RNDIS probe has been run yet.';
  Map<String, dynamic> _probeDetails = const {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _status = 'Scanning USB descriptors...';
    });
    try {
      final devices = await widget.usbDeviceService.listUsbDevices();
      setState(() {
        _devices = devices;
        if (_selectedDeviceName == null && devices.isNotEmpty) {
          _selectedDeviceName = devices.first.name;
        }
        _status = devices.isEmpty
            ? 'No USB devices enumerated.'
            : 'Found ${devices.length} USB device${devices.length == 1 ? '' : 's'}.';
      });
    } catch (error) {
      setState(() {
        _status = 'USB descriptor scan failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  UsbDeviceInfo? get _selectedDevice {
    if (_selectedDeviceName == null) {
      return _devices.isEmpty ? null : _devices.first;
    }
    for (final device in _devices) {
      if (device.name == _selectedDeviceName) {
        return device;
      }
    }
    return _devices.isEmpty ? null : _devices.first;
  }

  Future<void> _requestPermission(UsbDeviceInfo device) async {
    setState(() {
      _loading = true;
      _status = 'Requesting permission for ${device.name}...';
    });
    try {
      final message = await widget.usbDeviceService.requestPermission(
        device.name,
      );
      await _refresh();
      setState(() {
        _status = message;
        _selectedDeviceName = device.name;
      });
    } catch (error) {
      setState(() {
        _status = 'Permission request failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  Future<void> _probeRndis(UsbDeviceInfo device) async {
    setState(() {
      _loading = true;
      _probeStatus = 'Probing RNDIS transport for ${device.name}...';
      _probeSummary = 'Running USB transport check...';
      _probeDetails = const {};
    });
    try {
      final details = await widget.rndisProbeService.probe(device.name);
      setState(() {
        _probeDetails = details;
        _probeStatus = details.isEmpty
            ? 'RNDIS probe returned no details.'
            : 'RNDIS probe completed for ${device.name}.';
        _probeSummary = details.isEmpty
            ? 'Probe completed, but the camera did not return any transport details.'
            : details.entries
                  .map((entry) => '${entry.key}: ${entry.value}')
                  .join('\n');
      });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              details.isEmpty
                  ? 'RNDIS probe completed with no details.'
                  : 'RNDIS probe completed for ${device.productName == '-' ? device.name : device.productName}.',
            ),
          ),
        );
      }
    } catch (error) {
      setState(() {
        _probeStatus = 'RNDIS probe failed: $error';
        _probeSummary = 'Probe failed. See the status text above for details.';
      });
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('RNDIS probe failed: $error')));
      }
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selectedDevice;
    return _PanelShell(
      title: 'USB Descriptor',
      icon: Icons.developer_board_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            _status,
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          const SizedBox(height: 8),
          Text(
            _probeStatus,
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          if (_probeSummary.isNotEmpty) ...[
            const SizedBox(height: 10),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.blueSoft,
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.blue),
              ),
              child: SelectableText(
                _probeSummary,
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 12,
                  color: AppColors.text,
                ),
              ),
            ),
          ],
          const SizedBox(height: 14),
          if (_devices.isEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surfaceAlt,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: const Text(
                'No USB descriptors available yet.',
                style: TextStyle(color: AppColors.text, fontSize: 14),
              ),
            )
          else
            ..._devices.map(
              (device) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: _selectedDeviceName == device.name
                        ? AppColors.blueSoft
                        : AppColors.surfaceAlt,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(
                      color: _selectedDeviceName == device.name
                          ? AppColors.blue
                          : AppColors.border,
                    ),
                  ),
                  child: InkWell(
                    onTap: () {
                      setState(() {
                        _selectedDeviceName = device.name;
                      });
                    },
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                device.productName == '-'
                                    ? device.name
                                    : device.productName,
                                style: const TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.w800,
                                  color: AppColors.text,
                                ),
                              ),
                            ),
                            _InfoChip(
                              label: device.hasPermission
                                  ? 'Permission Granted'
                                  : 'No Permission',
                              color: device.hasPermission
                                  ? AppColors.teal
                                  : AppColors.red,
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          device.summary,
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.subtext,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Class ${device.deviceClass}  Subclass ${device.deviceSubclass}  Protocol ${device.deviceProtocol}',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.subtext,
                          ),
                        ),
                        Text(
                          'Manufacturer: ${device.manufacturerName}',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.subtext,
                          ),
                        ),
                        Text(
                          'Version: ${device.version}',
                          style: const TextStyle(
                            fontSize: 13,
                            color: AppColors.subtext,
                          ),
                        ),
                        const SizedBox(height: 10),
                        if (device.interfaces.isEmpty)
                          const Text(
                            'No interfaces exposed.',
                            style: TextStyle(
                              fontSize: 13,
                              color: AppColors.subtext,
                            ),
                          )
                        else
                          ...device.interfaces.map(
                            (iface) => Padding(
                              padding: const EdgeInsets.only(top: 10),
                              child: Container(
                                width: double.infinity,
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: AppColors.surface,
                                  borderRadius: BorderRadius.circular(14),
                                  border: Border.all(color: AppColors.border),
                                ),
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      'Interface ${iface.id}  Class ${iface.interfaceClass}  Subclass ${iface.interfaceSubclass}  Protocol ${iface.interfaceProtocol}',
                                      style: const TextStyle(
                                        fontSize: 13,
                                        fontWeight: FontWeight.w700,
                                        color: AppColors.text,
                                      ),
                                    ),
                                    const SizedBox(height: 6),
                                    if (iface.endpoints.isEmpty)
                                      const Text(
                                        'No endpoints exposed.',
                                        style: TextStyle(
                                          fontSize: 12,
                                          color: AppColors.subtext,
                                        ),
                                      )
                                    else
                                      ...iface.endpoints.map(
                                        (endpoint) => Text(
                                          'Endpoint 0x${endpoint.address.toRadixString(16).padLeft(2, '0')} attrs ${endpoint.attributes} max ${endpoint.maxPacketSize} interval ${endpoint.interval}',
                                          style: const TextStyle(
                                            fontSize: 12,
                                            color: AppColors.subtext,
                                          ),
                                        ),
                                      ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        const SizedBox(height: 12),
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Wrap(
                            spacing: 12,
                            runSpacing: 12,
                            children: [
                              FilledButton.icon(
                                onPressed: _loading
                                    ? null
                                    : () => _requestPermission(device),
                                icon: const Icon(Icons.key_rounded),
                                label: Text(
                                  device.hasPermission
                                      ? 'Permission Already Granted'
                                      : 'Request Permission',
                                ),
                              ),
                              OutlinedButton.icon(
                                onPressed: _loading
                                    ? null
                                    : () => _probeRndis(device),
                                icon: const Icon(Icons.usb_rounded),
                                label: const Text('Probe RNDIS'),
                              ),
                            ],
                          ),
                        ),
                        if (_probeDetails.isNotEmpty) ...[
                          const SizedBox(height: 12),
                          Container(
                            width: double.infinity,
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: AppColors.surface,
                              borderRadius: BorderRadius.circular(14),
                              border: Border.all(color: AppColors.border),
                            ),
                            child: SelectableText(
                              _probeDetails.entries
                                  .map(
                                    (entry) => '${entry.key}: ${entry.value}',
                                  )
                                  .join('\n'),
                              style: const TextStyle(
                                fontFamily: 'monospace',
                                fontSize: 12,
                                color: AppColors.text,
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
          if (selected != null) ...[
            const SizedBox(height: 10),
            Text(
              'Selected device: ${selected.name}',
              style: const TextStyle(color: AppColors.subtext, fontSize: 13),
            ),
          ],
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: _loading ? null : _refresh,
            icon: const Icon(Icons.refresh_rounded),
            label: Text(_loading ? 'Scanning...' : 'Refresh USB Descriptors'),
          ),
        ],
      ),
    );
  }
}

class _UsbNetworkPanel extends StatefulWidget {
  const _UsbNetworkPanel({
    required this.usbNetworkService,
    required this.cameraHost,
    required this.eventPort,
    required this.commandPort,
  });

  final UsbNetworkService usbNetworkService;
  final String cameraHost;
  final int eventPort;
  final int commandPort;

  @override
  State<_UsbNetworkPanel> createState() => _UsbNetworkPanelState();
}

class _UsbNetworkPanelState extends State<_UsbNetworkPanel> {
  List<UsbNetworkInterfaceInfo> _interfaces = const [];
  String _status = 'Tap refresh to inspect USB network interfaces.';
  bool _loading = false;
  Map<String, bool> _probeResults = const {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _status = 'Scanning network interfaces...';
    });
    try {
      final interfaces = await widget.usbNetworkService.listInterfaces();
      setState(() {
        _interfaces = interfaces;
        _status = interfaces.isEmpty
            ? 'No active IPv4 interfaces found.'
            : 'Found ${interfaces.length} active IPv4 interface${interfaces.length == 1 ? '' : 's'}.';
      });
    } catch (error) {
      setState(() {
        _status = 'Interface scan failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  Future<void> _probeCamera() async {
    setState(() {
      _loading = true;
      _status = 'Probing camera host ${widget.cameraHost}...';
    });
    try {
      final eventOpen = await widget.usbNetworkService.probePort(
        widget.cameraHost,
        widget.eventPort,
      );
      final commandOpen = await widget.usbNetworkService.probePort(
        widget.cameraHost,
        widget.commandPort,
      );
      setState(() {
        _probeResults = {'event': eventOpen, 'command': commandOpen};
        _status = eventOpen || commandOpen
            ? 'Camera host responded on ${eventOpen ? 'event' : ''}${eventOpen && commandOpen ? ' and ' : ''}${commandOpen ? 'command' : ''} port.'
            : 'Camera host did not respond on ${widget.cameraHost}.';
      });
    } catch (error) {
      setState(() {
        _status = 'Probe failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: 'USB Network',
      icon: Icons.router_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            _status,
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          const SizedBox(height: 14),
          if (_interfaces.isEmpty)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surfaceAlt,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: const Text(
                'No IPv4 interfaces are active right now.',
                style: TextStyle(color: AppColors.text, fontSize: 14),
              ),
            )
          else
            ..._interfaces.map(
              (iface) => Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceAlt,
                    borderRadius: BorderRadius.circular(16),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        iface.name,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w800,
                          color: AppColors.text,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        iface.addresses.join(', '),
                        style: const TextStyle(
                          fontSize: 13,
                          color: AppColors.subtext,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          const SizedBox(height: 8),
          Text(
            'Event port: ${widget.eventPort}  Command port: ${widget.commandPort}',
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          if (_probeResults.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              'Event port reachable: ${_probeResults['event'] == true ? 'yes' : 'no'}',
              style: const TextStyle(color: AppColors.subtext, fontSize: 13),
            ),
            Text(
              'Command port reachable: ${_probeResults['command'] == true ? 'yes' : 'no'}',
              style: const TextStyle(color: AppColors.subtext, fontSize: 13),
            ),
          ],
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              FilledButton.icon(
                onPressed: _loading ? null : _refresh,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(_loading ? 'Scanning...' : 'Refresh Interfaces'),
              ),
              OutlinedButton.icon(
                onPressed: _loading ? null : _probeCamera,
                icon: const Icon(Icons.wifi_find_rounded),
                label: const Text('Probe Camera Host'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TransportDiagnosticsPanel extends StatefulWidget {
  const _TransportDiagnosticsPanel({
    required this.transportDiagnosticsService,
    required this.usbNetworkService,
    required this.cameraHost,
    required this.eventPort,
    required this.commandPort,
    required this.onStateChanged,
  });

  final TransportDiagnosticsService transportDiagnosticsService;
  final UsbNetworkService usbNetworkService;
  final String cameraHost;
  final int eventPort;
  final int commandPort;
  final void Function(MadeyeControllerState Function(MadeyeControllerState))
  onStateChanged;

  @override
  State<_TransportDiagnosticsPanel> createState() =>
      _TransportDiagnosticsPanelState();
}

class _TransportDiagnosticsPanelState
    extends State<_TransportDiagnosticsPanel> {
  List<TransportNetworkInfo> _networks = const [];
  String _status = 'Tap refresh to inspect transport links.';
  bool _loading = false;
  String? _selectedCandidateHost;
  final List<String> _probes = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  List<String> _candidateHosts() {
    final candidates = <String>{
      widget.cameraHost,
      '192.168.1.111',
      '192.168.18.111',
      '192.168.7.2',
      '192.168.42.1',
    };
    for (final network in _networks) {
      for (final address in network.addresses) {
        final parts = address.split('.');
        if (parts.length == 4) {
          candidates.add('${parts[0]}.${parts[1]}.${parts[2]}.1');
          candidates.add('${parts[0]}.${parts[1]}.${parts[2]}.2');
          candidates.add('${parts[0]}.${parts[1]}.${parts[2]}.111');
        }
      }
    }
    return candidates.toList(growable: false);
  }

  Future<void> _refresh() async {
    setState(() {
      _loading = true;
      _status = 'Scanning transport links...';
    });
    try {
      final networks = await widget.transportDiagnosticsService.listNetworks();
      setState(() {
        _networks = networks;
        _status = networks.isEmpty
            ? 'No active transport links found.'
            : 'Found ${networks.length} active transport link${networks.length == 1 ? '' : 's'}.';
      });
    } catch (error) {
      setState(() {
        _status = 'Transport scan failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  Future<void> _probeCandidates() async {
    setState(() {
      _loading = true;
      _status = 'Probing candidate camera hosts...';
      _probes.clear();
    });
    try {
      final candidates = _candidateHosts();
      for (final host in candidates) {
        final eventOpen = await widget.usbNetworkService.probePort(
          host,
          widget.eventPort,
        );
        final commandOpen = await widget.usbNetworkService.probePort(
          host,
          widget.commandPort,
        );
        _probes.add(
          '$host -> event:${eventOpen ? 'open' : 'closed'} command:${commandOpen ? 'open' : 'closed'}',
        );
        if (eventOpen || commandOpen) {
          _selectedCandidateHost = host;
          widget.onStateChanged((state) => state.copyWith(cameraHost: host));
          break;
        }
      }
      setState(() {
        _status = _selectedCandidateHost == null
            ? 'No candidate camera host responded.'
            : 'Camera host appears reachable at $_selectedCandidateHost.';
      });
    } catch (error) {
      setState(() {
        _status = 'Probe failed: $error';
      });
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: 'Transport Diagnostics',
      icon: Icons.network_check_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            _status,
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          const SizedBox(height: 14),
          ..._networks.map(
            (network) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surfaceAlt,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: AppColors.border),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          network.interfaceName,
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: AppColors.text,
                          ),
                        ),
                        const SizedBox(width: 8),
                        _InfoChip(
                          label: network.transportLabel,
                          color: AppColors.blue,
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Addresses: ${network.addresses.isEmpty ? '-' : network.addresses.join(', ')}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.subtext,
                      ),
                    ),
                    Text(
                      'Routes: ${network.routes.isEmpty ? '-' : network.routes.join(' | ')}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.subtext,
                      ),
                    ),
                    Text(
                      'DNS: ${network.dnsServers.isEmpty ? '-' : network.dnsServers.join(', ')}',
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.subtext,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          if (_probes.isNotEmpty) ...[
            const SizedBox(height: 8),
            Container(
              width: double.infinity,
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.surfaceAlt,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: AppColors.border),
              ),
              child: Text(
                _probes.join('\n'),
                style: const TextStyle(
                  fontFamily: 'monospace',
                  fontSize: 13,
                  color: AppColors.text,
                ),
              ),
            ),
          ],
          const SizedBox(height: 12),
          Text(
            _selectedCandidateHost == null
                ? 'Camera host: ${widget.cameraHost}'
                : 'Camera host: $_selectedCandidateHost',
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: [
              FilledButton.icon(
                onPressed: _loading ? null : _refresh,
                icon: const Icon(Icons.refresh_rounded),
                label: Text(_loading ? 'Scanning...' : 'Refresh Transport'),
              ),
              OutlinedButton.icon(
                onPressed: _loading ? null : _probeCandidates,
                icon: const Icon(Icons.wifi_find_rounded),
                label: const Text('Probe Candidates'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ActionFormPanel extends StatefulWidget {
  const _ActionFormPanel({
    required this.icon,
    required this.title,
    required this.fields,
    required this.actions,
    required this.onAction,
  });

  final IconData icon;
  final String title;
  final List<_FieldSpec> fields;
  final List<String> actions;
  final Map<String, Future<String?> Function(Map<String, String>)> onAction;

  @override
  State<_ActionFormPanel> createState() => _ActionFormPanelState();
}

class _ActionFormPanelState extends State<_ActionFormPanel> {
  late final List<TextEditingController> _controllers = widget.fields
      .map((field) => TextEditingController(text: field.value))
      .toList(growable: false);
  String? _status;

  @override
  void dispose() {
    for (final controller in _controllers) {
      controller.dispose();
    }
    super.dispose();
  }

  Map<String, String> _values() {
    final result = <String, String>{};
    for (var index = 0; index < widget.fields.length; index++) {
      result[widget.fields[index].label] = _controllers[index].text;
    }
    return result;
  }

  Future<void> _runAction(String action) async {
    final handler = widget.onAction[action];
    if (handler == null) {
      setState(() {
        _status = 'No handler for $action';
      });
      return;
    }

    try {
      final message = await handler(_values());
      setState(() {
        _status = message ?? '$action complete';
      });
    } catch (error) {
      setState(() {
        _status = '$action failed: $error';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: widget.title,
      icon: widget.icon,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _FieldGrid(fields: widget.fields, controllers: _controllers),
          if (_status != null) ...[
            const SizedBox(height: 14),
            Text(
              _status!,
              style: const TextStyle(color: AppColors.subtext, fontSize: 13),
            ),
          ],
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: widget.actions
                .map(
                  (action) => action == widget.actions.first
                      ? FilledButton(
                          onPressed: () => _runAction(action),
                          child: Text(action),
                        )
                      : OutlinedButton(
                          onPressed: () => _runAction(action),
                          child: Text(action),
                        ),
                )
                .toList(),
          ),
        ],
      ),
    );
  }
}

class _ListUsersPanel extends StatefulWidget {
  const _ListUsersPanel({
    required this.commandClient,
    required this.cameraHost,
    required this.commandPort,
  });

  final MadeyeCommandClient commandClient;
  final String cameraHost;
  final int commandPort;

  @override
  State<_ListUsersPanel> createState() => _ListUsersPanelState();
}

class _ListUsersPanelState extends State<_ListUsersPanel> {
  String _status = 'Tap refresh to query enrolled users.';
  List<String> _lines = const ['Count: -', '', 'No results yet.'];

  Future<void> _refreshUsers() async {
    try {
      final result = await widget.commandClient.userList(
        widget.cameraHost,
        widget.commandPort,
      );
      setState(() {
        _status = 'User list loaded';
        _lines = [
          'Count: ${result.count}',
          '',
          ...result.rawList
              .split(RegExp(r'\r?\n'))
              .where((line) => line.trim().isNotEmpty),
        ];
      });
    } catch (error) {
      setState(() {
        _status = 'Refresh failed: $error';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: 'Enrolled Users',
      icon: Icons.groups_rounded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surfaceAlt,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.border),
            ),
            child: Text(
              _lines.join('\n'),
              style: const TextStyle(
                fontFamily: 'monospace',
                fontSize: 14,
                height: 1.5,
                color: AppColors.text,
              ),
            ),
          ),
          const SizedBox(height: 14),
          Text(
            _status,
            style: const TextStyle(color: AppColors.subtext, fontSize: 13),
          ),
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _refreshUsers,
            child: const Text('Refresh Users'),
          ),
        ],
      ),
    );
  }
}

class _ConsolePanel extends StatelessWidget {
  const _ConsolePanel({
    required this.icon,
    required this.title,
    required this.lines,
  });

  final IconData icon;
  final String title;
  final List<String> lines;

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: title,
      icon: icon,
      child: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surfaceAlt,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: AppColors.border),
            ),
            child: Text(
              lines.join('\n'),
              style: const TextStyle(
                fontFamily: 'monospace',
                fontSize: 14,
                height: 1.5,
                color: AppColors.text,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _FieldGrid extends StatelessWidget {
  const _FieldGrid({required this.fields, required this.controllers});

  final List<_FieldSpec> fields;
  final List<TextEditingController> controllers;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: fields.asMap().entries.map((entry) {
        final index = entry.key;
        final field = entry.value;
        return SizedBox(
          width: 260,
          child: TextFormField(
            controller: controllers[index],
            decoration: InputDecoration(labelText: field.label),
          ),
        );
      }).toList(),
    );
  }
}

class _FieldSpec {
  const _FieldSpec(this.label, this.value);

  final String label;
  final String value;
}

class _PanelShell extends StatelessWidget {
  const _PanelShell({
    required this.title,
    required this.icon,
    required this.child,
  });

  final String title;
  final IconData icon;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: AppColors.blue),
              const SizedBox(width: 10),
              Text(
                title,
                style: const TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w800,
                  color: AppColors.text,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          child,
        ],
      ),
    );
  }
}

class _MetricRow extends StatelessWidget {
  const _MetricRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        children: [
          Expanded(
            child: Text(
              label,
              style: const TextStyle(fontSize: 14, color: AppColors.subtext),
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: AppColors.text,
            ),
          ),
        ],
      ),
    );
  }
}

class _StatusBadge extends StatelessWidget {
  const _StatusBadge({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.fiber_manual_record_rounded, color: color, size: 14),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w700,
              color: AppColors.text,
            ),
          ),
        ],
      ),
    );
  }
}

class _CommandStatusStrip extends StatelessWidget {
  const _CommandStatusStrip({
    required this.status,
    required this.lastCommandAt,
  });

  final String status;
  final DateTime? lastCommandAt;

  @override
  Widget build(BuildContext context) {
    final timestamp = lastCommandAt == null
        ? 'No commands sent yet'
        : 'Last command at ${lastCommandAt!.hour.toString().padLeft(2, '0')}:${lastCommandAt!.minute.toString().padLeft(2, '0')}:${lastCommandAt!.second.toString().padLeft(2, '0')}';

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          const Icon(
            Icons.precision_manufacturing_rounded,
            color: AppColors.blue,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Command Status',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: AppColors.subtext,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  status,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    color: AppColors.text,
                  ),
                ),
              ],
            ),
          ),
          Text(
            timestamp,
            textAlign: TextAlign.end,
            style: const TextStyle(fontSize: 12, color: AppColors.subtext),
          ),
        ],
      ),
    );
  }
}

class _InfoChip extends StatelessWidget {
  const _InfoChip({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.circle, size: 10, color: color),
          const SizedBox(width: 8),
          Text(
            label,
            style: const TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: AppColors.text,
            ),
          ),
        ],
      ),
    );
  }
}

class _ViewerTag extends StatelessWidget {
  const _ViewerTag();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: AppColors.border),
      ),
      child: const Text(
        'LIVE VIEWER FRAME',
        style: TextStyle(
          fontSize: 11,
          fontWeight: FontWeight.w700,
          letterSpacing: 1.1,
          color: AppColors.blue,
        ),
      ),
    );
  }
}

class _OverlayPanel extends StatelessWidget {
  const _OverlayPanel({
    required this.label,
    required this.value,
    required this.accent,
  });

  final String label;
  final String value;
  final Color accent;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: AppColors.subtext,
            ),
          ),
          SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w800,
              color: accent,
            ),
          ),
        ],
      ),
    );
  }
}

Color _eventColor(MadeyeEventType eventType) {
  switch (eventType) {
    case MadeyeEventType.faceTooSmall:
      return AppColors.amber;
    case MadeyeEventType.headPoseWrong:
      return const Color(0xFFE8894B);
    case MadeyeEventType.faceDetected:
      return AppColors.blue;
    case MadeyeEventType.accessGranted:
      return AppColors.teal;
    case MadeyeEventType.accessDenied:
    case MadeyeEventType.connectionError:
      return AppColors.red;
    case MadeyeEventType.idle:
      return AppColors.blue;
  }
}
