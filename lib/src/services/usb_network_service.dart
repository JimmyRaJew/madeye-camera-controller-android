import 'dart:io';

class UsbNetworkInterfaceInfo {
  const UsbNetworkInterfaceInfo({
    required this.name,
    required this.addresses,
  });

  final String name;
  final List<String> addresses;
}

class UsbNetworkService {
  Future<List<UsbNetworkInterfaceInfo>> listInterfaces() async {
    final interfaces = await NetworkInterface.list(
      includeLinkLocal: false,
      includeLoopback: false,
      type: InternetAddressType.IPv4,
    );

    return interfaces
        .map(
          (iface) => UsbNetworkInterfaceInfo(
            name: iface.name,
            addresses: iface.addresses.map((address) => address.address).toList(growable: false),
          ),
        )
        .toList(growable: false);
  }

  Future<bool> probePort(String host, int port, {Duration timeout = const Duration(seconds: 2)}) async {
    Socket? socket;
    try {
      socket = await Socket.connect(host, port, timeout: timeout);
      await socket.close();
      return true;
    } catch (_) {
      return false;
    } finally {
      await socket?.close();
    }
  }
}
