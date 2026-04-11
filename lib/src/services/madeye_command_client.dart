import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

class MadeyeCommandClient {
  static const _txHeader = 'MADEYE_CMD_TX';
  static const _rxHeader = 'MADEYE_CMD_RX';
  static const _headerSize = 32;
  static const _connectTimeout = Duration(seconds: 2);
  static const _socketTimeout = Duration(seconds: 10);
  static const _maxAttempts = 3;

  static const commandVersionGet = 0xA0;
  static const commandVideoGet = 0xA1;
  static const commandVideoSet = 0xA2;
  static const commandFaceGet = 0xA3;
  static const commandFaceSet = 0xA4;
  static const commandNetworkGet = 0xA5;
  static const commandNetworkSet = 0xA6;
  static const commandCommGet = 0xA7;
  static const commandCommSet = 0xA8;
  static const commandFirmwareUpdate = 0xB0;
  static const commandUserAdd = 0xC0;
  static const commandUserDelete = 0xC1;
  static const commandUserDeleteAll = 0xC2;
  static const commandUserList = 0xC3;
  static const commandDatabaseGet = 0xD0;
  static const commandDatabaseSet = 0xD1;
  static const commandCameraOn = 0xE0;
  static const commandCameraOff = 0xE1;

  int _sequence = 1;

  Future<VersionInfo> versionGet(String host, int port) async {
    final payload = await _exchange(host, port, commandVersionGet, const [], commandVersionGet);
    final parser = _Parser(payload);
    parser.expectSuccess();
    return VersionInfo(
      firmware: parser.readLengthPrefixedString(),
      face: parser.readLengthPrefixedString(),
      os: parser.readLengthPrefixedString(),
    );
  }

  Future<VideoSettings> videoGet(String host, int port) async {
    final payload = await _exchange(host, port, commandVideoGet, const [], commandVideoGet);
    final parser = _Parser(payload);
    parser.expectSuccess();
    return VideoSettings(
      width: parser.readUInt16(),
      height: parser.readUInt16(),
      rotation: parser.readUInt8(),
      camera: parser.readUInt8(),
      balance: parser.readUInt8(),
    );
  }

  Future<void> videoSet(String host, int port, VideoSettings settings) async {
    final body = Uint8List(7);
    _writeUInt16(body, 0, settings.width);
    _writeUInt16(body, 2, settings.height);
    body[4] = settings.rotation & 0xFF;
    body[5] = settings.camera & 0xFF;
    body[6] = settings.balance & 0xFF;
    await _expectAck(host, port, commandVideoSet, body);
  }

  Future<FaceSettings> faceGet(String host, int port) async {
    final payload = await _exchange(host, port, commandFaceGet, const [], commandFaceGet);
    final parser = _Parser(payload);
    parser.expectSuccess();
    return FaceSettings(
      threshold: parser.readScaledFloat(),
      attempts: parser.readUInt8(),
      liveness: parser.readUInt8(),
      livenessThreshold: parser.readScaledFloat(),
      faceMinimum: parser.readUInt8(),
      faceSize: parser.readUInt16(),
    );
  }

  Future<void> faceSet(String host, int port, FaceSettings settings) async {
    final body = Uint8List(13);
    _writeScaledFloat(body, 0, settings.threshold);
    body[4] = settings.attempts & 0xFF;
    body[5] = settings.liveness & 0xFF;
    _writeScaledFloat(body, 6, settings.livenessThreshold);
    body[10] = settings.faceMinimum & 0xFF;
    _writeUInt16(body, 11, settings.faceSize);
    await _expectAck(host, port, commandFaceSet, body);
  }

  Future<NetworkSettings> networkGet(String host, int port) async {
    final payload = await _exchange(host, port, commandNetworkGet, const [], commandNetworkGet);
    final parser = _Parser(payload);
    parser.expectSuccess();
    return NetworkSettings(
      address: parser.readLengthPrefixedString(),
      gateway: parser.readLengthPrefixedString(),
      mask: parser.readLengthPrefixedString(),
    );
  }

  Future<void> networkSet(String host, int port, NetworkSettings settings) async {
    final body = _concat([
      _lengthPrefixed(settings.address),
      _lengthPrefixed(settings.gateway),
      _lengthPrefixed(settings.mask),
    ]);
    await _expectAck(host, port, commandNetworkSet, body);
  }

  Future<CommSettings> commGet(String host, int port) async {
    final payload = await _exchange(host, port, commandCommGet, const [], commandCommGet);
    final parser = _Parser(payload);
    parser.expectSuccess();
    return CommSettings(
      host: parser.readLengthPrefixedString(),
      eventPort: parser.readUInt16(),
      commandPort: parser.readUInt16(),
    );
  }

  Future<void> commSet(String host, int port, CommSettings settings) async {
    final body = _concat([
      _lengthPrefixed(settings.host),
      _uint16(settings.eventPort),
      _uint16(settings.commandPort),
    ]);
    await _expectAck(host, port, commandCommSet, body);
  }

  Future<void> firmwareUpdate(String host, int port, Uint8List firmware, String md5) async {
    final body = _concat([
      _uint32(firmware.length),
      firmware,
      _lengthPrefixed(md5),
    ]);
    await _expectAck(host, port, commandFirmwareUpdate, body);
  }

  Future<void> userAdd(String host, int port, String id, Uint8List face) async {
    final body = _concat([
      _lengthPrefixed(id),
      _uint16(face.length),
      face,
    ]);
    await _expectAck(host, port, commandUserAdd, body);
  }

  Future<void> userDelete(String host, int port, String id) async {
    await _expectAck(host, port, commandUserDelete, _lengthPrefixed(id));
  }

  Future<void> userDeleteAll(String host, int port) async {
    await _expectAck(host, port, commandUserDeleteAll, Uint8List(0));
  }

  Future<UserListResult> userList(String host, int port) async {
    final payload = await _exchange(host, port, commandUserList, const [], commandUserList);
    final parser = _Parser(payload);
    parser.expectSuccess();
    final count = parser.readInt32();
    final length = parser.readInt32();
    final listBytes = parser.readBytes(length);
    return UserListResult(count: count, rawList: utf8.decode(listBytes));
  }

  Future<DatabaseDownload> databaseGet(String host, int port) async {
    final payload = await _exchange(host, port, commandDatabaseGet, const [], commandDatabaseGet);
    final parser = _Parser(payload);
    parser.expectSuccess();
    final size = parser.readInt32();
    final database = parser.readBytes(size);
    final md5 = parser.readLengthPrefixedString();
    return DatabaseDownload(database: database, md5: md5);
  }

  Future<void> databaseSet(String host, int port, Uint8List database, String md5) async {
    final body = _concat([
      _uint32(database.length),
      database,
      _lengthPrefixed(md5),
    ]);
    await _expectAck(host, port, commandDatabaseSet, body);
  }

  Future<void> cameraOn(String host, int port) async {
    await _expectAck(host, port, commandCameraOn, Uint8List(0));
  }

  Future<void> cameraOff(String host, int port) async {
    await _expectAck(host, port, commandCameraOff, Uint8List(0));
  }

  Future<void> _expectAck(String host, int port, int command, Uint8List body) async {
    final payload = await _exchange(host, port, command, body, command);
    final parser = _Parser(payload);
    parser.expectSuccess();
  }

  Future<Uint8List> _exchange(String host, int port, int command, List<int> body, int expectedResponseCommand) async {
    Object? lastError;
    for (var attempt = 1; attempt <= _maxAttempts; attempt++) {
      final sequenceNumber = _sequence++;
      final request = _buildPacket(_txHeader, body, sequenceNumber, command);
      Socket? socket;
      try {
        socket = await Socket.connect(host, port, timeout: _connectTimeout);
        socket.add(request);
        await socket.flush();

        final reader = _SocketByteReader(socket.timeout(_socketTimeout));
        final header = await reader.readExactly(_headerSize);
        _validateHeader(header, expectedResponseCommand);
        final payloadSize = _readInt32(header, 16);
        final payload = await reader.readExactly(payloadSize);
        _validateLrc(header, payload);
        await socket.close();
        return payload;
      } catch (error) {
        lastError = error;
        await socket?.close();
      }
    }

    if (lastError is Exception) {
      throw lastError;
    }
    throw Exception('Command exchange failed');
  }

  void _validateHeader(Uint8List header, int expectedCommand) {
    final headerText = String.fromCharCodes(header.sublist(0, _rxHeader.length));
    if (headerText != _rxHeader) {
      throw const FormatException('Unexpected response header');
    }
    final payloadSize = _readInt32(header, 16);
    if (payloadSize <= 0) {
      throw const FormatException('Invalid response payload size');
    }
    final responseSequence = _readInt32(header, 20);
    if (responseSequence <= 0) {
      throw const FormatException('Invalid response sequence');
    }
    final responseCommand = header[24];
    if (!_isAcceptedResponseCommand(expectedCommand, responseCommand)) {
      throw FormatException(
        'Unexpected response command 0x${responseCommand.toRadixString(16).padLeft(2, '0')} for request 0x${expectedCommand.toRadixString(16).padLeft(2, '0')}',
      );
    }
  }

  bool _isAcceptedResponseCommand(int expectedCommand, int responseCommand) {
    if (responseCommand == expectedCommand) {
      return true;
    }
    if ((expectedCommand == commandCameraOn || expectedCommand == commandCameraOff) &&
        responseCommand == commandDatabaseSet) {
      return true;
    }
    return false;
  }

  void _validateLrc(Uint8List header, Uint8List payload) {
    final packet = Uint8List(header.length + payload.length);
    packet.setAll(0, header);
    packet.setAll(header.length, payload);
    final expected = _lrc(packet, packet.length - 1);
    final actual = packet[packet.length - 1];
    if (expected != actual) {
      throw const FormatException('Invalid response checksum');
    }
  }

  Uint8List _buildPacket(String headerText, List<int> body, int sequenceNumber, int command) {
    final payloadSize = body.length + 1;
    final packet = Uint8List(_headerSize + payloadSize);
    final headerBytes = ascii.encode(headerText);
    packet.setAll(0, headerBytes);
    _writeInt32(packet, 16, payloadSize);
    _writeInt32(packet, 20, sequenceNumber);
    packet[24] = command & 0xFF;
    packet.setAll(_headerSize, body);
    packet[packet.length - 1] = _lrc(packet, packet.length - 1);
    return packet;
  }

  static Uint8List _uint16(int value) {
    final bytes = Uint8List(2);
    _writeUInt16(bytes, 0, value);
    return bytes;
  }

  static Uint8List _uint32(int value) {
    final bytes = Uint8List(4);
    _writeInt32(bytes, 0, value);
    return bytes;
  }

  static Uint8List _lengthPrefixed(String value) {
    final encoded = utf8.encode(value);
    final bytes = Uint8List(encoded.length + 1);
    bytes[0] = encoded.length & 0xFF;
    bytes.setAll(1, encoded);
    return bytes;
  }

  static Uint8List _concat(List<Uint8List> parts) {
    final totalSize = parts.fold<int>(0, (sum, part) => sum + part.length);
    final result = Uint8List(totalSize);
    var offset = 0;
    for (final part in parts) {
      result.setAll(offset, part);
      offset += part.length;
    }
    return result;
  }

  static void _writeUInt16(Uint8List data, int offset, int value) {
    data[offset] = (value >> 8) & 0xFF;
    data[offset + 1] = value & 0xFF;
  }

  static void _writeInt32(Uint8List data, int offset, int value) {
    data[offset] = (value >> 24) & 0xFF;
    data[offset + 1] = (value >> 16) & 0xFF;
    data[offset + 2] = (value >> 8) & 0xFF;
    data[offset + 3] = value & 0xFF;
  }

  static void _writeScaledFloat(Uint8List data, int offset, double value) {
    _writeInt32(data, offset, (value * 10000).round());
  }

  static int _readInt32(Uint8List data, int offset) {
    return ((data[offset] & 0xFF) << 24) |
        ((data[offset + 1] & 0xFF) << 16) |
        ((data[offset + 2] & 0xFF) << 8) |
        (data[offset + 3] & 0xFF);
  }

  static int _lrc(Uint8List data, int size) {
    var checksum = 0;
    for (var i = 0; i < size; i++) {
      checksum ^= data[i];
    }
    return checksum & 0xFF;
  }
}

class _SocketByteReader {
  _SocketByteReader(Stream<List<int>> stream) : _stream = stream;

  final Stream<List<int>> _stream;
  final List<int> _buffer = [];
  Completer<void> _dataAvailable = Completer<void>();
  final Completer<void> _done = Completer<void>();
  late final StreamSubscription<List<int>> _subscription = _stream.listen(
    (chunk) {
      _buffer.addAll(chunk);
      if (!_dataAvailable.isCompleted) {
        _dataAvailable.complete();
      }
    },
    onError: (Object error, StackTrace stackTrace) {
      if (!_done.isCompleted) {
        _done.completeError(error, stackTrace);
      }
    },
    onDone: () {
      if (!_done.isCompleted) {
        _done.complete();
      }
    },
    cancelOnError: true,
  );

  Future<Uint8List> readExactly(int size) async {
    while (_buffer.length < size) {
      if (_done.isCompleted) {
        break;
      }
      try {
        await Future.any([
          _dataAvailable.future,
          _done.future,
        ]).timeout(const Duration(seconds: 10));
      } on TimeoutException {
        // Loop again and keep waiting for enough bytes.
      }
      if (_dataAvailable.isCompleted && !_done.isCompleted) {
        _dataAvailable = Completer<void>();
      }
    }

    if (_buffer.length < size) {
      await _subscription.cancel();
      throw StateError('Unexpected end of stream');
    }

    final result = Uint8List.fromList(_buffer.take(size).toList(growable: false));
    _buffer.removeRange(0, size);
    return result;
  }
}

class _Parser {
  _Parser(Uint8List payload) : _content = payload.sublist(0, payload.length - 1);

  final Uint8List _content;
  var _offset = 0;

  void expectSuccess() {
    final status = readUInt8();
    if (status != 0x01) {
      throw FormatException('Command failed with status 0x${status.toRadixString(16).padLeft(2, '0')}');
    }
  }

  int readUInt8() {
    return _content[_offset++] & 0xFF;
  }

  int readUInt16() {
    final value = ((_content[_offset] & 0xFF) << 8) | (_content[_offset + 1] & 0xFF);
    _offset += 2;
    return value;
  }

  int readInt32() {
    final value = MadeyeCommandClient._readInt32(_content, _offset);
    _offset += 4;
    return value;
  }

  double readScaledFloat() {
    return readInt32() / 10000.0;
  }

  String readLengthPrefixedString() {
    final length = readUInt8();
    final data = readBytes(length);
    return utf8.decode(data);
  }

  Uint8List readBytes(int length) {
    final result = Uint8List.fromList(_content.sublist(_offset, _offset + length));
    _offset += length;
    return result;
  }
}

class VersionInfo {
  const VersionInfo({
    required this.firmware,
    required this.face,
    required this.os,
  });

  final String firmware;
  final String face;
  final String os;
}

class VideoSettings {
  const VideoSettings({
    required this.width,
    required this.height,
    required this.rotation,
    required this.camera,
    required this.balance,
  });

  final int width;
  final int height;
  final int rotation;
  final int camera;
  final int balance;
}

class FaceSettings {
  const FaceSettings({
    required this.threshold,
    required this.attempts,
    required this.liveness,
    required this.livenessThreshold,
    required this.faceMinimum,
    required this.faceSize,
  });

  final double threshold;
  final int attempts;
  final int liveness;
  final double livenessThreshold;
  final int faceMinimum;
  final int faceSize;
}

class NetworkSettings {
  const NetworkSettings({
    required this.address,
    required this.gateway,
    required this.mask,
  });

  final String address;
  final String gateway;
  final String mask;
}

class CommSettings {
  const CommSettings({
    required this.host,
    required this.eventPort,
    required this.commandPort,
  });

  final String host;
  final int eventPort;
  final int commandPort;
}

class UserListResult {
  const UserListResult({
    required this.count,
    required this.rawList,
  });

  final int count;
  final String rawList;
}

class DatabaseDownload {
  const DatabaseDownload({
    required this.database,
    required this.md5,
  });

  final Uint8List database;
  final String md5;
}
