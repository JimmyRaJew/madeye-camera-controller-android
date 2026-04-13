import 'package:flutter/services.dart';

class UsbEndpointInfo {
  const UsbEndpointInfo({
    required this.address,
    required this.attributes,
    required this.maxPacketSize,
    required this.interval,
  });

  factory UsbEndpointInfo.fromMap(Map<dynamic, dynamic> map) {
    return UsbEndpointInfo(
      address: map['address'] as int? ?? 0,
      attributes: map['attributes'] as int? ?? 0,
      maxPacketSize: map['maxPacketSize'] as int? ?? 0,
      interval: map['interval'] as int? ?? 0,
    );
  }

  final int address;
  final int attributes;
  final int maxPacketSize;
  final int interval;
}

class UsbInterfaceInfo {
  const UsbInterfaceInfo({
    required this.id,
    required this.interfaceClass,
    required this.interfaceSubclass,
    required this.interfaceProtocol,
    required this.endpoints,
  });

  factory UsbInterfaceInfo.fromMap(Map<dynamic, dynamic> map) {
    return UsbInterfaceInfo(
      id: map['id'] as int? ?? 0,
      interfaceClass: map['interfaceClass'] as int? ?? 0,
      interfaceSubclass: map['interfaceSubclass'] as int? ?? 0,
      interfaceProtocol: map['interfaceProtocol'] as int? ?? 0,
      endpoints: (map['endpoints'] as List<dynamic>? ?? const [])
          .map((item) => UsbEndpointInfo.fromMap(Map<dynamic, dynamic>.from(item as Map)))
          .toList(growable: false),
    );
  }

  final int id;
  final int interfaceClass;
  final int interfaceSubclass;
  final int interfaceProtocol;
  final List<UsbEndpointInfo> endpoints;
}

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
    required this.hasPermission,
    required this.interfaces,
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
      hasPermission: map['hasPermission'] as bool? ?? false,
      interfaces: (map['interfaces'] as List<dynamic>? ?? const [])
          .map((item) => UsbInterfaceInfo.fromMap(Map<dynamic, dynamic>.from(item as Map)))
          .toList(growable: false),
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
  final bool hasPermission;
  final List<UsbInterfaceInfo> interfaces;

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

  Future<String> requestPermission(String deviceName) async {
    final result = await _channel.invokeMethod<String>(
      'requestUsbPermission',
      {'deviceName': deviceName},
    );
    return result ?? 'Permission result unavailable';
  }
}
