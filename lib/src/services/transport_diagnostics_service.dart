import 'package:flutter/services.dart';

class TransportNetworkInfo {
  const TransportNetworkInfo({
    required this.interfaceName,
    required this.networkName,
    required this.isVpn,
    required this.isCellular,
    required this.isWifi,
    required this.isEthernet,
    required this.isUsb,
    required this.addresses,
    required this.routes,
    required this.dnsServers,
  });

  factory TransportNetworkInfo.fromMap(Map<dynamic, dynamic> map) {
    return TransportNetworkInfo(
      interfaceName: map['interfaceName'] as String? ?? '-',
      networkName: map['networkName'] as String? ?? '-',
      isVpn: map['isVpn'] as bool? ?? false,
      isCellular: map['isCellular'] as bool? ?? false,
      isWifi: map['isWifi'] as bool? ?? false,
      isEthernet: map['isEthernet'] as bool? ?? false,
      isUsb: map['isUsb'] as bool? ?? false,
      addresses: (map['addresses'] as List<dynamic>? ?? const [])
          .map((value) => value.toString())
          .toList(growable: false),
      routes: (map['routes'] as List<dynamic>? ?? const [])
          .map((value) => value.toString())
          .toList(growable: false),
      dnsServers: (map['dnsServers'] as List<dynamic>? ?? const [])
          .map((value) => value.toString())
          .toList(growable: false),
    );
  }

  final String interfaceName;
  final String networkName;
  final bool isVpn;
  final bool isCellular;
  final bool isWifi;
  final bool isEthernet;
  final bool isUsb;
  final List<String> addresses;
  final List<String> routes;
  final List<String> dnsServers;

  String get transportLabel {
    if (isUsb) return 'USB';
    if (isEthernet) return 'Ethernet';
    if (isWifi) return 'Wi-Fi';
    if (isCellular) return 'Cellular';
    if (isVpn) return 'VPN';
    return 'Other';
  }
}

class TransportDiagnosticsService {
  static const _channel = MethodChannel('fortress_camera_controller/transport');

  Future<List<TransportNetworkInfo>> listNetworks() async {
    final result = await _channel.invokeMethod<List<dynamic>>('listNetworks');
    final networks = result ?? const [];
    return networks
        .map((item) => TransportNetworkInfo.fromMap(Map<dynamic, dynamic>.from(item as Map)))
        .toList(growable: false);
  }
}
