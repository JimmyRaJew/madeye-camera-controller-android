import 'package:flutter/services.dart';

class RndisProbeService {
  static const _channel = MethodChannel('fortress_camera_controller/rndis');

  Future<Map<String, dynamic>> probe(String deviceName) async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'probeRndis',
      {'deviceName': deviceName},
    );
    return Map<String, dynamic>.from(result ?? const {});
  }
}
