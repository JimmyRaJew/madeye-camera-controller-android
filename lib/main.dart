import 'package:flutter/material.dart';

import 'src/models/madeye_models.dart';
import 'src/services/madeye_event_server.dart';

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
  MenuSection _selectedSection = menuSections.first;
  late MadeyeControllerState _controllerState;

  void _selectMenu(MenuSection section) {
    setState(() {
      _selectedSection = section;
    });
    Navigator.of(context).pop();
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
                color: _controllerState.listenerRunning ? AppColors.blue : AppColors.red,
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
                style: const TextStyle(
                  fontSize: 15,
                  color: AppColors.subtext,
                ),
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
                    color: _controllerState.listenerRunning ? AppColors.blue : AppColors.amber,
                  ),
                  _InfoChip(
                    label: 'Command Port ${_controllerState.commandPort}',
                    color: AppColors.red,
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Expanded(
                child: SingleChildScrollView(
                  child: _SectionContent(
                    section: _selectedSection,
                    controllerState: _controllerState,
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
                style: TextStyle(
                  fontSize: 14,
                  color: AppColors.subtext,
                ),
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
                          color: selected
                              ? AppColors.blue
                              : AppColors.border,
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
    required this.onOpenMenu,
    required this.onStartListener,
    required this.onStopListener,
    required this.onRestartListener,
  });

  final MenuSection section;
  final MadeyeControllerState controllerState;
  final VoidCallback onOpenMenu;
  final VoidCallback onStartListener;
  final VoidCallback onStopListener;
  final VoidCallback onRestartListener;

  @override
  Widget build(BuildContext context) {
    switch (section.title) {
      case 'Camera Viewer':
        return _CameraViewerPanel(
          state: controllerState,
          onOpenMenu: onOpenMenu,
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
            _FieldSpec('Camera', 'RGB'),
            _FieldSpec('Balance', 'On'),
          ],
          primaryLabel: 'Get',
          secondaryLabel: 'Set',
        );
      case 'Face Settings':
        return _SettingsPanel(
          icon: Icons.face_retouching_natural,
          title: 'Face Controls',
          fields: const [
            _FieldSpec('Threshold', '0.5500'),
            _FieldSpec('Attempts', '3'),
            _FieldSpec('Liveness', 'On'),
            _FieldSpec('Liveness Threshold', '0.7200'),
            _FieldSpec('Face Size', '160'),
          ],
          primaryLabel: 'Get',
          secondaryLabel: 'Set',
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
        );
      case 'Delete User':
        return _ActionFormPanel(
          icon: Icons.person_remove_alt_1_rounded,
          title: 'Delete User',
          fields: const [
            _FieldSpec('User ID', '1001'),
          ],
          actions: const ['Delete User', 'Delete All Users'],
        );
      case 'List Users':
        return _ConsolePanel(
          icon: Icons.groups_rounded,
          title: 'Enrolled Users',
          lines: const [
            'Count: 3',
            '',
            '1001',
            '1002',
            '1003',
          ],
          actionLabel: 'Refresh User List',
        );
      case 'Database Tools':
        return _ActionFormPanel(
          icon: Icons.storage_rounded,
          title: 'Database Tools',
          fields: const [
            _FieldSpec('Database File', '/storage/emulated/0/camera_database.sql'),
            _FieldSpec('MD5 File', '/storage/emulated/0/camera_database.md5'),
          ],
          actions: const ['Download Database', 'Upload Database'],
        );
      case 'Firmware Update':
        return _ActionFormPanel(
          icon: Icons.system_update_alt_rounded,
          title: 'Firmware Update',
          fields: const [
            _FieldSpec('Firmware Zip', '/storage/emulated/0/FortressCameraController.zip'),
            _FieldSpec('MD5 File', '/storage/emulated/0/FortressCameraController.md5'),
          ],
          actions: const ['Choose Firmware', 'Upload Firmware'],
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
  });

  final MadeyeControllerState state;
  final VoidCallback onOpenMenu;

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
                const Positioned(
                  top: 16,
                  left: 16,
                  child: _ViewerTag(),
                ),
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
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: onOpenMenu,
            icon: const Icon(Icons.menu_open_rounded),
            label: const Text('Open sections'),
          ),
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
          lines: state.logs.map((entry) => entry.formatted).toList(growable: false),
          actionLabel: 'Restart Listener',
          onPressed: onRestartListener,
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(
              child: FilledButton.icon(
                onPressed: state.listenerRunning ? onRestartListener : onStartListener,
                icon: Icon(
                  state.listenerRunning ? Icons.restart_alt_rounded : Icons.play_arrow_rounded,
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
                  state.listenerRunning ? Icons.stop_circle_outlined : Icons.menu_open_rounded,
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

class _SettingsPanel extends StatelessWidget {
  const _SettingsPanel({
    required this.icon,
    required this.title,
    required this.fields,
    required this.primaryLabel,
    required this.secondaryLabel,
  });

  final IconData icon;
  final String title;
  final List<_FieldSpec> fields;
  final String primaryLabel;
  final String secondaryLabel;

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: title,
      icon: icon,
      child: Column(
        children: [
          _FieldGrid(fields: fields),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(
                child: FilledButton(
                  onPressed: noop,
                  child: Text(primaryLabel),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: OutlinedButton(
                  onPressed: noop,
                  child: Text(secondaryLabel),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _ActionFormPanel extends StatelessWidget {
  const _ActionFormPanel({
    required this.icon,
    required this.title,
    required this.fields,
    required this.actions,
  });

  final IconData icon;
  final String title;
  final List<_FieldSpec> fields;
  final List<String> actions;

  @override
  Widget build(BuildContext context) {
    return _PanelShell(
      title: title,
      icon: icon,
      child: Column(
        children: [
          _FieldGrid(fields: fields),
          const SizedBox(height: 16),
          Wrap(
            spacing: 12,
            runSpacing: 12,
            children: actions
                .map(
                  (action) => action == actions.first
                      ? FilledButton(onPressed: noop, child: Text(action))
                      : OutlinedButton(onPressed: noop, child: Text(action)),
                )
                .toList(),
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
    required this.actionLabel,
    this.onPressed = noop,
  });

  final IconData icon;
  final String title;
  final List<String> lines;
  final String actionLabel;
  final VoidCallback onPressed;

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
          const SizedBox(height: 16),
          FilledButton(onPressed: onPressed, child: Text(actionLabel)),
        ],
      ),
    );
  }
}

class _FieldGrid extends StatelessWidget {
  const _FieldGrid({
    required this.fields,
  });

  final List<_FieldSpec> fields;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 12,
      runSpacing: 12,
      children: fields
          .map(
            (field) => SizedBox(
              width: 260,
              child: TextFormField(
                initialValue: field.value,
                decoration: InputDecoration(labelText: field.label),
              ),
            ),
          )
          .toList(),
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
  const _MetricRow({
    required this.label,
    required this.value,
  });

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
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.subtext,
              ),
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
  const _StatusBadge({
    required this.label,
    required this.color,
  });

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

class _InfoChip extends StatelessWidget {
  const _InfoChip({
    required this.label,
    required this.color,
  });

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
