import 'package:flutter/services.dart';

class UsbDeviceInfo {
  const UsbDeviceInfo({
    required this.name,
    required this.vendorId,
    required this.productId,
    required this.deviceClass,
    required this.deviceSubclass,
    required this.deviceProtocol,
    required this.manufacturerName,
    required this.productName,
    required this.version,
  });

  factory UsbDeviceInfo.fromMap(Map<dynamic, dynamic> map) {
    return UsbDeviceInfo(
      name: map['name'] as String? ?? 'Unknown',
      vendorId: map['vendorId'] as int? ?? 0,
      productId: map['productId'] as int? ?? 0,
      deviceClass: map['deviceClass'] as int? ?? 0,
      deviceSubclass: map['deviceSubclass'] as int? ?? 0,
      deviceProtocol: map['deviceProtocol'] as int? ?? 0,
      manufacturerName: map['manufacturerName'] as String? ?? '-',
      productName: map['productName'] as String? ?? '-',
      version: map['version'] as String? ?? '-',
    );
  }

  final String name;
  final int vendorId;
  final int productId;
  final int deviceClass;
  final int deviceSubclass;
  final int deviceProtocol;
  final String manufacturerName;
  final String productName;
  final String version;

  String get summary =>
      'VID ${vendorId.toRadixString(16).padLeft(4, '0').toUpperCase()} '
      'PID ${productId.toRadixString(16).padLeft(4, '0').toUpperCase()}';
}

class UsbDeviceService {
  static const _channel = MethodChannel('fortress_camera_controller/usb');

  Future<List<UsbDeviceInfo>> listUsbDevices() async {
    final result = await _channel.invokeMethod<List<dynamic>>('listUsbDevices');
    final devices = result ?? const [];
    return devices
        .map((item) => UsbDeviceInfo.fromMap(Map<dynamic, dynamic>.from(item as Map)))
        .toList(growable: false);
  }
}
