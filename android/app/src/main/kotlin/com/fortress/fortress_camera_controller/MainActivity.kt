package com.fortress.fortress_camera_controller

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    private val usbChannel = "fortress_camera_controller/usb"
    private val transportChannel = "fortress_camera_controller/transport"
    private val usbPermissionAction = "fortress_camera_controller.USB_PERMISSION"
    private var pendingUsbPermissionResult: MethodChannel.Result? = null
    private var pendingUsbPermissionDeviceName: String? = null

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != usbPermissionAction) {
                return
            }
            val deviceName = intent.getStringExtra("deviceName")
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val result = pendingUsbPermissionResult
            if (result != null && deviceName == pendingUsbPermissionDeviceName) {
                if (granted) {
                    result.success("Permission granted for $deviceName")
                } else {
                    result.error("usb_permission_denied", "USB permission denied for $deviceName", null)
                }
                pendingUsbPermissionResult = null
                pendingUsbPermissionDeviceName = null
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            IntentFilter(usbPermissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, usbChannel).setMethodCallHandler { call, result ->
            when (call.method) {
                "listUsbDevices" -> result.success(listUsbDevices())
                "requestUsbPermission" -> requestUsbPermission(call.argument<String>("deviceName"), result)
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

    override fun onDestroy() {
        runCatching {
            unregisterReceiver(usbPermissionReceiver)
        }
        pendingUsbPermissionResult = null
        pendingUsbPermissionDeviceName = null
        super.onDestroy()
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
                "hasPermission" to usbManager.hasPermission(device),
                "interfaces" to List(device.interfaceCount) { index ->
                    val usbInterface = device.getInterface(index)
                    mapOf(
                        "id" to usbInterface.id,
                        "interfaceClass" to usbInterface.interfaceClass,
                        "interfaceSubclass" to usbInterface.interfaceSubclass,
                        "interfaceProtocol" to usbInterface.interfaceProtocol,
                        "endpoints" to List(usbInterface.endpointCount) { endpointIndex ->
                            val endpoint = usbInterface.getEndpoint(endpointIndex)
                            mapOf(
                                "address" to endpoint.address,
                                "attributes" to endpoint.attributes,
                                "maxPacketSize" to endpoint.maxPacketSize,
                                "interval" to endpoint.interval,
                            )
                        },
                    )
                },
            )
        }
    }

    private fun requestUsbPermission(deviceName: String?, result: MethodChannel.Result) {
        if (deviceName.isNullOrBlank()) {
            result.error("usb_permission_missing", "Missing USB device name", null)
            return
        }
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val device = usbManager.deviceList[deviceName]
        if (device == null) {
            result.error("usb_permission_missing_device", "USB device not found: $deviceName", null)
            return
        }
        if (usbManager.hasPermission(device)) {
            result.success("Permission already granted for $deviceName")
            return
        }
        if (pendingUsbPermissionResult != null) {
            result.error("usb_permission_pending", "A USB permission request is already in progress", null)
            return
        }

        pendingUsbPermissionResult = result
        pendingUsbPermissionDeviceName = deviceName
        val intent = Intent(usbPermissionAction).apply {
            putExtra("deviceName", deviceName)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        usbManager.requestPermission(device, pendingIntent)
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
