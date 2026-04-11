import 'dart:typed_data';

enum MadeyeEventType {
  idle,
  faceTooSmall,
  headPoseWrong,
  faceDetected,
  accessGranted,
  accessDenied,
  connectionError,
}

class MadeyeLogEntry {
  const MadeyeLogEntry({
    required this.timestamp,
    required this.message,
  });

  final DateTime timestamp;
  final String message;

  String get formatted {
    final hour = timestamp.hour.toString().padLeft(2, '0');
    final minute = timestamp.minute.toString().padLeft(2, '0');
    final second = timestamp.second.toString().padLeft(2, '0');
    return '$hour:$minute:$second  $message';
  }
}

class MadeyeControllerState {
  const MadeyeControllerState({
    required this.cameraHost,
    required this.eventPort,
    required this.commandPort,
    required this.listenerRunning,
    required this.listenerStatus,
    required this.eventType,
    required this.headline,
    required this.detail,
    required this.updatedAt,
    required this.lastFrameBytes,
    required this.eventCount,
    required this.lastSource,
    required this.logs,
  });

  factory MadeyeControllerState.initial() {
    final now = DateTime.now();
    return MadeyeControllerState(
      cameraHost: '192.168.1.111',
      eventPort: 7777,
      commandPort: 7778,
      listenerRunning: false,
      listenerStatus: 'Listener offline',
      eventType: MadeyeEventType.idle,
      headline: 'Ready',
      detail: 'Waiting for camera events',
      updatedAt: now,
      lastFrameBytes: null,
      eventCount: 0,
      lastSource: '-',
      logs: [
        MadeyeLogEntry(
          timestamp: now,
          message: 'Controller ready. Start the event listener to receive MADEYE frames.',
        ),
      ],
    );
  }

  final String cameraHost;
  final int eventPort;
  final int commandPort;
  final bool listenerRunning;
  final String listenerStatus;
  final MadeyeEventType eventType;
  final String headline;
  final String detail;
  final DateTime updatedAt;
  final Uint8List? lastFrameBytes;
  final int eventCount;
  final String lastSource;
  final List<MadeyeLogEntry> logs;

  MadeyeControllerState copyWith({
    String? cameraHost,
    int? eventPort,
    int? commandPort,
    bool? listenerRunning,
    String? listenerStatus,
    MadeyeEventType? eventType,
    String? headline,
    String? detail,
    DateTime? updatedAt,
    Object? lastFrameBytes = _sentinel,
    int? eventCount,
    String? lastSource,
    List<MadeyeLogEntry>? logs,
  }) {
    return MadeyeControllerState(
      cameraHost: cameraHost ?? this.cameraHost,
      eventPort: eventPort ?? this.eventPort,
      commandPort: commandPort ?? this.commandPort,
      listenerRunning: listenerRunning ?? this.listenerRunning,
      listenerStatus: listenerStatus ?? this.listenerStatus,
      eventType: eventType ?? this.eventType,
      headline: headline ?? this.headline,
      detail: detail ?? this.detail,
      updatedAt: updatedAt ?? this.updatedAt,
      lastFrameBytes: identical(lastFrameBytes, _sentinel)
          ? this.lastFrameBytes
          : lastFrameBytes as Uint8List?,
      eventCount: eventCount ?? this.eventCount,
      lastSource: lastSource ?? this.lastSource,
      logs: logs ?? this.logs,
    );
  }

  static const _sentinel = Object();
}

class ParsedMadeyeEvent {
  const ParsedMadeyeEvent({
    required this.eventType,
    required this.headline,
    required this.detail,
    required this.imageBytes,
  });

  final MadeyeEventType eventType;
  final String headline;
  final String detail;
  final Uint8List? imageBytes;
}
