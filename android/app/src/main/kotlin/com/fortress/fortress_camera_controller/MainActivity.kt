package com.fortress.fortress_camera_controller

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.LinkProperties
import android.hardware.usb.UsbManager
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    private val usbChannel = "fortress_camera_controller/usb"
    private val transportChannel = "fortress_camera_controller/transport"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, usbChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "listUsbDevices" -> result.success(listUsbDevices())
                else -> result.notImplemented()
            }
        }
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, transportChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "listNetworks" -> result.success(listNetworks())
                else -> result.notImplemented()
            }
        }
    }

    private fun listUsbDevices(): List<Map<String, Any?>> {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.map { device ->
            mapOf(
                "name" to device.deviceName,
                "vendorId" to device.vendorId,
                "productId" to device.productId,
                "deviceClass" to device.deviceClass,
                "deviceSubclass" to device.deviceSubclass,
                "deviceProtocol" to device.deviceProtocol,
                "manufacturerName" to device.manufacturerName,
                "productName" to device.productName,
                "version" to device.version,
            )
        }
    }

    private fun listNetworks(): List<Map<String, Any?>> {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.allNetworks.mapNotNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            val props = connectivityManager.getLinkProperties(network) ?: return@mapNotNull null
            mapOf(
                "interfaceName" to (props.interfaceName ?: "-"),
                "networkName" to network.toString(),
                "isVpn" to caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                "isCellular" to caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                "isWifi" to caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                "isEthernet" to caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
                "isUsb" to props.interfaceName?.startsWith("usb", ignoreCase = true),
                "addresses" to props.linkAddresses.map { it.address.hostAddress ?: "-" },
                "routes" to props.routes.map { route ->
                    "${route.destination} via ${route.gateway?.hostAddress ?: "-"}"
                },
                "dnsServers" to props.dnsServers.map { it.hostAddress ?: "-" },
            )
        }
    }
}
