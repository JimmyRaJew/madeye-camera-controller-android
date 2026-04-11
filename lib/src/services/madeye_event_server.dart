import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import '../models/madeye_models.dart';

class MadeyeEventServer {
  static const _eventHeader = 'MADEYE_EVENT';
  static const _eventHeaderSize = 32;
  static const _idleTimeout = Duration(seconds: 2);
  static const _socketTimeout = Duration(seconds: 2);
  static const _maxLogEntries = 60;

  final _controller = StreamController<MadeyeControllerState>.broadcast();

  MadeyeControllerState _state = MadeyeControllerState.initial();
  ServerSocket? _serverSocket;
  StreamSubscription<Socket>? _serverSubscription;
  Timer? _idleTimer;
  bool _stopping = false;
  DateTime? _lastEventAt;

  Stream<MadeyeControllerState> get stream => _controller.stream;

  MadeyeControllerState get currentState => _state;

  Future<void> start() async {
    if (_state.listenerRunning) {
      _appendLog('Listener already running on 0.0.0.0:${_state.eventPort}.');
      return;
    }

    await stop(emitOfflineState: false);
    _stopping = false;

    try {
      final socket = await ServerSocket.bind(
        InternetAddress.anyIPv4,
        _state.eventPort,
        shared: false,
      );
      _serverSocket = socket;
      _serverSubscription = socket.listen(_handleClient);
      _lastEventAt = null;

      _publish(
        _state.copyWith(
          listenerRunning: true,
          listenerStatus: 'Listening on 0.0.0.0:${_state.eventPort}',
          eventType: MadeyeEventType.idle,
          headline: 'Ready',
          detail: 'Waiting for camera events',
          updatedAt: DateTime.now(),
        ),
      );
      _appendLog('Listening on 0.0.0.0:${_state.eventPort}.');
      _startIdleTimer();
    } on SocketException catch (error) {
      _publishError('Unable to listen on event port ${_state.eventPort}: ${error.message}');
    } catch (error) {
      _publishError('Unable to start event listener: $error');
    }
  }

  Future<void> stop({bool emitOfflineState = true}) async {
    _stopping = true;
    _idleTimer?.cancel();
    _idleTimer = null;
    await _serverSubscription?.cancel();
    _serverSubscription = null;
    await _serverSocket?.close();
    _serverSocket = null;

    if (emitOfflineState) {
      _publish(
        _state.copyWith(
          listenerRunning: false,
          listenerStatus: 'Listener offline',
          updatedAt: DateTime.now(),
        ),
      );
      _appendLog('Listener stopped.');
    }
  }

  Future<void> restart() async {
    _appendLog('Restarting listener.');
    await stop();
    await start();
  }

  Future<void> dispose() async {
    await stop();
    await _controller.close();
  }

  void _handleClient(Socket socket) {
    unawaited(_readClient(socket));
  }

  Future<void> _readClient(Socket socket) async {
    final remoteAddress = socket.remoteAddress.address;
    try {
      final reader = _SocketByteReader(socket.timeout(_socketTimeout));
      final header = await reader.readExactly(_eventHeaderSize);
      final headerText = String.fromCharCodes(
        header.sublist(0, _eventHeader.length),
      );
      if (headerText != _eventHeader) {
        throw const FormatException('Unexpected event header');
      }

      final payloadSize = _readInt32(header, 16);
      if (payloadSize < 5) {
        throw const FormatException('Payload too small');
      }

      final payload = await reader.readExactly(payloadSize);
      final parsed = _parsePayload(payload);
      final now = DateTime.now();
      _lastEventAt = now;

      final source = '$remoteAddress at ${_formatTime(now)}';
      _publish(
        _state.copyWith(
          listenerRunning: true,
          listenerStatus: 'Last event from $source',
          eventType: parsed.eventType,
          headline: parsed.headline,
          detail: parsed.detail,
          updatedAt: now,
          lastFrameBytes: parsed.imageBytes ?? _state.lastFrameBytes,
          eventCount: _state.eventCount + 1,
          lastSource: source,
        ),
      );
      _appendLog('${parsed.headline}: ${parsed.detail}');
    } on TimeoutException {
      _appendLog('Event stream timed out before payload completed.');
    } on FormatException catch (error) {
      _publishError('Invalid event payload: ${error.message}');
    } on SocketException catch (error) {
      if (!_stopping) {
        _publishError('Event stream error: ${error.message}');
      }
    } catch (error) {
      if (!_stopping) {
        _publishError('Event stream error: $error');
      }
    } finally {
      await socket.close();
    }
  }

  ParsedMadeyeEvent _parsePayload(Uint8List payload) {
    var offset = 0;
    final jpgSize = _readInt32(payload, offset);
    offset += 4;
    if (jpgSize < 0 || offset + jpgSize > payload.length) {
      throw const FormatException('Invalid JPG payload size');
    }

    Uint8List? imageBytes;
    if (jpgSize > 0) {
      imageBytes = Uint8List.sublistView(payload, offset, offset + jpgSize);
    }
    offset += jpgSize;

    final detectStatus = offset < payload.length ? payload[offset++] : 0;
    if (detectStatus > 0 && offset + 8 <= payload.length) {
      offset += 8;
    }

    final identifyStatus = offset < payload.length ? payload[offset++] : 0;
    var identifyId = '';
    var identifyScore = 0.0;
    if (identifyStatus != 0 && offset < payload.length) {
      final idLength = payload[offset++];
      if (offset + idLength <= payload.length) {
        identifyId = String.fromCharCodes(
          payload.sublist(offset, offset + idLength),
        ).trim();
        offset += idLength;
      }
      if (offset + 4 <= payload.length) {
        identifyScore = _readInt32(payload, offset) / 10000.0;
      }
    }

    if (identifyStatus == 1) {
      final detail = identifyId.isEmpty
          ? 'Recognised at ${_formatTime(DateTime.now())}'
          : 'Recognised user $identifyId • score ${identifyScore.toStringAsFixed(4)}';
      return ParsedMadeyeEvent(
        eventType: MadeyeEventType.accessGranted,
        headline: 'Access Granted',
        detail: detail,
        imageBytes: imageBytes,
      );
    }

    if (identifyStatus == 2) {
      return ParsedMadeyeEvent(
        eventType: MadeyeEventType.accessDenied,
        headline: 'Access Denied',
        detail: 'User not recognised',
        imageBytes: imageBytes,
      );
    }

    switch (detectStatus) {
      case 1:
        return ParsedMadeyeEvent(
          eventType: MadeyeEventType.faceTooSmall,
          headline: 'Move Closer',
          detail: 'Face too small',
          imageBytes: imageBytes,
        );
      case 2:
        return ParsedMadeyeEvent(
          eventType: MadeyeEventType.headPoseWrong,
          headline: 'Adjust Head Pose',
          detail: 'Head pose wrong',
          imageBytes: imageBytes,
        );
      case 3:
        return ParsedMadeyeEvent(
          eventType: MadeyeEventType.faceDetected,
          headline: 'Face Detected',
          detail: 'Face detected by camera',
          imageBytes: imageBytes,
        );
      default:
        return ParsedMadeyeEvent(
          eventType: MadeyeEventType.idle,
          headline: 'Ready',
          detail: 'Waiting for camera events',
          imageBytes: imageBytes,
        );
    }
  }

  void _startIdleTimer() {
    _idleTimer?.cancel();
    _idleTimer = Timer.periodic(const Duration(milliseconds: 500), (_) {
      if (!_state.listenerRunning) {
        return;
      }
      final lastEventAt = _lastEventAt;
      final now = DateTime.now();
      if (lastEventAt == null || now.difference(lastEventAt) >= _idleTimeout) {
        if (_state.eventType != MadeyeEventType.idle ||
            _state.detail != 'Waiting for camera events') {
          _publish(
            _state.copyWith(
              eventType: MadeyeEventType.idle,
              headline: 'Ready',
              detail: 'Waiting for camera events',
              updatedAt: now,
            ),
          );
        }
      }
    });
  }

  void _publishError(String message) {
    _publish(
      _state.copyWith(
        listenerRunning: false,
        listenerStatus: 'Listener error',
        eventType: MadeyeEventType.connectionError,
        headline: 'Connection Error',
        detail: message,
        updatedAt: DateTime.now(),
      ),
    );
    _appendLog(message);
  }

  void _appendLog(String message) {
    final nextLogs = [
      MadeyeLogEntry(timestamp: DateTime.now(), message: message),
      ..._state.logs,
    ].take(_maxLogEntries).toList(growable: false);
    _publish(_state.copyWith(logs: nextLogs));
  }

  void _publish(MadeyeControllerState nextState) {
    _state = nextState;
    if (!_controller.isClosed) {
      _controller.add(_state);
    }
  }

  static int _readInt32(List<int> bytes, int offset) {
    return ((bytes[offset] & 0xFF) << 24) |
        ((bytes[offset + 1] & 0xFF) << 16) |
        ((bytes[offset + 2] & 0xFF) << 8) |
        (bytes[offset + 3] & 0xFF);
  }

  static String _formatTime(DateTime time) {
    final hour = time.hour.toString().padLeft(2, '0');
    final minute = time.minute.toString().padLeft(2, '0');
    final second = time.second.toString().padLeft(2, '0');
    return '$hour:$minute:$second';
  }
}

class _SocketByteReader {
  _SocketByteReader(Stream<List<int>> stream) : _iterator = StreamIterator(stream);

  final StreamIterator<List<int>> _iterator;
  final BytesBuilder _buffer = BytesBuilder(copy: false);

  Future<Uint8List> readExactly(int size) async {
    while (_buffer.length < size) {
      final hasNext = await _iterator.moveNext();
      if (!hasNext) {
        throw const SocketException('Unexpected end of stream');
      }
      _buffer.add(_iterator.current);
    }

    final bytes = _buffer.takeBytes();
    final wanted = Uint8List.sublistView(bytes, 0, size);
    final remainder = bytes.length > size
        ? Uint8List.sublistView(bytes, size)
        : Uint8List(0);
    if (remainder.isNotEmpty) {
      _buffer.add(remainder);
    }
    return Uint8List.fromList(wanted);
  }
}
